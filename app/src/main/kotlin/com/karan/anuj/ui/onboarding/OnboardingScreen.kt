package com.karan.anuj.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karan.anuj.R
import com.karan.anuj.core.ui.components.AnujScaffold
import com.karan.anuj.core.ui.components.PrimaryButton
import com.karan.anuj.core.ui.components.SecondaryButton

/**
 * One page of the first-run walk-through. A later phase that needs its own
 * permission (microphone, location, usage access) adds a step here; the
 * screen itself does not change.
 */
private enum class OnboardingStep { WELCOME, NOTIFICATIONS, APP_LOCK }

/** The notification permission only exists from Android 13; older phones allow notifications without asking. */
private fun stepsForThisPhone(): List<OnboardingStep> = buildList {
    add(OnboardingStep.WELCOME)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(OnboardingStep.NOTIFICATIONS)
    add(OnboardingStep.APP_LOCK)
}

/**
 * @param onEnableLock asks the user to prove they can unlock, switches app
 * lock on if they can, then calls the callback it was given.
 */
@Composable
fun OnboardingScreen(
    lockAvailable: Boolean,
    onEnableLock: (onDone: () -> Unit) -> Unit,
    onFinished: () -> Unit,
) {
    val steps = remember { stepsForThisPhone() }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val next = { if (index == steps.lastIndex) onFinished() else index += 1 }

    BackHandler(enabled = index > 0) { index -= 1 }

    /** Whatever the user answers, the walk-through moves on; a refusal can be changed later in the phone's settings. */
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { next() }

    AnujScaffold {
        when (steps[index]) {
            OnboardingStep.WELCOME -> StepLayout(
                icon = Icons.Filled.Check,
                title = stringResource(R.string.onboarding_welcome_title),
                body = stringResource(R.string.onboarding_welcome_body),
                primaryText = stringResource(R.string.onboarding_start),
                onPrimary = next,
            )

            OnboardingStep.NOTIFICATIONS -> StepLayout(
                icon = Icons.Filled.Notifications,
                title = stringResource(R.string.onboarding_notifications_title),
                body = stringResource(R.string.onboarding_notifications_body),
                primaryText = stringResource(R.string.onboarding_notifications_allow),
                onPrimary = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        next()
                    }
                },
                secondaryText = stringResource(R.string.onboarding_not_now),
                onSecondary = next,
            )

            OnboardingStep.APP_LOCK -> if (lockAvailable) {
                StepLayout(
                    icon = Icons.Filled.Lock,
                    title = stringResource(R.string.onboarding_lock_title),
                    body = stringResource(R.string.onboarding_lock_body),
                    primaryText = stringResource(R.string.onboarding_lock_enable),
                    onPrimary = { onEnableLock(next) },
                    secondaryText = stringResource(R.string.onboarding_not_now),
                    onSecondary = next,
                )
            } else {
                StepLayout(
                    icon = Icons.Filled.Lock,
                    title = stringResource(R.string.onboarding_lock_title),
                    body = stringResource(R.string.onboarding_lock_unavailable),
                    primaryText = stringResource(R.string.onboarding_continue),
                    onPrimary = next,
                )
            }
        }
    }
}

/** Explanation in the upper part, the buttons at the bottom where the thumb rests. */
@Composable
private fun StepLayout(
    icon: ImageVector,
    title: String,
    body: String,
    primaryText: String,
    onPrimary: () -> Unit,
    secondaryText: String? = null,
    onSecondary: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = primaryText, onClick = onPrimary)
        if (secondaryText != null) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = secondaryText, onClick = onSecondary)
        }
    }
}
