package com.allysong.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.allysong.ui.screens.CommunicationModeScreen
import com.allysong.ui.screens.HITLScreen
import com.allysong.ui.screens.MainDashboardScreen
import com.allysong.ui.screens.ManualSOSScreen
import com.allysong.ui.theme.AllySongTheme
import com.allysong.viewmodel.AllySongViewModel
import com.allysong.viewmodel.AppScreen

// ============================================================================
// AppNavigation.kt – commonMain (Compose Multiplatform)
// ============================================================================
// Single-composable navigation host that switches between screens based on
// the ViewModel's reactive AppScreen state.
//
// System bar insets (status bar + navigation bar) are handled here so that
// all screens are correctly inset without each screen needing to handle it.
// ============================================================================

@Composable
fun AllySongApp(viewModel: AllySongViewModel) {
    val state by viewModel.uiState.collectAsState()

    AllySongTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            when (state.screen) {
                AppScreen.DASHBOARD -> {
                    MainDashboardScreen(
                        latestReading = state.latestReading,
                        isSensorActive = state.isSensorActive,
                        isBarometerActive = state.isBarometerActive,
                        isBarometerAvailable = state.isBarometerAvailable,
                        latestPressureHpa = state.latestPressureHpa,
                        isMeshActive = state.isMeshActive,
                        peerCount = state.peerCount,
                        aiStatus = state.aiStatus,
                        onToggleSensor = viewModel::toggleSensor,
                        onToggleBarometer = viewModel::toggleBarometer,
                        onToggleMesh = viewModel::toggleMesh,
                        onOpenCommunications = {
                            viewModel.navigateTo(AppScreen.COMMUNICATION_MODE)
                        },
                        onManualSos = viewModel::onManualSos
                    )
                }

                AppScreen.HITL_VALIDATION -> {
                    state.currentEvent?.let { event ->
                        HITLScreen(
                            event = event,
                            onSafe = viewModel::onUserSafe,
                            onSos = viewModel::onUserSos
                        )
                    }
                }

                AppScreen.COMMUNICATION_MODE -> {
                    CommunicationModeScreen(
                        localAlias = state.localAlias,
                        peers = state.peers,
                        messages = state.chatMessages,
                        hasLocation = state.currentLatitude != 0.0 || state.currentLongitude != 0.0,
                        onSendMessage = viewModel::sendChatMessage,
                        onSendLocation = viewModel::sendLocation,
                        onSendImage = viewModel::sendImage,
                        onBackToDashboard = {
                            viewModel.navigateTo(AppScreen.DASHBOARD)
                        }
                    )
                }

                AppScreen.MANUAL_SOS -> {
                    ManualSOSScreen(
                        onConfirm = viewModel::onManualSosConfirm,
                        onCancel = viewModel::onManualSosCancel
                    )
                }
            }
        }
    }
}
