@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.safeviewkids.v1

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.security.MessageDigest

object SafeViewKidsPrefs {
    const val PREFS_NAME = "safeviewkids_prefs"
    const val KEY_PIN_HASH = "pin_hash"
    const val KEY_SELECTED_APPS = "selected_apps"
    const val KEY_LIMIT_SECONDS = "limit_seconds"
    const val KEY_UNLOCK_UNTIL = "unlock_until"
    const val SESSION_PREFIX = "session_start_"

    val APP_PACKAGES = mapOf(
        "YouTube" to "com.google.android.youtube",
        "Instagram" to "com.instagram.android",
        "Facebook" to "com.facebook.katana"
    )

    fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

enum class Screen {
    HOME,
    SETUP,
    LIMIT,
    BLOCKED,
    UNLOCK
}

class MainActivity : ComponentActivity() {

    private var usageAccessEnabled by mutableStateOf(false)
    private var accessibilityEnabled by mutableStateOf(false)

    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = android.content.ComponentName(
            this,
            AppBlockAccessibilityService::class.java
        ).flattenToString()

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":")
            .any { it.equals(expected, ignoreCase = true) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        usageAccessEnabled = UsageAccessHelper.hasUsageAccess(this)
        accessibilityEnabled = isAccessibilityServiceEnabled()

        val prefs = getSharedPreferences(
            SafeViewKidsPrefs.PREFS_NAME,
            MODE_PRIVATE
        )

        val initialPinHash =
            prefs.getString(SafeViewKidsPrefs.KEY_PIN_HASH, "") ?: ""

        val initialSelectedApps =
            prefs.getStringSet(
                SafeViewKidsPrefs.KEY_SELECTED_APPS,
                emptySet()
            )?.toSet() ?: emptySet()

        val initialLimitSeconds =
            prefs.getInt(
                SafeViewKidsPrefs.KEY_LIMIT_SECONDS,
                180
            )

        val startBlocked =
            intent.getBooleanExtra("blocked", false)

        setContent {
            SafeViewKidsApp(
                startBlocked = startBlocked,
                initialPinHash = initialPinHash,
                initialSelectedApps = initialSelectedApps,
                initialLimitSeconds = initialLimitSeconds,
                usageAccessEnabled = usageAccessEnabled,
                accessibilityEnabled = accessibilityEnabled,

                onUsageAccess = {
                    if (UsageAccessHelper.hasUsageAccess(this)) {
                        usageAccessEnabled = true
                    } else {
                        openUsageAccessSettings()
                    }
                },

                onAccessibilityAccess = {
                    if (isAccessibilityServiceEnabled()) {
                        accessibilityEnabled = true
                    } else {
                        openAccessibilitySettings()
                    }
                },

                onSaveSettings = { pin, apps, limit ->
                    prefs.edit()
                        .putString(
                            SafeViewKidsPrefs.KEY_PIN_HASH,
                            SafeViewKidsPrefs.hashPin(pin)
                        )
                        .putStringSet(
                            SafeViewKidsPrefs.KEY_SELECTED_APPS,
                            apps
                        )
                        .putInt(
                            SafeViewKidsPrefs.KEY_LIMIT_SECONDS,
                            limit
                        )
                        .apply()
                },

                onParentUnlock = {
                    val edit = prefs.edit()
                        .putLong(
                            SafeViewKidsPrefs.KEY_UNLOCK_UNTIL,
                            System.currentTimeMillis() +
                                    10 * 60 * 1000L
                        )

                    SafeViewKidsPrefs.APP_PACKAGES.values.forEach { pkg ->
                        edit.remove(
                            SafeViewKidsPrefs.SESSION_PREFIX + pkg
                        )
                    }

                    edit.apply()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        usageAccessEnabled =
            UsageAccessHelper.hasUsageAccess(this)
        accessibilityEnabled =
            isAccessibilityServiceEnabled()
    }
}

@Composable
fun SafeViewKidsApp(
    startBlocked: Boolean,
    initialPinHash: String,
    initialSelectedApps: Set<String>,
    initialLimitSeconds: Int,
    usageAccessEnabled: Boolean,
    accessibilityEnabled: Boolean,
    onUsageAccess: () -> Unit,
    onAccessibilityAccess: () -> Unit,
    onSaveSettings: (String, Set<String>, Int) -> Unit,
    onParentUnlock: () -> Unit
) {
    var screen by remember {
        mutableStateOf(
            if (startBlocked) Screen.BLOCKED
            else Screen.HOME
        )
    }

    var pin by remember { mutableStateOf("") }

    var savedPinHash by remember {
        mutableStateOf(initialPinHash)
    }

    var selectedApps by remember {
        mutableStateOf(initialSelectedApps)
    }

    var limitSeconds by remember {
        mutableIntStateOf(initialLimitSeconds)
    }

    MaterialTheme {
        when (screen) {

            Screen.HOME -> HomeScreen(
                selectedApps = selectedApps,
                limitSeconds = limitSeconds,
                usageAccessEnabled = usageAccessEnabled,
                accessibilityEnabled = accessibilityEnabled,
                onSetup = {
                    screen = Screen.SETUP
                },
                onTest = {
                    screen = Screen.LIMIT
                },
                onUsageAccess = onUsageAccess,
                onAccessibilityAccess = onAccessibilityAccess
            )

            Screen.SETUP -> SetupScreen(
                pin = pin,
                onPinChange = {
                    pin = it.filter(Char::isDigit).take(6)
                },
                selectedApps = selectedApps,
                onToggleApp = { app ->
                    selectedApps =
                        if (app in selectedApps) {
                            selectedApps - app
                        } else {
                            selectedApps + app
                        }
                },
                limitSeconds = limitSeconds,
                onLimitChange = {
                    limitSeconds = it
                },
                onSave = {
                    if (pin.length == 6) {
                        savedPinHash =
                            SafeViewKidsPrefs.hashPin(pin)

                        onSaveSettings(
                            pin,
                            selectedApps,
                            limitSeconds
                        )

                        pin = ""
                        screen = Screen.HOME
                    }
                }
            )

            Screen.LIMIT -> LimitScreen(
                totalSeconds = limitSeconds,
                onExpired = {
                    screen = Screen.BLOCKED
                }
            )

            Screen.BLOCKED -> BlockedScreen(
                onUnlock = {
                    screen = Screen.UNLOCK
                }
            )

            Screen.UNLOCK -> UnlockScreen(
                onSuccess = { entered ->
                    if (
                        entered.length == 6 &&
                        savedPinHash.isNotEmpty() &&
                        SafeViewKidsPrefs.hashPin(entered) ==
                        savedPinHash
                    ) {
                        onParentUnlock()
                        screen = Screen.HOME
                    }
                },
                onBack = {
                    screen = Screen.BLOCKED
                }
            )
        }
    }
}

@Composable
fun HomeScreen(
    selectedApps: Set<String>,
    limitSeconds: Int,
    usageAccessEnabled: Boolean,
    accessibilityEnabled: Boolean,
    onSetup: () -> Unit,
    onTest: () -> Unit,
    onUsageAccess: () -> Unit,
    onAccessibilityAccess: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("SafeView Kids")
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            Text(
                "Version 1 - App Blocking",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                "Protected apps: ${
                    if (selectedApps.isEmpty())
                        "None configured"
                    else
                        selectedApps.joinToString()
                }"
            )

            Text(
                "Session limit: ${limitSeconds / 60} min"
            )

            Button(
                onClick = onSetup,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Parent Setup")
            }

            OutlinedButton(
                onClick = onTest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test Timer")
            }

            Button(
                onClick = onUsageAccess,
                enabled = !usageAccessEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (usageAccessEnabled)
                        "Usage Access: Enabled"
                    else
                        "Enable Usage Access"
                )
            }

            Button(
                onClick = onAccessibilityAccess,
                enabled = !accessibilityEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (accessibilityEnabled)
                        "App Blocking: Enabled"
                    else
                        "Enable App Blocking"
                )
            }

