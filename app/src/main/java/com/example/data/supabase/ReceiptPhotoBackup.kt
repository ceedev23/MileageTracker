package com.example.data.supabase

import android.content.Context
import com.example.BuildConfig
import com.example.data.GasReceiptEntity
import com.example.data.InputCheck
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Photos are optional and never represented by a device path in the cloud. */
class ReceiptPhotoBackup(
    context: Context,
    private val client: SupabaseClient,
    val available: Boolean = BuildConfig.RECEIPT_BACKUP_READY == "true",
    val region: String = BuildConfig.RECEIPT_STORAGE_REGION
) {
    private val filesDir = context.filesDir
    private val prefs = context.getSharedPreferences("receipt_photo_backup", Context.MODE_PRIVATE)
    private fun account(): String? = client.config.getSession()?.user?.id
    private fun namespace(): String = digest(client.config.getUrl() + "/" + account())
    fun enabled(): Boolean = available && account() != null && prefs.getBoolean("enabled_${namespace()}", false)
    fun setEnabled(enabled: Boolean) {
        check(!enabled || (available && account() != null)) { "Receipt backup is not available" }
        check(prefs.edit().putBoolean("enabled_${namespace()}", enabled).commit())
    }

    private fun key(id: Long): String {
        val user = account() ?: throw IOException("Sign in to back up receipt photos.")
        return "$user/$id.jpg".also { require(validKey(it, user)) }
    }

    suspend fun upload(receipt: GasReceiptEntity): String? {
        if (!enabled()) return null
        val file = InputCheck.receiptFile(filesDir, receipt.imagePath)
        // A missing image must never be mistaken for a completed photo backup.
        if (receipt.imagePath.isBlank()) return null
        check(file != null && file.isFile && file.length() in 1..MAX_BYTES.toLong()) {
            "Receipt photo is missing or too large. It has not been backed up."
        }
        val bytes = file.readBytes().also { validateImage(it) }
        val key = key(receipt.id)
        client.receiptObject("POST", key, bytes).getOrThrow()
        return PREFIX + key
    }

    suspend fun restore(dto: SupabaseReceiptDto, local: GasReceiptEntity?): GasReceiptEntity {
        val remote = dto.toGasReceiptEntity()
        val localFile = local?.let { InputCheck.receiptFile(filesDir, it.imagePath) }?.takeIf { it.isFile }
        if (!dto.imagePath.startsWith(PREFIX)) {
            // Older backups contain paths belonging to another installation.
            return remote.copy(imagePath = localFile?.absolutePath.orEmpty())
        }
        val objectKey = dto.imagePath.removePrefix(PREFIX)
        require(validKey(objectKey, account().orEmpty()) && objectKey == key(dto.clientId)) { "Invalid cloud receipt photo" }
        if (!enabled()) return remote.copy(imagePath = localFile?.absolutePath.orEmpty())
        val bytes = client.receiptObject("GET", objectKey).getOrThrow()
        // Numeric, account/project-scoped filename remains compatible with the existing viewer.
        val dir = File(filesDir, "receipts").apply { check(exists() || mkdirs()) }
        val number = java.math.BigInteger(digest(namespace() + "/" + objectKey), 16).toString()
        val target = File(dir, "receipt_$number.jpg")
        val temporary = File.createTempFile("restore_", ".tmp", dir)
        try {
            temporary.outputStream().use { output -> output.write(bytes); output.fd.sync() }
            check(temporary.renameTo(target)) { "Could not save restored receipt photo" }
        } finally { temporary.delete() }
        return remote.copy(imagePath = target.absolutePath)
    }

    /** Persist BEFORE deleting the local row, even if offline or photo backup is off. */
    @Synchronized
    fun queueDeletion(id: Long) {
        if (account() == null) return
        val prefKey = "deletions_${namespace()}"
        val pending = prefs.getStringSet(prefKey, emptySet()).orEmpty().toMutableSet()
        pending.add(id.toString())
        check(prefs.edit().putStringSet(prefKey, pending).commit()) { "Could not queue cloud deletion" }
    }

    suspend fun drainDeletions() {
        if (account() == null) return
        val prefKey = "deletions_${namespace()}"
        for (id in prefs.getStringSet(prefKey, emptySet()).orEmpty().toSet()) {
            // Storage deletion is idempotent; retain this tombstone until BOTH operations succeed.
            client.receiptObject("DELETE", key(id.toLong())).getOrThrow()
            check(client.deleteReceipt(id.toLong()).getOrThrow())
            synchronized(this) {
                val remaining = prefs.getStringSet(prefKey, emptySet()).orEmpty().toMutableSet()
                remaining.remove(id)
                check(prefs.edit().putStringSet(prefKey, remaining).commit())
            }
        }
    }

    companion object {
        const val PREFIX = "receipt-photo:"
        const val MAX_BYTES = 10 * 1024 * 1024
        fun validKey(key: String, user: String): Boolean =
            user.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) &&
                key.matches(Regex("${Regex.escape(user)}/[1-9][0-9]*\\.jpg"))
        fun validateImage(bytes: ByteArray) {
            require(bytes.size in 4..MAX_BYTES && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()) {
                "Receipt photo must be a JPEG under 10 MB"
            }
        }
        private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
