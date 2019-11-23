/*
 * Copyright (c) 2019 大前良介 (OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.orientation.view

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings.System
import android.text.format.DateFormat
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout.LayoutParams
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle.State
import com.google.android.gms.ads.AdView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_main.*
import net.mm2d.android.orientationfaker.BuildConfig
import net.mm2d.android.orientationfaker.R
import net.mm2d.orientation.control.OrientationHelper
import net.mm2d.orientation.control.OverlayPermissionHelper
import net.mm2d.orientation.review.ReviewRequest
import net.mm2d.orientation.service.MainService
import net.mm2d.orientation.settings.Settings
import net.mm2d.orientation.util.LaunchUtils
import net.mm2d.orientation.util.UpdateRouter

/**
 * @author [大前良介 (OHMAE Ryosuke)](mailto:ryo@mm2d.net)
 */
class MainActivity : AppCompatActivity() {
    private val settings by lazy {
        Settings.get()
    }
    private val handler = Handler(Looper.getMainLooper())
    private val checkSystemSettingsTask = Runnable { checkSystemSettings() }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            applyStatus()
            notificationSample.update()
        }
    }
    private lateinit var notificationSample: NotificationSample
    private lateinit var adView: AdView
    private lateinit var relevantAds: MenuItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.title = getString(R.string.app_name)
        setUpViews()
        UpdateRouter.register(receiver)
        if (!OverlayPermissionHelper.canDrawOverlays(this)) {
            MainService.stop(this)
        } else {
            Settings.doOnGet {
                if (it.shouldAutoStart()) {
                    MainService.start(this)
                }
            }
        }
        setUpAdView()
        checkPermission()
    }

    private fun setUpAdView() {
        adView = AdMob.makeSettingsAdView(this)
        container.addView(adView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun checkPermission() {
        OverlayPermissionHelper.requestOverlayPermissionIfNeed(
            this,
            REQUEST_CODE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE) {
            handler.postDelayed({ checkPermission() }, 1000)
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        super.onDestroy()
        UpdateRouter.unregister(receiver)
    }

    override fun onResume() {
        super.onResume()
        notificationSample.update()
        AdMob.loadAd(this, adView)
        handler.removeCallbacks(checkSystemSettingsTask)
        handler.post(checkSystemSettingsTask)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(checkSystemSettingsTask)
    }

    private fun checkSystemSettings() {
        if (lifecycle.currentState != State.RESUMED) {
            return
        }
        if (!settings.autoRotateWarning) {
            caution.visibility = View.GONE
            return
        }
        kotlin.runCatching {
            val fixed = System.getInt(contentResolver, System.ACCELEROMETER_ROTATION) == 0
            if (fixed != caution.isVisible) {
                caution.visibility = if (fixed) View.VISIBLE else View.GONE
            }
            handler.postDelayed(
                checkSystemSettingsTask,
                CHECK_INTERVAL
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        relevantAds = menu.findItem(R.id.relevant_ads)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        relevantAds.isVisible = AdMob.isInEeaOrUnknown()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.license -> LicenseActivity.start(this)
            R.id.source_code -> LaunchUtils.openSourceCode(this)
            R.id.privacy_policy -> LaunchUtils.openPrivacyPolicy(this)
            R.id.play_store -> LaunchUtils.openGooglePlay(this)
            R.id.relevant_ads -> AdMob.updateConsent(this)
        }
        return true
    }

    private fun setUpViews() {
        notificationSample = NotificationSample(this)
        status.setOnClickListener { toggleStatus() }
        auto_start.setOnClickListener { toggleAutoStart() }
        detailed_setting.setOnClickListener { DetailedSettingsActivity.start(this) }
        version_description.text = makeVersionInfo()
        setUpOrientationIcons()
        applyStatus()
        Settings.doOnGet {
            applyAutoStart()
        }
    }

    private fun setUpOrientationIcons() {
        notificationSample.buttonList.forEach { view ->
            view.button.setOnClickListener { updateOrientation(view.orientation) }
        }
    }

    private fun toggleStatus() {
        if (OrientationHelper.isEnabled) {
            MainService.stop(this)
            if (settings.shouldAutoStart()) {
                settings.setAutoStart(false)
                applyAutoStart()
            }
        } else {
            MainService.start(this)
        }
    }

    private fun applyStatus() {
        status.isChecked = OrientationHelper.isEnabled
        ReviewRequest.requestReviewIfNeed(this)
    }

    private fun toggleAutoStart() {
        settings.setAutoStart(!settings.shouldAutoStart())
        applyAutoStart()
        if (settings.shouldAutoStart() && !OrientationHelper.isEnabled) {
            MainService.start(this)
        }
    }

    private fun applyAutoStart() {
        auto_start.isChecked = settings.shouldAutoStart()
    }

    private fun updateOrientation(orientation: Int) {
        settings.orientation = orientation
        notificationSample.update()
        if (OrientationHelper.isEnabled) {
            MainService.start(this)
        }
    }

    private fun makeVersionInfo(): String {
        return BuildConfig.VERSION_NAME +
            if (BuildConfig.DEBUG)
                " # " + DateFormat.format("yyyy/M/d kk:mm:ss", BuildConfig.BUILD_TIME)
            else ""
    }

    companion object {
        private const val REQUEST_CODE = 101
        private const val CHECK_INTERVAL = 5000L
    }
}