            Text(
                if (accessibilityEnabled) {
                    "Real app blocking is enabled."
                } else {
                    "Enable App Blocking in Android Accessibility settings."
                }
            )

            Text(
                "After the session limit, a protected app is sent to the Home screen and SafeView Kids opens the Parent Unlock screen."
            )
        }
    }
}

@Composable
fun SetupScreen(
    pin: String,
    onPinChange: (String) -> Unit,
    selectedApps: Set<String>,
    onToggleApp: (String) -> Unit,
    limitSeconds: Int,
    onLimitChange: (Int) -> Unit,
    onSave: () -> Unit
) {
    val apps = listOf(
        "YouTube",
        "Instagram",
        "Facebook"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Parent Setup")
                }
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item {
                Text("Create a 6-digit Parent PIN")
            }

            item {
                OutlinedTextField(
                    value = pin,
                    onValueChange = onPinChange,
                    label = {
                        Text("Parent PIN")
                    },
                    visualTransformation =
                        PasswordVisualTransformation(),
                    singleLine = true
                )
            }

            item {
                Text("Controlled apps")
            }

            items(apps) { app ->

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Checkbox(
                        checked = app in selectedApps,
                        onCheckedChange = {
                            onToggleApp(app)
                        }
                    )

                    Text(app)
                }
            }

            item {
                Text(
                    "Session limit: ${limitSeconds / 60} minutes"
                )
            }

            item {

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    listOf(
                        60,
                        180,
                        300,
                        600
                    ).forEach { seconds ->

                        FilterChip(
                            selected =
                                limitSeconds == seconds,
                            onClick = {
                                onLimitChange(seconds)
                            },
                            label = {
                                Text("${seconds / 60}m")
                            }
                        )
                    }
                }
            }

            item {

                Button(
                    onClick = onSave,
                    enabled = pin.length == 6,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Protection Settings")
                }
            }
        }
    }
}

