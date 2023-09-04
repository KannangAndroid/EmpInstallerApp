package com.nibav.installer

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.app.admin.SystemUpdatePolicy
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.SessionParams
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.nibav.installer.databinding.ActivityMainBinding
import com.nibav.receiver.ApkInstallResultReceiver
import com.nibav.util.appPackages
import com.nibav.util.getUpdateApkFile
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Arrays

class MainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null
    private var mAdminComponentName: ComponentName? = null
    private var mDevicePolicyManager: DevicePolicyManager? = null

    private var isUpdate = false
    private var clearRestrictions = false
    private var fileName: String? = null
    private var isNewPackage: Boolean? = null
    private var newPackage: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        isUpdate = intent.getBooleanExtra("isUpdate", false)
        fileName = intent.getStringExtra("fileName")
        clearRestrictions = intent.getBooleanExtra("clearRestrictions", false)
        isNewPackage = intent.getBooleanExtra("isNewPackage", false)
        newPackage = intent.getStringExtra("newPackage")
        handleRequiredPermissions()
        mDevicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        mAdminComponentName = ComponentName(this, DeviceAdmin::class.java)
        if (!mDevicePolicyManager!!.isAdminActive(mAdminComponentName!!)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mAdminComponentName)
            adminAccessResult.launch(intent)
        } else {
            checkIfAppIsOwner()
            handleRequiredPermissions()
        }
    }


    private fun upgrade() {
        Thread {
            val apkFileName = "update.apk"
            val apkFile = getUpdateApkFile(apkFileName)
            val packageInstaller = packageManager.packageInstaller
            Log.d("OTA_UPDATE", "OTA_START")
            val params = SessionParams(
                SessionParams.MODE_FULL_INSTALL
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
                val intent = Intent(this, ApkInstallResultReceiver::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    this, 123, intent, PendingIntent.FLAG_IMMUTABLE
                )

                // Commit the session (start the installation)
                session.commit(pendingIntent.intentSender)
                Log.d("OTA_UPDATE", "OTA_COMPLETE")
            } catch (e: IOException) {
                e.printStackTrace()
                Log.d("OTA_UPDATE", "OTA_ERROR")
                Log.d("OTA_UPDATE", e.message.toString())
                openNibavApp()
                // Handle exception
            } finally {
                // openInstalledApp()
            }
        }.start()
    }

    private val adminAccessResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            checkIfAppIsOwner()
            handleRequiredPermissions()
        } else finishAffinity()
    }

    private fun checkIfAppIsOwner() {
        if (mDevicePolicyManager!!.isDeviceOwnerApp(packageName)) {
            if (isUpdate)
                updateIfNewVersionAvailable()
            else
                setDefaultPolicies(!clearRestrictions)
        }
    }

    private fun updateIfNewVersionAvailable() {
        if (isUpdate) {
            upgrade()
        }
    }

    override fun onBackPressed() {
    }

    private fun handleRequiredPermissions() {
        if (!packageManager.canRequestPackageInstalls()) {
            requestInstallPackagesResult.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse(String.format("package:%s", packageName)))
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) requestManageStoragePermissions()
        }
    }

    private val requestInstallPackagesResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult? ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) requestManageStoragePermissions()
        }
    }

    private fun requestManageStoragePermissions() {
        val intent = Intent()
        intent.action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }


    public override fun onResume() {
        super.onResume()
    }

    private fun setDefaultPolicies(active: Boolean) {
        try {
            if (!isNewPackage!!) {
                // Set user restrictions
                setUserRestriction(UserManager.DISALLOW_SAFE_BOOT, active)
                setUserRestriction(UserManager.DISALLOW_FACTORY_RESET, active)
                setUserRestriction(UserManager.DISALLOW_ADD_USER, active)
                setUserRestriction(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, active)
                setUserRestriction(UserManager.DISALLOW_USB_FILE_TRANSFER, active)
            }
            // Disable keyguard and status bar
            mDevicePolicyManager!!.setKeyguardDisabled(mAdminComponentName!!, active)
            mDevicePolicyManager!!.setStatusBarDisabled(mAdminComponentName!!, active)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                mDevicePolicyManager!!.setLocationEnabled(mAdminComponentName!!, active)
            }

            // Set system update policy
            if (active) {
                mDevicePolicyManager!!.setSystemUpdatePolicy(
                    mAdminComponentName!!,
                    SystemUpdatePolicy.createWindowedInstallPolicy(60, 120)
                )
            } else {
                mDevicePolicyManager!!.setSystemUpdatePolicy(mAdminComponentName!!, null)
            }

            // set this Activity as a lock task package
            val filter = IntentFilter(Intent.ACTION_MAIN)
            filter.addCategory(Intent.CATEGORY_HOME)
            filter.addCategory(Intent.CATEGORY_DEFAULT)
            mDevicePolicyManager!!.addPersistentPreferredActivity(
                mAdminComponentName!!, filter,
                ComponentName("com.nibav.employee", "com.nibav.employee.HomeActivity")
            )

            //if (isUsbDebugEnabled) {
            if (isNewPackage!!) appPackages.add(newPackage)
            mDevicePolicyManager!!.setLockTaskPackages(
                mAdminComponentName!!,
                if (active) appPackages.toTypedArray() else arrayOf()
            )
            if (active && !isUpdate) {
                Log.d("NibavUpdater", "openApp")
                openNibavApp()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun nibavPackageInfo(packageManager: PackageManager): PackageInfo? {
        return try {
            packageManager.getPackageInfo(
                "com.nibav.employee",
                PackageManager.GET_ACTIVITIES
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun openNibavApp() {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val packageManger = packageManager
                val pi = nibavPackageInfo(packageManger)
                if (pi != null) {
                    Intent().apply {
                        component = ComponentName(
                            "com.nibav.employee",
                            "com.nibav.employee.HomeActivity"
                        )
                        startActivity(this)
                        finishAffinity()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 1000)
    }

    private fun setUserRestriction(restriction: String, disallow: Boolean) {
        try {
            if (disallow) {
                mDevicePolicyManager!!.addUserRestriction(mAdminComponentName!!, restriction)
            } else {
                mDevicePolicyManager!!.clearUserRestriction(mAdminComponentName!!, restriction)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}