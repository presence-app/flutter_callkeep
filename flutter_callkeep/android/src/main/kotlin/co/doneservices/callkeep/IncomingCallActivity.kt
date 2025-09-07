package co.doneservices.callkeep

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import co.doneservices.callkeep.CallKeepBroadcastReceiver.Companion.ACTION_CALL_INCOMING
import co.doneservices.callkeep.CallKeepBroadcastReceiver.Companion.EXTRA_CALLKEEP_AVATAR
import co.doneservices.callkeep.CallKeepBroadcastReceiver.Companion.EXTRA_CALLKEEP_DURATION
import co.doneservices.callkeep.CallKeepBroadcastReceiver.Companion.EXTRA_CALLKEEP_INCOMING_DATA
import co.doneservices.callkeep.CallKeepBroadcastReceiver.Companion.EXTRA_CALLKEEP_CALLER_NAME
import co.doneservices.callkeep.CallKeepBroadcastReceiver.Companion.EXTRA_CALLKEEP_CONTENT_TITLE
import com.squareup.picasso.Picasso
import kotlin.math.abs
import android.os.PowerManager

class IncomingCallActivity : Activity() {

    companion object {
        const val ACTION_ENDED_CALL_INCOMING = "co.doneservices.callkeep.ACTION_ENDED_CALL_INCOMING"

        fun getIntent(context: Context, data: Bundle) = Intent(ACTION_CALL_INCOMING).apply {
            action = "${context.packageName}.${ACTION_CALL_INCOMING}"
            putExtra(EXTRA_CALLKEEP_INCOMING_DATA, data)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }

        fun getIntentEnded(context: Context): Intent {
            return Intent("${context.packageName}.$ACTION_ENDED_CALL_INCOMING")
        }
    }

    inner class EndedCallKeepBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isFinishing) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    finishAndRemoveTask()
                } else {
                    finish()
                }
            }
        }
    }

    private var endedCallKeepBroadcastReceiver = EndedCallKeepBroadcastReceiver()

    private lateinit var llBackground: LinearLayout
    private lateinit var tvCallerName: TextView
    private lateinit var tvCallHeader: TextView
    private lateinit var ivLogo: ImageView
    private lateinit var btnAnswer: ImageButton
    private lateinit var btnDecline: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setTurnScreenOn(true)
            setShowWhenLocked(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }
        setTransparentStatusAndNavigation()
        setContentView(R.layout.activity_call_incoming)
        initView()
        updateViewWithIncomingIntentData(intent)

        val filter = IntentFilter("${packageName}.${ACTION_ENDED_CALL_INCOMING}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(endedCallKeepBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(endedCallKeepBroadcastReceiver, filter)
        }
    }

    private fun wakeLockRequest(duration: Long) {
        val pm = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "CallKeep:PowerManager"
        )
        wakeLock.acquire(duration)
    }

    private fun setTransparentStatusAndNavigation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            setWindowFlag(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setWindowFlag(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
    }

    private fun setWindowFlag(bits: Int, on: Boolean) {
        val win: Window = window
        val winParams = win.attributes
        winParams.flags = if (on) winParams.flags or bits else winParams.flags and bits.inv()
        win.attributes = winParams
    }

    private fun updateViewWithIncomingIntentData(intent: Intent) {
        val data = intent.extras?.getBundle(EXTRA_CALLKEEP_INCOMING_DATA)
        if (data == null) finish()

        // Set CallerName (eg John Doe) and CallHeader (eg, Incoming Call)
        // from EXTRA_CALLKEEP_CALLER_NAME and EXTRA_CALLKEEP_CONTENT_TITLE
        tvCallerName.text = data?.getString(EXTRA_CALLKEEP_CALLER_NAME, "")
        tvCallHeader.text = data?.getString(EXTRA_CALLKEEP_CONTENT_TITLE, "")

        // Set avatarUrl from EXTRA_CALLKEEP_AVATAR
        val avatarUrl = data?.getString(EXTRA_CALLKEEP_AVATAR)
        val avatarDrawable = if (!avatarUrl.isNullOrEmpty()) {
            avatarUrl
        } else {
            null
        }

        // If avatarUrl is not available, display default ic_default_avatar
        ivLogo.visibility = View.VISIBLE
        if (avatarDrawable != null) {
            Picasso.get().load(avatarDrawable).into(ivLogo)
        } else {
            ivLogo.setImageResource(R.drawable.ic_default_avatar)
        }

        val duration = data?.getLong(EXTRA_CALLKEEP_DURATION, 0L) ?: 0L
        wakeLockRequest(duration)
        finishTimeout(data, duration)
        applyGradientBackground() // Apply background gradient color
    }

    // Set background gradient blue color
    private fun applyGradientBackground() {
        val colors = intArrayOf(Color.parseColor("#456D91"), Color.parseColor("#2A5379"))
        val gradient = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, colors)
        gradient.cornerRadius = 0f
        llBackground.background = gradient
    }

    private fun finishTimeout(data: Bundle?, duration: Long) {
        val currentSystemTime = System.currentTimeMillis()
        val timeStartCall = data?.getLong(CallKeepNotificationManager.EXTRA_TIME_START_CALL, currentSystemTime)
            ?: currentSystemTime
        val timeOut = duration - abs(currentSystemTime - timeStartCall)
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) finishAndRemoveTask() else finish()
            }
        }, timeOut)
    }

    private fun initView() {
        llBackground = findViewById(R.id.llBackground)
        tvCallerName = findViewById(R.id.tvCallerName)
        tvCallHeader = findViewById(R.id.tvCallHeader)
        ivLogo = findViewById(R.id.ivLogo)
        btnAnswer = findViewById(R.id.btnAnswer)
        btnDecline = findViewById(R.id.btnDecline)

        btnAnswer.setOnClickListener { onAcceptClick() }
        btnDecline.setOnClickListener { onDeclineClick() }
    }

    private fun onAcceptClick() {
        val data = intent.extras?.getBundle(EXTRA_CALLKEEP_INCOMING_DATA)
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.cloneFilter()
        if (isTaskRoot) {
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        } else {
            intent?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (intent != null) {
            val intentTransparent = TransparentActivity.getIntentAccept(this, data)
            startActivities(arrayOf(intent, intentTransparent))
        } else {
            val acceptIntent = CallKeepBroadcastReceiver.getIntentAccept(this, data)
            sendBroadcast(acceptIntent)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) finishAndRemoveTask() else finish()
    }

    private fun onDeclineClick() {
        val data = intent.extras?.getBundle(EXTRA_CALLKEEP_INCOMING_DATA)
        val intent = CallKeepBroadcastReceiver.getIntentDecline(this, data)
        sendBroadcast(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) finishAndRemoveTask() else finish()
    }

    override fun onDestroy() {
        unregisterReceiver(endedCallKeepBroadcastReceiver)
        super.onDestroy()
    }

    override fun onBackPressed() {}
}
