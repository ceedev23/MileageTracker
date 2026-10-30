package com.example.ui.screens

import android.app.Activity
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import com.example.data.InputCheck
import com.example.ui.MileageViewModel
import com.example.ui.theme.MileageBlue

enum class AuthStep {
    WELCOME,
    SIGN_IN,
    SIGN_UP
}

private enum class LandingDestination {
    SIGN_IN,
    SIGN_UP,
    GUEST
}

private val NavyDark = Color(0xFF1E284A)
private val InputBg = Color(0xFFF1F5F9)

// Welcome screen, sampled from the scenic reference.
private val WelcomeBlue = Color(0xFF2E6AFE)
private val WelcomePanelTop = Color(0xFF97C6F8)
private val WelcomePanelBottom = Color(0xFF84BBF4)
private val WelcomeSignUpFill = Color(0xFFFCFCFE)
private val WelcomeSkyScrim = Brush.verticalGradient(
    0.00f to Color(0x992158F0),
    0.12f to Color(0x732158F0),
    0.26f to Color(0x262158F0),
    0.38f to Color.Transparent,
    1.00f to Color.Transparent
)

/**
 * Onboarding and Auth Screen faithfully matching the provided UI design & flow:
 * - Screen 1: "Get Started" Welcome screen with the mountain road illustration,
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
    var pending by remember { mutableStateOf<LandingDestination?>(null) }
    var countryDestination by remember { mutableStateOf<LandingDestination?>(null) }
    val authUiState by viewModel.authUiState.collectAsState()

    fun openDestination(dest: LandingDestination) {
        when (dest) {
            LandingDestination.SIGN_IN -> currentStep = AuthStep.SIGN_IN
            LandingDestination.SIGN_UP -> currentStep = AuthStep.SIGN_UP
            LandingDestination.GUEST -> viewModel.continueAsGuest()
        }
    }

    fun proceedAfterIntro(dest: LandingDestination) {
        if (viewModel.settings.value.taxCountrySelected) openDestination(dest)
        else countryDestination = dest
    }

    if (countryDestination != null) {
        AlertDialog(
            onDismissRequest = { countryDestination = null },
            title = { Text("Choose your tax country") },
            text = { Text("Canada defaults to kilometres; United States defaults to miles. You can change your country and distance unit in Settings.") },
            confirmButton = {
                TextButton(onClick = {
                    val dest = countryDestination ?: return@TextButton
                    viewModel.setTaxCountry("CA")
                    countryDestination = null
                    openDestination(dest)
                }) { Text("Canada") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val dest = countryDestination ?: return@TextButton
                    viewModel.setTaxCountry("US")
                    countryDestination = null
                    openDestination(dest)
                }) { Text("United States") }
            }
        )
    }

    fun leaveLanding(dest: LandingDestination) {
        viewModel.clearAuthMessages()
        if (viewModel.settings.value.onboardingSeen) {
            proceedAfterIntro(dest)
        } else {
            pending = dest
        }
    }

    if (pending != null) {
        OnboardingScreen(
            onBackToLanding = { pending = null },
            onDone = {
                val dest = pending ?: return@OnboardingScreen
                pending = null
                viewModel.setOnboardingSeen(true)
                proceedAfterIntro(dest)
            }
        )
        return
    }

    // Intercept back button
    BackHandler {
        if (currentStep == AuthStep.SIGN_IN || currentStep == AuthStep.SIGN_UP) {
            viewModel.clearAuthMessages()
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
                    onNavigateToSignIn = { leaveLanding(LandingDestination.SIGN_IN) },
                    onNavigateToSignUp = { leaveLanding(LandingDestination.SIGN_UP) },
                    onContinueAsGuest = { leaveLanding(LandingDestination.GUEST) },
                    canDismiss = canDismiss,
                    onDismiss = onDismiss
                )
            }
            AuthStep.SIGN_IN -> {
                SignInFormView(
                    onBack = {
                        viewModel.clearAuthMessages()
                        currentStep = AuthStep.WELCOME
                    },
                    onNavigateToSignUp = {
                        viewModel.clearAuthMessages()
                        currentStep = AuthStep.SIGN_UP
                    },
                    onSignIn = { email, pass ->
                        viewModel.signIn(email = email, password = pass)
                    },
                    onResetPassword = { email, onSuccess, onError ->
                        viewModel.resetPassword(email = email, onSuccess = onSuccess, onError = onError)
                    },
                    isLoading = authUiState.isLoading,
                    backendError = authUiState.errorMessage,
                    backendSuccess = authUiState.successMessage,
                    onClearBackendMessages = { viewModel.clearAuthMessages() }
                )
            }
            AuthStep.SIGN_UP -> {
                SignUpFormView(
                    onBack = {
                        viewModel.clearAuthMessages()
                        currentStep = AuthStep.WELCOME
                    },
                    onNavigateToSignIn = {
                        viewModel.clearAuthMessages()
                        currentStep = AuthStep.SIGN_IN
                    },
                    onSignUp = { name, email, pass ->
                        viewModel.signUp(name = name, email = email, password = pass)
                    },
                    isLoading = authUiState.isLoading,
                    backendError = authUiState.errorMessage,
                    backendSuccess = authUiState.successMessage,
                    onClearBackendMessages = { viewModel.clearAuthMessages() }
                )
            }
        }
    }
}

/**
 * Welcome landing: scenic road bleeds to the screen edges, then a light-blue
 * panel holds the large pill actions and the guest link.
 */
