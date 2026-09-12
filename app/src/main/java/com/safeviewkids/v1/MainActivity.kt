@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.safeviewkids.v1

import android.os.Bundle
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

enum class Screen {
    HOME, SETUP, LIMIT, BLOCKED, UNLOCK
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SafeViewKidsApp()
        }
    }
}

@Composable
fun SafeViewKidsApp() {

    var screen by remember { mutableStateOf(Screen.HOME) }
    var pin by remember { mutableStateOf("") }
    var savedPin by remember { mutableStateOf("") }
    var selectedApps by remember { mutableStateOf(setOf<String>()) }
    var limitSeconds by remember { mutableIntStateOf(180) }

    MaterialTheme {

        when (screen) {

            Screen.HOME -> HomeScreen(
                selectedApps = selectedApps,
                limitSeconds = limitSeconds,
                onSetup = { screen = Screen.SETUP },
                onTest = { screen = Screen.LIMIT }
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
                        savedPin = pin
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
                        entered == savedPin &&
                        savedPin.isNotEmpty()
                    ) {
                        screen = Screen.LIMIT
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
    onSetup: () -> Unit,
    onTest: () -> Unit
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
                "Version 1 MVP",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                "Protected apps: ${
                    if (selectedApps.isEmpty()) {
                        "None configured"
                    } else {
                        selectedApps.joinToString()
                    }
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

            Text(
                "This version demonstrates the parental-control UI and timer flow."
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
                modifier = Modifier.height(12.dp)
            )

            Text(
                "%02d:%02d".format(
                    remaining / 60,
                    remaining % 60
                ),
                style = MaterialTheme.typography.displayLarge
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                "When the timer reaches zero, this session will be blocked."
            )
        }
    }
}

@Composable
fun BlockedScreen(
    onUnlock: () -> Unit
) {

    Scaffold {

        padding ->

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
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Text(
                "The allowed video session has ended."
            )

            Spacer(
                modifier = Modifier.height(24.dp)
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
                        it.filter(Char::isDigit).take(6)
                },
                label = {
                    Text("Parent PIN")
                },
                visualTransformation =
                    PasswordVisualTransformation(),
                singleLine = true
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Button(
                onClick = {
                    onSuccess(entered)
                },
                enabled = entered.length == 6
            ) {
                Text("Verify")
            }

            TextButton(
                onClick = onBack
            ) {
                Text("Back")
            }
        }
    }
}
