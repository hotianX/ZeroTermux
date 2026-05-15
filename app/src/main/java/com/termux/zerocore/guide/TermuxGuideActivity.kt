package com.termux.zerocore.guide

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.example.xh_lib.utils.SaveData.getStringOther
import com.example.xh_lib.utils.SaveData.saveStringOther
import com.example.xh_lib.utils.UUtils
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.termux.R
import com.termux.app.TermuxActivity
import com.termux.zerocore.dialog.SwitchDialog
import com.termux.zerocore.ftp.utils.UserSetManage
import com.termux.zerocore.settings.BaseTitleActivity
import com.termux.zerocore.url.FileUrl.zeroTermuxApk
import com.termux.zerocore.url.FileUrl.zeroTermuxCommand
import com.termux.zerocore.url.FileUrl.zeroTermuxData
import com.termux.zerocore.url.FileUrl.zeroTermuxFont
import com.termux.zerocore.url.FileUrl.zeroTermuxHome
import com.termux.zerocore.url.FileUrl.zeroTermuxIso
import com.termux.zerocore.url.FileUrl.zeroTermuxMysql
import com.termux.zerocore.url.FileUrl.zeroTermuxOnlineSystem
import com.termux.zerocore.url.FileUrl.zeroTermuxQemu
import com.termux.zerocore.url.FileUrl.zeroTermuxServer
import com.termux.zerocore.url.FileUrl.zeroTermuxShare
import com.termux.zerocore.url.FileUrl.zeroTermuxSystem
import com.termux.zerocore.url.FileUrl.zeroTermuxWebConfig
import com.termux.zerocore.url.FileUrl.zeroTermuxWindows

class TermuxGuideActivity: BaseTitleActivity() {
    companion object {
        public val TAG = TermuxGuideActivity::class.simpleName
        public const val GUIDE_EXTRA = "zt_guide"
        public const val GUIDE_AGREEMENT = 0
        public const val GUIDE_USAGE_HABITS = 1
        public const val GUIDE_CREATE_FOLDER = 2
        public const val GUIDE_TERMUX_MAIN = 3
        public var ACTIVITYS: ArrayList<Activity>? = ArrayList<Activity>()
    }
    // 协议页面
    private var mPreviousCardView: CardView? = null
    private var mPreviousTextView: TextView? = null

    private var mNextCardView: CardView? = null
    private var mNextTextView: TextView? = null

    // 使用习惯
    private var mZeroTermuxGuideSwitch: CardView? = null
    private var mTermuxGuideSwitch: CardView? = null

    // 创建文件夹
    private var mCreateFolderSdcard: CardView? = null
    private var mCreateFolderSdcardAndroid: CardView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (getGuideCode() == GUIDE_TERMUX_MAIN || UserSetManage.get().getZTUserBean().isJumpGuide) {
            setContentView(R.layout.termux_loading)
            ACTIVITYS?.let {
                it.forEach {
                    it.finish()
                }
                it.clear()
            }
            ACTIVITYS = null
            startActivity(Intent(this@TermuxGuideActivity, TermuxActivity::class.java))
            finish()
        }
        if (getGuideCode() == GUIDE_AGREEMENT) {
            // 第一次进入
            setContentView(R.layout.activity_zt_guide_main1)
            setBaseTitle(UUtils.getString(R.string.guide_zerotermux_settings_agreement))
            goneCancelButton()
        }
        if (getGuideCode() == GUIDE_USAGE_HABITS) {
            // 使用习惯
            setContentView(R.layout.activity_zt_guide_main_usage_habits)
            setBaseTitle(UUtils.getString(R.string.guide_zerotermux_usage_habits))
        }

        if (getGuideCode() == GUIDE_CREATE_FOLDER) {
            //创建文件夹
            setContentView(R.layout.activity_zt_guide_main_create_folder)
            setBaseTitle(UUtils.getString(R.string.guide_zerotermux_create_folder))
        }
        // 协议
        mPreviousCardView = findViewById<CardView>(R.id.previous)
        mPreviousTextView = findViewById<TextView>(R.id.previous_tv)

        mNextCardView = findViewById<CardView>(R.id.next)
        mNextTextView = findViewById<TextView>(R.id.next_tv)

