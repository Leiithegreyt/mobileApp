package com.example.drivebroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun AwaitApprovalScreen(
    onRefresh: () -> Unit
) {
    // Trigger an initial check and auto-poll every 20s while visible
    LaunchedEffect(Unit) {
        onRefresh()
        while (true) {
            delay(20_000)
            onRefresh()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Your account is pending approval.", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text("An administrator must approve your driver account before you can continue.")
        Spacer(Modifier.height(8.dp))
        Text("You can tap Refresh or wait — we’ll re-check every 20 seconds.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRefresh) {
            Text("Refresh")
        }
    }
}


