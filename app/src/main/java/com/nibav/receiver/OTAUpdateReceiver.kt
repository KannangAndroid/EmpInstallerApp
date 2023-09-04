package com.nibav.receiver

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.app.admin.SystemUpdatePolicy
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.nibav.installer.DeviceAdmin
import com.nibav.util.appPackages
import com.nibav.util.getUpdateApkFile
import java.io.FileInputStream
import java.io.IOException

class OTAUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(mContext: Context, intent: Intent) {
        if (intent.action == "com.nibav.installer.OTA") {
            val fileName = intent.getStringExtra("fileName") ?: ""
            val updatePackageName = intent.getStringExtra("packageName") ?: ""
            Log.d("OTA_UPDATE", "OTA_RECEIVED_FILE")
            Log.d("OTA_UPDATE", fileName)
            Log.d("OTA_UPDATE", updatePackageName)
            if (!fileName.isNullOrEmpty())
                installNewAPK(mContext, fileName,updatePackageName)
        }
    }


    private fun installNewAPK(context: Context, apkFileName: String,updatePackageName:String) {
        val apkFile = getUpdateApkFile(apkFileName) ?: return
        val packageInstaller = context.packageManager.packageInstaller
        Log.d("OTA_UPDATE", "OTA_START")
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )

        try {
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)
            FileInputStream(apkFile).use { `in` ->
                session.openWrite("package", 0, -1).use { out ->
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    while (`in`.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                    session.fsync(out)
                }
            }

            // Create an Intent to start the PackageInstaller's installation UI
            val intent = Intent(context, ApkInstallResultReceiver::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 123, intent, PendingIntent.FLAG_IMMUTABLE
            )

            // Commit the session (start the installation)
            session.commit(pendingIntent.intentSender)
            Log.d("OTA_UPDATE", "OTA_COMPLETE")
        } catch (e: IOException) {
            e.printStackTrace()
            Log.d("OTA_UPDATE", "OTA_ERROR")
            Log.d("OTA_UPDATE", e.message.toString())
            // Handle exception
        }finally {
            setDefaultPolicies(updatePackageName,context)
        }
    }

    private fun setDefaultPolicies(newPackageName: String, context: Context) {
        try {
            val mDevicePolicyManager =
                context.getSystemService(AppCompatActivity.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val mAdminComponentName = ComponentName(context, DeviceAdmin::class.java)
            if (!mDevicePolicyManager!!.isAdminActive(mAdminComponentName!!)) {
                return
            }
            mDevicePolicyManager!!.setKeyguardDisabled(mAdminComponentName!!, true)
            mDevicePolicyManager!!.setStatusBarDisabled(mAdminComponentName!!, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                mDevicePolicyManager!!.setLocationEnabled(mAdminComponentName!!, true)
            }

            mDevicePolicyManager!!.setSystemUpdatePolicy(
                mAdminComponentName!!,
                SystemUpdatePolicy.createWindowedInstallPolicy(60, 120)
            )


            // set this Activity as a lock task package
            val filter = IntentFilter(Intent.ACTION_MAIN)
            filter.addCategory(Intent.CATEGORY_HOME)
            filter.addCategory(Intent.CATEGORY_DEFAULT)
            mDevicePolicyManager!!.addPersistentPreferredActivity(
                mAdminComponentName!!, filter,
                ComponentName("com.nibav.employee", "com.nibav.employee.HomeActivity")
            )
            appPackages.add(newPackageName)
            mDevicePolicyManager!!.setLockTaskPackages(
                mAdminComponentName!!,
                appPackages.toTypedArray()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


}