        // 使用习惯
        mZeroTermuxGuideSwitch = findViewById(R.id.zero_termux_guide)
        mTermuxGuideSwitch = findViewById(R.id.termux_guide)

        //创建文件夹
        mCreateFolderSdcard = findViewById(R.id.sdcard)
        mCreateFolderSdcardAndroid = findViewById(R.id.sdcard_android)
        setButtonClickListener()
    }
    // 设置隐私Button点击事件
    private fun setButtonClickListener() {
        if (getGuideCode() == GUIDE_AGREEMENT) {
            mPreviousCardView?.setOnClickListener {
                System.exit(1)
            }
            mNextCardView?.setOnClickListener {
                startGuideActivity(GUIDE_USAGE_HABITS)
            }
        }
        if (getGuideCode() == GUIDE_USAGE_HABITS) {
            switchUsageHabits(true)
            mZeroTermuxGuideSwitch?.let {
                it.setOnClickListener { switchUsageHabits(true) }
            }
            mTermuxGuideSwitch?.let {
                it.setOnClickListener { switchUsageHabits(false) }
            }
            mPreviousTextView?.text = UUtils.getString(R.string.guide_zerotermux_previous)
            mNextTextView?.text = UUtils.getString(R.string.guide_zerotermux_next)
            mPreviousCardView?.setOnClickListener {
                finish()
            }
            mNextCardView?.setOnClickListener {
                // 具体功能需要后续完善
                startGuideActivity(GUIDE_CREATE_FOLDER)
                //startGuideActivity(GUIDE_TERMUX_MAIN)
            }
        }
        if (getGuideCode() == GUIDE_CREATE_FOLDER) {
            switchPath(UserSetManage.get().getZTUserBean().isCreateFolderForSdcardAndroid)
            Log.i(TAG, "setButtonClickListener isCreateFolderForSdcardAndroid: ${UserSetManage.get().getZTUserBean().isCreateFolderForSdcardAndroid}")
            mCreateFolderSdcard?.let {
                it.setOnClickListener { switchPath(false) }
            }
            mCreateFolderSdcardAndroid?.let {
                it.setOnClickListener {
                    //switchPath(true)
                    UUtils.showMsg(UUtils.getString(R.string.guide_zerotermux_create_toast_created))
                }
            }
            mPreviousCardView?.setOnClickListener {
                finish()
            }
            mNextCardView?.setOnClickListener {
                createSdcardFiles()
            }
        }
    }
    // 使用习惯切换
    private fun switchUsageHabits(isZeroTermuxUsageHabits: Boolean) {
        mZeroTermuxGuideSwitch?.setCardBackgroundColor(getColor(R.color.color_55000000))
        mTermuxGuideSwitch?.setCardBackgroundColor(getColor(R.color.color_55000000))
        val ztUserBean = UserSetManage.get().getZTUserBean()
        if (isZeroTermuxUsageHabits) {
            ztUserBean.isToolShow = false
            ztUserBean.isResetVolume = false
            mZeroTermuxGuideSwitch?.setCardBackgroundColor(getColor(R.color.color_5548baf3))
        } else {
            ztUserBean.isToolShow = true
            ztUserBean.isResetVolume = true
            mTermuxGuideSwitch?.setCardBackgroundColor(getColor(R.color.color_5548baf3))
        }
        UserSetManage.get().setZTUserBean(ztUserBean)
    }

    private fun switchPath(isCreateFolderForSdcardAndroid: Boolean) {
        mCreateFolderSdcard?.setCardBackgroundColor(getColor(R.color.color_55000000))
        mCreateFolderSdcardAndroid?.setCardBackgroundColor(getColor(R.color.color_55000000))
        val ztUserBean = UserSetManage.get().getZTUserBean()
        ztUserBean.isCreateFolderForSdcardAndroid = isCreateFolderForSdcardAndroid
        if (isCreateFolderForSdcardAndroid) {
            mCreateFolderSdcardAndroid?.setCardBackgroundColor(getColor(R.color.color_5548baf3))
        } else {
            mCreateFolderSdcard?.setCardBackgroundColor(getColor(R.color.color_5548baf3))
        }
        UserSetManage.get().setZTUserBean(ztUserBean)
    }

    private fun startGuideActivity(code: Int) {
        if (code == GUIDE_TERMUX_MAIN) {
            val ztUserBean = UserSetManage.get().getZTUserBean();
            ztUserBean.isJumpGuide = true
            UserSetManage.get().setZTUserBean(ztUserBean)
        }
        ACTIVITYS?.add(this)
        val guideIntent = Intent()
        guideIntent.setClass(this@TermuxGuideActivity, TermuxGuideActivity::class.java)
        guideIntent.putExtra(GUIDE_EXTRA, code)
        startActivity(guideIntent)
    }

    private fun getGuideCode(): Int {
        if (intent == null) {
            return GUIDE_AGREEMENT
        }
        return intent.getIntExtra(GUIDE_EXTRA, GUIDE_AGREEMENT)
    }
    // 创建 Sdcard/Android/data/com.termux 目录
    private fun createSdcardAndroidFiles() {
        val externalFilesDir = getExternalFilesDir(null)
    }
    // 创建 Sdcard 目录
    private fun createSdcardFiles() {
        if (!zeroTermuxHome.exists()) {
            XXPermissions.with(this@TermuxGuideActivity)
                .permission(Permission.WRITE_EXTERNAL_STORAGE)
                .permission(Permission.READ_EXTERNAL_STORAGE)
                .request(object : OnPermissionCallback {
                    override fun onGranted(
                        permissions: MutableList<String?>?,
                        all: Boolean
                    ) {
                        if (all) {
                            if (!zeroTermuxHome.exists()) {
                                zeroTermuxHome.mkdirs()
                            }
                            if (!zeroTermuxData.exists()) {
                                zeroTermuxData.mkdirs()
                            }
                            if (!zeroTermuxApk.exists()) {
                                zeroTermuxApk.mkdirs()
                            }
                            if (!zeroTermuxWindows.exists()) {
                                zeroTermuxWindows.mkdirs()
                            }
                            if (!zeroTermuxCommand.exists()) {
                                zeroTermuxCommand.mkdirs()
                            }
                            if (!zeroTermuxFont.exists()) {
                                zeroTermuxFont.mkdirs()
                            }
                            if (!zeroTermuxIso.exists()) {
                                zeroTermuxIso.mkdirs()
                            }
                            if (!zeroTermuxMysql.exists()) {
                                zeroTermuxMysql.mkdirs()
                            }
                            if (!zeroTermuxOnlineSystem.exists()) {
                                zeroTermuxOnlineSystem.mkdirs()
                            }
                            if (!zeroTermuxQemu.exists()) {
                                zeroTermuxQemu.mkdirs()
                            }
                            if (!zeroTermuxServer.exists()) {
                                zeroTermuxServer.mkdirs()
                            }
                            if (!zeroTermuxShare.exists()) {
                                zeroTermuxShare.mkdirs()
                            }
                            if (!zeroTermuxSystem.exists()) {
                                zeroTermuxSystem.mkdirs()
                            }
                            if (!zeroTermuxWebConfig.exists()) {
                                zeroTermuxWebConfig.mkdirs()
                            }
                            UUtils.showMsg(UUtils.getString(R.string.guide_zerotermux_create_toast_ok))
                        } else {
                            UUtils.showMsg(UUtils.getString(R.string.guide_zerotermux_create_toast_no))
                        }
                        startGuideActivity(GUIDE_TERMUX_MAIN)
                    }
                    override fun onDenied(
                        permissions: MutableList<String?>?,
                        never: Boolean
                    ) {
                        if (never) {
                            UUtils.showMsg(UUtils.getString(R.string.guide_zerotermux_create_toast_no))
                            // 如果是被永久拒绝就跳转到应用权限系统设置页面
                            XXPermissions.startPermissionActivity(
                                this@TermuxGuideActivity,
                                permissions
                            )
                        } else {
                            UUtils.showMsg(UUtils.getString(R.string.guide_zerotermux_create_toast_no))
                        }
                        startGuideActivity(GUIDE_TERMUX_MAIN)
                    }
                })
        } else {
            startGuideActivity(GUIDE_TERMUX_MAIN)
        }
    }
}
