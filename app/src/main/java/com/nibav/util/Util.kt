package com.nibav.util

import android.os.Environment
import java.io.File


private const val KIOSK_PACKAGE = "com.nibav.employee"
private const val APP_INSTALLER_PACKAGE = "com.nibav.installer"

val appPackages = ArrayList(
    listOf(
        KIOSK_PACKAGE,
        APP_INSTALLER_PACKAGE,
        "android.content.pm.PackageManager",
        "android.content.pm.PackageInstaller",
        "com.android.systemui"
    )
)

fun getUpdateApkFile(fileName: String): File? {
    var directory: File?
    directory = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .toString() + "/Nibav/employee/$fileName"
    )
    if (!directory.exists()) {
        // Make it, if it doesn't exit
        val success = directory.mkdirs()
        if (!success) {
            directory = null
        }
    }
    return directory
}