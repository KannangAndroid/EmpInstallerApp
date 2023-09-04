package com.nibav.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log


class ApkInstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        if (status == PackageInstaller.STATUS_SUCCESS) {
            Log.d("OTA_UPDATE", "APK installation successful")
        } else {
            Log.d("OTA_UPDATE", "APK installation failed")
        }
    }

}