@Composable
private fun WelcomeLandingView(
    onNavigateToSignIn: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onContinueAsGuest: () -> Unit,
    canDismiss: Boolean,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val insets = WindowCompat.getInsetsController(window, view)
        val previousLightStatusBars = insets.isAppearanceLightStatusBars
        insets.isAppearanceLightStatusBars = false
        onDispose { insets.isAppearanceLightStatusBars = previousLightStatusBars }
    }

    val panelGradient = Brush.verticalGradient(
        colors = listOf(WelcomePanelTop, WelcomePanelBottom)
    )
    val heroBottomShape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
    val pill = RoundedCornerShape(33.dp)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(panelGradient)
            .navigationBarsPadding()
    ) {
        val gapAfterArt = maxHeight * 0.05f
        val gapBeforeGuest = maxHeight * 0.06f
        val gapBelowGuest = maxHeight * 0.04f

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(heroBottomShape)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_home_hero),
                    contentDescription = "Minimalist mountain road illustration",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.BottomCenter
                )
                // The artwork sky is paler than the reference. Deepen only the
                // upper sky so the wordmark stays on saturated blue.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(WelcomeSkyScrim)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 28.dp)
                        .padding(top = 8.dp)
                ) {
                    if (canDismiss) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.testTag("welcome_app_logo")
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_asset),
                            contentDescription = "App logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, Color.White, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Text(
                            text = "MileageTracker",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Get Started",
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 0.2.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "It's Voom Time",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Track drives, log gas receipts & claim\nmaximum tax deductions",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        lineHeight = 21.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(gapAfterArt))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onNavigateToSignIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .testTag("welcome_sign_in_button"),
                    shape = pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WelcomeBlue,
                        contentColor = Color.White
                    ),
                    elevation = null
                ) {
                    Text(
                        text = "Sign In",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onNavigateToSignUp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .testTag("welcome_sign_up_button"),
                    shape = pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WelcomeSignUpFill,
                        contentColor = WelcomeBlue
                    ),
                    elevation = null
                ) {
                    Text(
                        text = "Sign Up",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = WelcomeBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(gapBeforeGuest))

            TextButton(
                onClick = onContinueAsGuest,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("auth_guest_button")
            ) {
                Text(
                    text = "Continue as Guest  →",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WelcomeBlue
                )
            }

            Spacer(modifier = Modifier.height(gapBelowGuest))
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
    onResetPassword: (String, () -> Unit, (String) -> Unit) -> Unit,
    isLoading: Boolean,
    backendError: String?,
    backendSuccess: String?,
    onClearBackendMessages: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    var showForgotDialog by remember { mutableStateOf(false) }
    var showResetSentAlert by remember { mutableStateOf(false) }
    var showSocialDialog by remember { mutableStateOf(false) }
    var socialDialogProvider by remember { mutableStateOf("") }

    val displayError = localError ?: backendError
    val displaySuccess = backendSuccess

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
                    .clipToBounds()
                    .background(topHeaderGradient)
            ) {
                // Background image illustration
                Image(
                    painter = painterResource(id = R.drawable.img_home_hero),
                    contentDescription = "Minimalist mountain road illustration",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.BottomCenter
                )

                // Top Header Bar with Back Button & App Logo Badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.85f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NavyDark
                        )
                    }

                    // App Logo Chip Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.testTag("auth_top_app_logo")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_asset),
                                contentDescription = "App logo",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                text = "MileageTracker",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                        }
                    }
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
                        .padding(horizontal = 28.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // App Logo & Screen Title Header
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_asset),
                            contentDescription = "MileageTracker Logo",
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .testTag("auth_screen_app_logo"),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Sign In",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Text(
                            text = "Access your mileage and tax records",
                            fontSize = 12.5.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    // Success notice (e.g. redirected from registration)
                    AnimatedVisibility(visible = displaySuccess != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF0FDF4),
                            border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = displaySuccess ?: "",
                                fontSize = 12.5.sp,
                                color = Color(0xFF16A34A),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Error message
                    AnimatedVisibility(visible = displayError != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFECACA)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = displayError ?: "",
                                fontSize = 12.5.sp,
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
                            email = InputCheck.keepEmail(it)
                            localError = null
                            if (backendError != null) onClearBackendMessages()
                        },
                        enabled = !isLoading,
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
                            password = InputCheck.keepPassword(it)
                            localError = null
                            if (backendError != null) onClearBackendMessages()
                        },
                        enabled = !isLoading,
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
                                val cleanEmail = InputCheck.email(email)
                                val passwordError = InputCheck.passwordProblem(password)
                                if (cleanEmail == null) {
                                    localError = "Please enter a valid email address."
                                } else if (passwordError != null) {
                                    localError = passwordError
                                } else {
                                    localError = null
                                    onClearBackendMessages()
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
                            val cleanEmail = InputCheck.email(email)
                            val passwordError = InputCheck.passwordProblem(password)
                            if (cleanEmail == null) {
                                localError = "Please enter a valid email address."
                            } else if (passwordError != null) {
                                localError = passwordError
                            } else {
                                localError = null
                                onClearBackendMessages()
                                onSignIn(cleanEmail, password)
                            }
                        },
                        enabled = !isLoading,
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
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text(
                                text = "Sign In",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // "Forgot password?" Link
                    Text(
                        text = "Forgot password?",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B),
                        modifier = Modifier
                            .clickable {
                                onClearBackendMessages()
                                showForgotDialog = true
                            }
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
                            onClick = {
                                socialDialogProvider = "Facebook"
                                showSocialDialog = true
                            },
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
                            onClick = {
                                socialDialogProvider = "Google"
                                showSocialDialog = true
                            },
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
                            modifier = Modifier.clickable {
                                onClearBackendMessages()
                                onNavigateToSignUp()
                            }
                        )
                    }
                }
            }
        }
    }

    // Social Provider Information Dialog
    if (showSocialDialog) {
        AlertDialog(
            onDismissRequest = { showSocialDialog = false },
            title = { Text(text = "$socialDialogProvider Sign In", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "$socialDialogProvider OAuth is not enabled on this Supabase project. Please sign in or register with your email address and password, or Continue as Guest.",
                    fontSize = 13.5.sp,
                    color = Color(0xFF475569)
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSocialDialog = false },
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

    // Forgot Password Dialog
    if (showForgotDialog) {
        var resetEmail by remember { mutableStateOf(email.ifBlank { "" }) }
        var resetError by remember { mutableStateOf<String?>(null) }
        var isResetting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isResetting) showForgotDialog = false },
            title = { Text(text = "Reset Password", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter your email address to receive password reset instructions from Supabase:",
                        fontSize = 13.5.sp,
                        color = Color(0xFF475569)
                    )
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = {
                            resetEmail = InputCheck.keepEmail(it)
                            resetError = null
                        },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (resetError != null) {
                        Text(
                            text = resetError ?: "",
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = InputCheck.email(resetEmail)
                        if (clean == null) {
                            resetError = "Please enter a valid email address."
                            return@Button
                        }
                        isResetting = true
                        onResetPassword(
                            clean,
                            {
                                isResetting = false
                                showForgotDialog = false
                                showResetSentAlert = true
                            },
                            { err ->
                                isResetting = false
                                resetError = err
                            }
                        )
                    },
                    enabled = !isResetting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MileageBlue,
                        contentColor = Color.White
                    ),
                    elevation = null
                ) {
                    if (isResetting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Send Link", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showForgotDialog = false },
                    enabled = !isResetting
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResetSentAlert) {
        AlertDialog(
            onDismissRequest = { showResetSentAlert = false },
            title = { Text("Reset Link Sent", fontWeight = FontWeight.Bold) },
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
    isLoading: Boolean,
    backendError: String?,
    backendSuccess: String?,
    onClearBackendMessages: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var showSocialDialog by remember { mutableStateOf(false) }
    var socialDialogProvider by remember { mutableStateOf("") }

    val displayError = localError ?: backendError

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
                    .clipToBounds()
                    .background(topHeaderGradient)
            ) {
                // Background image illustration
                Image(
                    painter = painterResource(id = R.drawable.img_home_hero),
                    contentDescription = "Minimalist mountain road illustration",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.BottomCenter
                )

                // Top Header Bar with Back Button & App Logo Badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.85f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NavyDark
                        )
                    }

                    // App Logo Chip Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.testTag("auth_signup_top_app_logo")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_asset),
                                contentDescription = "App logo",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                text = "MileageTracker",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                        }
                    }
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
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    // App Logo & Screen Title Header
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_asset),
                            contentDescription = "MileageTracker Logo",
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .testTag("auth_screen_app_logo"),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Create Account",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Text(
                            text = "Track drives, log receipts & claim deductions",
                            fontSize = 12.5.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    // Success notice (when confirmation email sent)
                    AnimatedVisibility(visible = backendSuccess != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF0FDF4),
                            border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Account Created!",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                                Text(
                                    text = backendSuccess ?: "",
                                    fontSize = 13.sp,
                                    color = Color(0xFF166534),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = onNavigateToSignIn,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF16A34A),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text("Proceed to Sign In", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Error notice
                    AnimatedVisibility(visible = displayError != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFECACA)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = displayError ?: "",
                                fontSize = 12.5.sp,
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
                            name = InputCheck.keepName(it)
                            localError = null
                            if (backendError != null) onClearBackendMessages()
                        },
                        enabled = !isLoading,
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
                            email = InputCheck.keepEmail(it)
                            localError = null
                            if (backendError != null) onClearBackendMessages()
                        },
                        enabled = !isLoading,
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
                            password = InputCheck.keepPassword(it)
                            localError = null
                            if (backendError != null) onClearBackendMessages()
                        },
                        enabled = !isLoading,
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
                            confirmPassword = InputCheck.keepPassword(it)
                            localError = null
                            if (backendError != null) onClearBackendMessages()
                        },
                        enabled = !isLoading,
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
                                    onError = { localError = it },
                                    onSuccess = { nm, em, pw ->
                                        localError = null
                                        onClearBackendMessages()
                                        onSignUp(nm, em, pw)
                                    }
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
                                onError = { localError = it },
                                onSuccess = { nm, em, pw ->
                                    localError = null
                                    onClearBackendMessages()
                                    onSignUp(nm, em, pw)
                                }
                            )
                        },
                        enabled = !isLoading,
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
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text(
                                text = "Sign Up",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Social Registration Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                socialDialogProvider = "Facebook"
                                showSocialDialog = true
                            },
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
                            onClick = {
                                socialDialogProvider = "Google"
                                showSocialDialog = true
                            },
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
                            modifier = Modifier.clickable {
                                onClearBackendMessages()
                                onNavigateToSignIn()
                            }
                        )
                    }
                }
            }
        }
    }

    // Social Provider Information Dialog
    if (showSocialDialog) {
        AlertDialog(
            onDismissRequest = { showSocialDialog = false },
            title = { Text(text = "$socialDialogProvider Sign Up", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "$socialDialogProvider OAuth is not enabled on this Supabase project. Please register with your email address and password, or Continue as Guest.",
                    fontSize = 13.5.sp,
                    color = Color(0xFF475569)
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSocialDialog = false },
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

private fun validateAndRegister(
    name: String,
    email: String,
    password: String,
    confirmPassword: String,
    onError: (String) -> Unit,
    onSuccess: (String, String, String) -> Unit
) {
    val cleanName = InputCheck.exactName(name)
    val cleanEmail = InputCheck.email(email)
    val passwordError = InputCheck.passwordProblem(password)

    if (name.isBlank()) {
        onError("Please enter your name.")
        return
    }
    if (cleanName == null) {
        onError("Name can use letters, spaces, hyphens, and apostrophes.")
        return
    }
    if (cleanEmail == null) {
        onError("Please enter a valid email address.")
        return
    }
    if (passwordError != null) {
        onError(passwordError)
        return
    }
    if (password != confirmPassword) {
        onError("Passwords do not match.")
        return
    }

    onSuccess(cleanName, cleanEmail, password)
}
