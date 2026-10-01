package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.MileageViewModel
import com.example.ui.theme.MileageBlue

enum class AuthStep {
    WELCOME,
    SIGN_IN,
    SIGN_UP
}

private val NavyDark = Color(0xFF1E284A)
private val SkyBlueLight = Color(0xFFCDE4FE)
private val SkyBlueMid = Color(0xFFB1D7FE)
private val SkyBlueDeeper = Color(0xFF7CB8F6)
private val InputBg = Color(0xFFF1F5F9)

/**
 * Onboarding and Auth Screen faithfully matching the provided UI design & flow:
 * - Screen 1: "HELLO" Welcome screen with city skyline & white sedan illustration,
 *   rounded navy "Sign In" button and rounded white "Sign Up" button.
 * - Screen 2: "Sign In" screen with illustration header, curved white card,
 *   soft pill inputs for Email & Password, navy "Sign In" button, "Forgot password?",
 *   social login buttons (Facebook & Google), and link to Sign Up.
 * - Screen 3: "Sign Up" screen adhering to the same visual language.
 */
@Composable
fun AuthScreen(
    viewModel: MileageViewModel,
    onDismiss: () -> Unit,
    canDismiss: Boolean = false,
    initialStep: AuthStep = AuthStep.WELCOME,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableStateOf(initialStep) }

    // Intercept back button
    BackHandler {
        if (currentStep == AuthStep.SIGN_IN || currentStep == AuthStep.SIGN_UP) {
            currentStep = AuthStep.WELCOME
        } else if (canDismiss) {
            onDismiss()
        } else {
            viewModel.continueAsGuest()
        }
    }

    AnimatedContent(
        targetState = currentStep,
        transitionSpec = {
            if (targetState != AuthStep.WELCOME && initialState == AuthStep.WELCOME) {
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it / 2 } + fadeOut()
            } else if (targetState == AuthStep.WELCOME) {
                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it / 2 } + fadeOut()
            } else {
                fadeIn() togetherWith fadeOut()
            }
        },
        label = "auth_screen_flow"
    ) { step ->
        when (step) {
            AuthStep.WELCOME -> {
                WelcomeLandingView(
                    onNavigateToSignIn = { currentStep = AuthStep.SIGN_IN },
                    onNavigateToSignUp = { currentStep = AuthStep.SIGN_UP },
                    onContinueAsGuest = { viewModel.continueAsGuest() },
                    canDismiss = canDismiss,
                    onDismiss = onDismiss
                )
            }
            AuthStep.SIGN_IN -> {
                SignInFormView(
                    onBack = { currentStep = AuthStep.WELCOME },
                    onNavigateToSignUp = { currentStep = AuthStep.SIGN_UP },
                    onSignIn = { email, pass ->
                        viewModel.signIn(email)
                    },
                    onSocialSignIn = { provider ->
                        viewModel.signIn(
                            email = if (provider == "Google") "akpalamichael@gmail.com" else "user.facebook@example.com",
                            name = if (provider == "Google") "Michael Akpala" else "Social User"
                        )
                    }
                )
            }
            AuthStep.SIGN_UP -> {
                SignUpFormView(
                    onBack = { currentStep = AuthStep.WELCOME },
                    onNavigateToSignIn = { currentStep = AuthStep.SIGN_IN },
                    onSignUp = { name, email, pass ->
                        viewModel.signUp(name, email)
                    },
                    onSocialSignIn = { provider ->
                        viewModel.signIn(
                            email = if (provider == "Google") "akpalamichael@gmail.com" else "user.facebook@example.com",
                            name = if (provider == "Google") "Michael Akpala" else "Social User"
                        )
                    }
                )
            }
        }
    }
}

/**
 * Screen 1: Welcome / Landing Page matching the left mockup
 */
@Composable
private fun WelcomeLandingView(
    onNavigateToSignIn: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onContinueAsGuest: () -> Unit,
    canDismiss: Boolean,
    onDismiss: () -> Unit
) {
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFD8EBFF),
            Color(0xFFC0DEFE),
            Color(0xFF90C2F9)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Back button if allowed & HELLO Title
            Column(modifier = Modifier.fillMaxWidth()) {
                if (canDismiss) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = NavyDark
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Spacer(modifier = Modifier.height(28.dp))
                }

                Text(
                    text = "HELLO",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = NavyDark,
                    letterSpacing = (-1).sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Track drives, log gas receipts & claim maximum tax deductions",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF475569),
                    lineHeight = 20.sp
                )
            }

            // Center Illustration (City & White Car)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_auth_hero),
                    contentDescription = "Mileage tracking city and car illustration",
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .clip(RoundedCornerShape(24.dp))
                        .shadow(12.dp, RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            // Bottom Buttons: Rounded Navy "Sign In" & Rounded White "Sign Up"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App's Blue Sign In Button (Flat, no shadow, white text)
                Button(
                    onClick = onNavigateToSignIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("welcome_sign_in_button"),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MileageBlue,
                        contentColor = Color.White
                    ),
                    elevation = null
                ) {
                    Text(
                        text = "Sign In",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // White Sign Up Button with App Blue Accents
                Button(
                    onClick = onNavigateToSignUp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("welcome_sign_up_button"),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = MileageBlue
                    ),
                    border = BorderStroke(1.5.dp, MileageBlue),
                    elevation = null
                ) {
                    Text(
                        text = "Sign Up",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MileageBlue
                    )
                }

                // Guest Access Prompt
                TextButton(
                    onClick = onContinueAsGuest,
                    modifier = Modifier.testTag("auth_guest_button")
                ) {
                    Text(
                        text = "Continue as Guest  →",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MileageBlue
                    )
                }
            }
        }
    }
}