@Composable
fun LimitScreen(
    totalSeconds: Int,
    onExpired: () -> Unit
) {
    var remaining by remember(totalSeconds) {
        mutableIntStateOf(totalSeconds)
    }

    LaunchedEffect(totalSeconds) {

        while (remaining > 0) {
            delay(1000)
            remaining--
        }

        onExpired()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Protected Session")
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.Center
        ) {

            Text(
                "Time remaining",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(
                Modifier.height(12.dp)
            )

            Text(
                "%02d:%02d".format(
                    remaining / 60,
                    remaining % 60
                ),
                style =
                    MaterialTheme.typography.displayLarge
            )

            Spacer(
                Modifier.height(16.dp)
            )

            Text(
                "This is the test timer. Real app blocking is handled by App Blocking."
            )
        }
    }
}

@Composable
fun BlockedScreen(
    onUnlock: () -> Unit
) {
    Scaffold { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.Center
        ) {

            Text(
                "Screen time finished",
                style =
                    MaterialTheme.typography.headlineMedium
            )

            Spacer(
                Modifier.height(12.dp)
            )

            Text(
                "The protected app has been blocked."
            )

            Spacer(
                Modifier.height(24.dp)
            )

            Button(
                onClick = onUnlock
            ) {
                Text("Parent Unlock")
            }
        }
    }
}

@Composable
fun UnlockScreen(
    onSuccess: (String) -> Unit,
    onBack: () -> Unit
) {
    var entered by remember {
        mutableStateOf("")
    }

    var error by remember {
        mutableStateOf(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Parent Unlock")
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.Center
        ) {

            OutlinedTextField(
                value = entered,
                onValueChange = {
                    entered =
                        it.filter(Char::isDigit)
                            .take(6)

                    error = false
                },
                label = {
                    Text("Parent PIN")
                },
                visualTransformation =
                    PasswordVisualTransformation(),
                singleLine = true
            )

            Spacer(
                Modifier.height(16.dp)
            )

            Button(
                onClick = {
                    val value = entered
                    onSuccess(value)

                    if (value.length == 6) {
                        error = true
                    }
                },
                enabled = entered.length == 6
            ) {
                Text("Verify")
            }

            if (error) {
                Text("Wrong Parent PIN")
            }

            TextButton(
                onClick = onBack
            ) {
                Text("Back")
            }
        }
    }
}
