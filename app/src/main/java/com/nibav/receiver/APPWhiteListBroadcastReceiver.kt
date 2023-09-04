package com.nibav.receiver

import android.app.admin.DevicePolicyManager
import android.app.admin.SystemUpdatePolicy
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.nibav.installer.DeviceAdmin
import com.nibav.util.appPackages

class APPWhiteListBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(mContext: Context, intent: Intent) {
        if (intent.action == "com.nibav.installer.whiteList_kiosk") {
            val whiteListedApps = intent.getStringArrayListExtra("whiteListedApps") ?: arrayListOf()
            val isActive = intent.getBooleanExtra("isActive", true)
            Log.d("OTA_KIOSK", isActive.toString())
            setDefaultPolicies(mContext, whiteListedApps, isActive)
        }
    }


    private fun setDefaultPolicies(
        context: Context,
        whiteListedApps: ArrayList<String>,
        active: Boolean,
    ) {
        try {
            val mDevicePolicyManager =
                context.getSystemService(AppCompatActivity.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val mAdminComponentName = ComponentName(context, DeviceAdmin::class.java)
            if (!mDevicePolicyManager.isAdminActive(mAdminComponentName)) {
                return
            }

            if (active) {
                mDevicePolicyManager.addUserRestriction(
                    mAdminComponentName,
                    UserManager.DISALLOW_SAFE_BOOT
                )
                mDevicePolicyManager.addUserRestriction(
                    mAdminComponentName,
                    UserManager.DISALLOW_FACTORY_RESET
                )
                mDevicePolicyManager.addUserRestriction(
                    mAdminComponentName,
                    UserManager.DISALLOW_ADD_USER
                )
                mDevicePolicyManager.addUserRestriction(
                    mAdminComponentName,
                    UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA
                )
                mDevicePolicyManager.addUserRestriction(
                    mAdminComponentName,
                    UserManager.DISALLOW_USB_FILE_TRANSFER
                )
            } else {
                mDevicePolicyManager.clearUserRestriction(
                    mAdminComponentName,
                    UserManager.DISALLOW_SAFE_BOOT
                )
                mDevicePolicyManager.clearUserRestriction(
                    mAdminComponentName,
                    UserManager.DISALLOW_FACTORY_RESET
                )
                mDevicePolicyManager.clearUserRestriction(
                    mAdminComponentName,
                    UserManager.DISALLOW_ADD_USER
                )
                mDevicePolicyManager.clearUserRestriction(
                    mAdminComponentName,
                    UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA
                )
                mDevicePolicyManager.clearUserRestriction(
                    mAdminComponentName,
                    UserManager.DISALLOW_USB_FILE_TRANSFER
                )
            }

            mDevicePolicyManager.setKeyguardDisabled(mAdminComponentName, active)
            mDevicePolicyManager.setStatusBarDisabled(mAdminComponentName, active)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                mDevicePolicyManager.setLocationEnabled(mAdminComponentName, active)
            }

            mDevicePolicyManager.setSystemUpdatePolicy(
                mAdminComponentName,
                SystemUpdatePolicy.createWindowedInstallPolicy(60, 120)
            )


            // set this Activity as a lock task package
            val filter = IntentFilter(Intent.ACTION_MAIN)
            filter.addCategory(Intent.CATEGORY_HOME)
            filter.addCategory(Intent.CATEGORY_DEFAULT)
            mDevicePolicyManager.addPersistentPreferredActivity(
                mAdminComponentName, filter,
                ComponentName("com.nibav.employee", "com.nibav.employee.HomeActivity")
            )
            appPackages.addAll(whiteListedApps)
            mDevicePolicyManager.setLockTaskPackages(
                mAdminComponentName,
                appPackages.toTypedArray()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


}