/**
 * Screen 2: Sign In Screen matching the right mockup
 */
@Composable
private fun SignInFormView(
    onBack: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onSocialSignIn: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showForgotDialog by remember { mutableStateOf(false) }
    var showResetSentAlert by remember { mutableStateOf(false) }

    val topHeaderGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFDCEBFF),
            Color(0xFFC7E2FE),
            Color(0xFFA5D1FD)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Section (35% height): Illustration header with Back arrow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.38f)
                    .background(topHeaderGradient)
            ) {
                // Background image illustration
                Image(
                    painter = painterResource(id = R.drawable.img_auth_hero),
                    contentDescription = "Car and city skyline",
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(y = 10.dp),
                    contentScale = ContentScale.Crop
                )

                // Back Button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(start = 16.dp, top = 8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.8f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NavyDark
                    )
                }
            }

            // Bottom Section (62% height): White curved sheet
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.62f)
                    .offset(y = (-20).dp),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Error message
                    AnimatedVisibility(visible = errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFECACA)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                fontSize = 12.sp,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Email input (Soft pill field)
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        placeholder = { Text("Email", color = Color(0xFF94A3B8)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("auth_email_input"),
                        shape = RoundedCornerShape(27.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = InputBg,
                            unfocusedContainerColor = InputBg,
                            focusedBorderColor = Color(0xFF93C5FD),
                            unfocusedBorderColor = Color.Transparent
                        )
                    )

                    // Password input (Soft pill field)
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        placeholder = { Text("Password", color = Color(0xFF94A3B8)) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("auth_password_input"),
                        shape = RoundedCornerShape(27.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                val cleanEmail = email.trim()
                                if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
                                    errorMessage = "Please enter a valid email address."
                                } else if (password.length < 6) {
                                    errorMessage = "Password must be at least 6 characters."
                                } else {
                                    onSignIn(cleanEmail, password)
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = InputBg,
                            unfocusedContainerColor = InputBg,
                            focusedBorderColor = Color(0xFF93C5FD),
                            unfocusedBorderColor = Color.Transparent
                        )
                    )

                    // App's Blue Sign In Button (Flat, no shadow, white text)
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            val cleanEmail = email.trim()
                            if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
                                errorMessage = "Please enter a valid email address."
                            } else if (password.length < 6) {
                                errorMessage = "Password must be at least 6 characters."
                            } else {
                                onSignIn(cleanEmail, password)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("auth_sign_in_button"),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MileageBlue,
                            contentColor = Color.White
                        ),
                        elevation = null
                    ) {
                        Text(
                            text = "Sign In",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // "Forgot password?" Link
                    Text(
                        text = "Forgot password?",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B),
                        modifier = Modifier
                            .clickable { showForgotDialog = true }
                            .padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Social Login Buttons: Facebook & Google (matching reference layout)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Facebook Sign In Button
                        OutlinedButton(
                            onClick = { onSocialSignIn("Facebook") },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(23.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF334155)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1877F2)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "f",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Text(
                                    text = "Sign In",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF475569)
                                )
                            }
                        }

                        // Google Sign In Button
                        OutlinedButton(
                            onClick = { onSocialSignIn("Google") },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(23.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF334155)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEA4335)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "G",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Text(
                                    text = "Sign In",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Switch to Sign Up Prompt
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = "Don't have an account? ",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "Sign Up",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MileageBlue,
                            modifier = Modifier.clickable { onNavigateToSignUp() }
                        )
                    }
                }
            }
        }
    }

    // Forgot Password Dialog
    if (showForgotDialog) {
        var resetEmail by remember { mutableStateOf(email.ifBlank { "akpalamichael@gmail.com" }) }
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = { Text(text = "Reset Password", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter your email address to receive password reset instructions:",
                        fontSize = 13.5.sp,
                        color = Color(0xFF475569)
                    )
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showForgotDialog = false
                        showResetSentAlert = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MileageBlue,
                        contentColor = Color.White
                    ),
                    elevation = null
                ) {
                    Text("Send Link", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResetSentAlert) {
        AlertDialog(
            onDismissRequest = { showResetSentAlert = false },
            title = { Text("Link Dispatched", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "A password reset email has been sent. Check your inbox to proceed.",
                    fontSize = 13.5.sp,
                    color = Color(0xFF475569)
                )
            },
            confirmButton = {
                Button(
                    onClick = { showResetSentAlert = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MileageBlue,
                        contentColor = Color.White
                    ),
                    elevation = null
                ) {
                    Text("OK", color = Color.White)
                }
            }
        )
    }
}

