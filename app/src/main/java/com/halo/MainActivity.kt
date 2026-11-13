package com.halo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.ui.screens.HaloActions
import com.halo.ui.screens.HaloApp
import com.halo.ui.theme.HaloTheme

class MainActivity : ComponentActivity() {

    private val viewModel: HaloViewModel by viewModels()

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onVehiclePermissionResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actions = HaloActions(
            answer = viewModel::answer,
            dismiss = viewModel::dismiss,
            setCareful = viewModel::setCareful,
            openSettings = viewModel::openSettings,
            replayBriefing = viewModel::replayBriefing,
            useLive = viewModel::useLive,
            startDemo = viewModel::startDemo,
            demoNext = viewModel::demoNext,
            demoRestart = viewModel::demoRestart,
        )
        setContent {
            HaloTheme {
                val state by viewModel.ui.collectAsStateWithLifecycle()
                HaloApp(state, actions)
            }
        }
        requestPermissions()
    }

    /**
     * 차량에서는 속도 권한(위험 등급)만 묻는다. 음성 인식은 차량 음성 서비스가 맡는다.
     * 차량이 아닌 기기(태블릿 시연)에서는 기본 음성 인식에 쓸 마이크 권한을 묻는다.
     */
    private fun requestPermissions() {
        val wanted = if (viewModel.isAutomotive) VEHICLE_PERMISSIONS else listOf(Manifest.permission.RECORD_AUDIO)
        val missing = wanted.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) viewModel.onVehiclePermissionResult() else permissions.launch(missing.toTypedArray())
    }

    private companion object {
        val VEHICLE_PERMISSIONS = listOf(
            "android.car.permission.CAR_SPEED",
            "android.car.permission.CAR_EXTERIOR_ENVIRONMENT",
            "android.car.permission.CAR_POWERTRAIN",
        )
    }
}
