/*
 * Copyright (c) 2018 大前良介 (OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.orientation.view

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout.LayoutParams
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdView
import kotlinx.android.synthetic.main.activity_detailed_settings.*
import kotlinx.android.synthetic.main.layout_detailed_settings.*
import net.mm2d.android.orientationfaker.BuildConfig
import net.mm2d.android.orientationfaker.R
import net.mm2d.color.chooser.ColorChooserDialog
import net.mm2d.orientation.control.OrientationHelper
import net.mm2d.orientation.control.Orientations
import net.mm2d.orientation.event.EventObserver
import net.mm2d.orientation.event.EventRouter
import net.mm2d.orientation.service.MainService
import net.mm2d.orientation.settings.Default
import net.mm2d.orientation.settings.OrientationList
import net.mm2d.orientation.settings.Settings
import net.mm2d.orientation.util.AdMob
import net.mm2d.orientation.view.dialog.ResetButtonDialog
import net.mm2d.orientation.view.dialog.ResetThemeDialog
import net.mm2d.orientation.view.view.CheckItemView

class DetailedSettingsActivity : AppCompatActivity(),
    ResetThemeDialog.Callback,
    ResetButtonDialog.Callback,
    ColorChooserDialog.Callback {
    private val settings by lazy {
        Settings.get()
    }
    private val eventObserver: EventObserver = EventRouter.createUpdateObserver()
    private lateinit var notificationSample: NotificationSample
    private lateinit var checkList: List<CheckItemView>
    private val orientationList: MutableList<Int> = mutableListOf()
    private lateinit var orientationListStart: List<Int>
    private lateinit var adView: AdView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detailed_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setUpViews()
        eventObserver.subscribe { notificationSample.update() }
        setUpAdView()
    }

    private fun setUpAdView() {
        adView = AdMob.makeDetailedAdView(this)
        container.addView(adView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    override fun onDestroy() {
        super.onDestroy()
        eventObserver.unsubscribe()
        if (!orientationList.contains(settings.orientation)) {
            settings.orientation = orientationList[0]
            MainService.update(this)
            if (!OrientationHelper.isEnabled) {
                EventRouter.notifyUpdate()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (orientationListStart != orientationList) {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        notificationSample.update()
        applyOrientationSelection()
        applyUseBlankIcon()
        applyAutoRotateWarning()
        applyNotificationPrivacy()
        AdMob.loadAd(this, adView)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setUpViews() {
        notificationSample = NotificationSample(this)
        setUpSample()
        setUpOrientationSelector()
        setUpUseBlankIcon()
        setUpAutoRotateWarning()
        setUpNotificationPrivacy()
        setUpSystemSetting()
    }

    private fun setUpSample() {
        sample_foreground.setColorFilter(settings.foregroundColor)
        sample_background.setColorFilter(settings.backgroundColor)
        sample_foreground_selected.setColorFilter(settings.foregroundColorSelected)
        sample_background_selected.setColorFilter(settings.backgroundColorSelected)
        foreground.setOnClickListener {
            ColorChooserDialog.show(this, it.id, settings.foregroundColor)
        }
        background.setOnClickListener {
            ColorChooserDialog.show(this, it.id, settings.backgroundColor)
        }
        foreground_selected.setOnClickListener {
            ColorChooserDialog.show(this, it.id, settings.foregroundColorSelected)
        }
        background_selected.setOnClickListener {
            ColorChooserDialog.show(this, it.id, settings.backgroundColorSelected)
        }
        reset_theme.setOnClickListener { ResetThemeDialog.show(this) }
        setUpOrientationIcons()
    }

    private fun setUpOrientationIcons() {
        notificationSample.buttonList.forEach { view ->
            view.button.setOnClickListener { updateOrientation(view.orientation) }
        }
    }

    private fun updateOrientation(orientation: Int) {
        settings.orientation = orientation
        notificationSample.update()
        MainService.update(this)
    }

    override fun onColorChooserResult(requestCode: Int, resultCode: Int, color: Int) {
        if (resultCode != Activity.RESULT_OK) return
        when (requestCode) {
            R.id.foreground -> {
                settings.foregroundColor = color
                sample_foreground.setColorFilter(color)
            }
            R.id.background -> {
                settings.backgroundColor = color
                sample_background.setColorFilter(color)
            }
            R.id.foreground_selected -> {
                settings.foregroundColorSelected = color
                sample_foreground_selected.setColorFilter(color)
            }
            R.id.background_selected -> {
                settings.backgroundColorSelected = color
                sample_background_selected.setColorFilter(color)
            }
        }
        notificationSample.update()
        MainService.update(this)
    }

    override fun resetTheme() {
        settings.resetTheme()
        sample_foreground.setColorFilter(settings.foregroundColor)
        sample_background.setColorFilter(settings.backgroundColor)
        sample_foreground_selected.setColorFilter(settings.foregroundColorSelected)
        sample_background_selected.setColorFilter(settings.backgroundColorSelected)
        notificationSample.update()
        MainService.update(this)
    }

    private fun setUpOrientationSelector() {
        orientationListStart = settings.orientationList
        orientationList.addAll(orientationListStart)
        checkList = listOf(
            check_orientation1,
            check_orientation2,
            check_orientation3,
            check_orientation4,
            check_orientation5,
            check_orientation6,
            check_orientation7,
            check_orientation8
        )
        checkList.forEachIndexed { index, view ->
            val orientation = Orientations.values[index]
            view.orientation = orientation.value
            view.setIcon(orientation.icon)
            view.setText(orientation.label)
        }
        checkList.forEach { view ->
            view.setOnClickListener {
                onClickCheckItem(view)
                updateCaution()
            }
        }
        applyOrientationSelection()
        reset_button.setOnClickListener { ResetButtonDialog.show(this) }
        updateCaution()
    }

    private fun updateCaution() {
        if (orientationList.contains(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT) ||
            orientationList.contains(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
        ) {
            caution.visibility = View.VISIBLE
        } else {
            caution.visibility = View.GONE
        }
    }

    private fun onClickCheckItem(view: CheckItemView) {
        if (view.isChecked) {
            if (orientationList.size <= OrientationList.MIN) {
                Toast.makeText(this, R.string.toast_select_item_min, Toast.LENGTH_LONG).show()
            } else {
                orientationList.remove(view.orientation)
                view.isChecked = false
                updateOrientationSelector()
            }
        } else {
            if (orientationList.size >= OrientationList.MAX) {
                Toast.makeText(this, R.string.toast_select_item_max, Toast.LENGTH_LONG).show()
            } else {
                orientationList.add(view.orientation)
                view.isChecked = true
                updateOrientationSelector()
            }
        }
    }

    private fun updateOrientationSelector() {
        settings.orientationList = orientationList
        notificationSample.update()
        MainService.update(this)
    }

    override fun resetOrientation() {
        orientationList.clear()
        orientationList.addAll(Default.orientationList)
        applyOrientationSelection()
        updateOrientationSelector()
    }

    private fun applyOrientationSelection() {
        checkList.forEach { view ->
            view.isChecked = orientationList.contains(view.orientation)
        }
    }

    private fun setUpUseBlankIcon() {
        use_blank_icon_for_notification.setOnClickListener { toggleUseBlankIcon() }
    }

    private fun applyUseBlankIcon() {
        use_blank_icon_for_notification.isChecked = settings.shouldUseBlankIconForNotification
    }

    private fun toggleUseBlankIcon() {
        settings.shouldUseBlankIconForNotification = !settings.shouldUseBlankIconForNotification
        applyUseBlankIcon()
        MainService.update(this)
    }

    private fun setUpAutoRotateWarning() {
        auto_rotate_warning.setOnClickListener { toggleAutoRotateWarning() }
    }

    private fun applyAutoRotateWarning() {
        auto_rotate_warning.isChecked = settings.autoRotateWarning
    }

    private fun toggleAutoRotateWarning() {
        settings.autoRotateWarning = !settings.autoRotateWarning
        applyAutoRotateWarning()
    }

    private fun setUpNotificationPrivacy() {
        notification_privacy.setOnClickListener { toggleNotificationPrivacy() }
    }

    private fun applyNotificationPrivacy() {
        notification_privacy.isChecked = settings.notifySecret
    }

    private fun toggleNotificationPrivacy() {
        settings.notifySecret = !settings.notifySecret
        applyNotificationPrivacy()
        MainService.update(this)
    }

    private fun setUpSystemSetting() {
        system_app.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).also {
                it.data = Uri.parse("package:" + BuildConfig.APPLICATION_ID)
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
        system_notification.setOnClickListener {
            runCatching {
                startActivity(Intent(ACTION_APP_NOTIFICATION_SETTINGS).also {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    it.putExtra("app_package", BuildConfig.APPLICATION_ID)
                    it.putExtra("app_uid", applicationInfo.uid)
                    it.putExtra("android.provider.extra.APP_PACKAGE", BuildConfig.APPLICATION_ID)
                })
            }
        }
    }

    companion object {
        private const val ACTION_APP_NOTIFICATION_SETTINGS =
            "android.settings.APP_NOTIFICATION_SETTINGS"

        fun start(context: Context) {
            context.startActivity(Intent(context, DetailedSettingsActivity::class.java))
        }
    }
}