/**
 * Screen 3: Sign Up Screen matching the visual language
 */
@Composable
private fun SignUpFormView(
    onBack: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onSocialSignIn: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val topHeaderGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFDCEBFF),
            Color(0xFFC7E2FE),
            Color(0xFFA5D1FD)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Section (30% height): Illustration header with Back arrow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.32f)
                    .background(topHeaderGradient)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_auth_hero),
                    contentDescription = "Car and city skyline",
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(y = 10.dp),
                    contentScale = ContentScale.Crop
                )

                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(start = 16.dp, top = 8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.8f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NavyDark
                    )
                }
            }

            // Bottom Section: White curved card with registration fields
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.68f)
                    .offset(y = (-20).dp),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AnimatedVisibility(visible = errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFECACA)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                fontSize = 12.sp,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Full Name (Soft pill field)
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            errorMessage = null
                        },
                        placeholder = { Text("Full Name", color = Color(0xFF94A3B8)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("auth_name_input"),
                        shape = RoundedCornerShape(26.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = InputBg,
                            unfocusedContainerColor = InputBg,
                            focusedBorderColor = Color(0xFF93C5FD),
                            unfocusedBorderColor = Color.Transparent
                        )
                    )

                    // Email (Soft pill field)
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        placeholder = { Text("Email", color = Color(0xFF94A3B8)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("auth_email_input"),
                        shape = RoundedCornerShape(26.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = InputBg,
                            unfocusedContainerColor = InputBg,
                            focusedBorderColor = Color(0xFF93C5FD),
                            unfocusedBorderColor = Color.Transparent
                        )
                    )

                    // Password (Soft pill field)
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        placeholder = { Text("Password", color = Color(0xFF94A3B8)) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("auth_password_input"),
                        shape = RoundedCornerShape(26.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = InputBg,
                            unfocusedContainerColor = InputBg,
                            focusedBorderColor = Color(0xFF93C5FD),
                            unfocusedBorderColor = Color.Transparent
                        )
                    )

                    // Confirm Password (Soft pill field)
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            errorMessage = null
                        },
                        placeholder = { Text("Confirm Password", color = Color(0xFF94A3B8)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("auth_confirm_password_input"),
                        shape = RoundedCornerShape(26.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                validateAndRegister(
                                    name = name,
                                    email = email,
                                    password = password,
                                    confirmPassword = confirmPassword,
                                    onError = { errorMessage = it },
                                    onSuccess = { nm, em, pw -> onSignUp(nm, em, pw) }
                                )
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = InputBg,
                            unfocusedContainerColor = InputBg,
                            focusedBorderColor = Color(0xFF93C5FD),
                            unfocusedBorderColor = Color.Transparent
                        )
                    )

                    // App's Blue Sign Up Button (Flat, no shadow, white text)
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            validateAndRegister(
                                name = name,
                                email = email,
                                password = password,
                                confirmPassword = confirmPassword,
                                onError = { errorMessage = it },
                                onSuccess = { nm, em, pw -> onSignUp(nm, em, pw) }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("auth_sign_up_button"),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MileageBlue,
                            contentColor = Color.White
                        ),
                        elevation = null
                    ) {
                        Text(
                            text = "Sign Up",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Social Registration Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSocialSignIn("Facebook") },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(23.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF334155)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1877F2)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("f", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                }
                                Text("Sign In", fontSize = 13.sp, color = Color(0xFF475569))
                            }
                        }

                        OutlinedButton(
                            onClick = { onSocialSignIn("Google") },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(23.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF334155)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEA4335)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("G", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                }
                                Text("Sign In", fontSize = 13.sp, color = Color(0xFF475569))
                            }
                        }
                    }

                    // Switch back to Sign In
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = "Already have an account? ",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "Sign In",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MileageBlue,
                            modifier = Modifier.clickable { onNavigateToSignIn() }
                        )
                    }
                }
            }
        }
    }
}

private fun validateAndRegister(
    name: String,
    email: String,
    password: String,
    confirmPassword: String,
    onError: (String) -> Unit,
    onSuccess: (String, String, String) -> Unit
) {
    val cleanName = name.trim()
    val cleanEmail = email.trim()

    if (cleanName.isEmpty()) {
        onError("Please enter your name.")
        return
    }
    if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
        onError("Please enter a valid email address.")
        return
    }
    if (password.length < 6) {
        onError("Password must be at least 6 characters.")
        return
    }
    if (password != confirmPassword) {
        onError("Passwords do not match.")
        return
    }

    onSuccess(cleanName, cleanEmail, password)
}
