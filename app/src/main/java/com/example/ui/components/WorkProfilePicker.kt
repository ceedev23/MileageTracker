package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.WorkProfile

@Composable
fun WorkProfilePicker(selected: WorkProfile, onSelect: (WorkProfile) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("work_profile_picker"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Work profile")
        WorkProfile.entries.forEach { profile ->
            FilterChip(selected = selected == profile, onClick = { onSelect(profile) },
                label = { Text(profile.label) }, modifier = Modifier.testTag("work_profile_${profile.id}"))
        }
        Text("Choose labels and shortcuts for your work. All profiles share the same trips, units and custom rate. Switching does not change your records or decide reimbursement or tax eligibility.")
    }
}
