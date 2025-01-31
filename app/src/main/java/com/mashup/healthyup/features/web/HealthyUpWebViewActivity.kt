package com.mashup.healthyup.features.web

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.mashup.healthyup.Key
import com.mashup.healthyup.R
import com.mashup.healthyup.base.BaseActivity
import com.mashup.healthyup.bridge.HealthyUpWebViewClient
import com.mashup.healthyup.bridge.HealthyUpWebViewClient.Action.RemoveLoadingView
import com.mashup.healthyup.bridge.WebAPIController
import com.mashup.healthyup.bridge.WebAPIController.FunctionName
import com.mashup.healthyup.bridge.WebPreference
import com.mashup.healthyup.core.Empty
import com.mashup.healthyup.databinding.ActivityHealthyUpWebViewBinding
import com.mashup.healthyup.domain.entity.Exercise
import com.mashup.healthyup.features.exercise.ExerciseDashboardActivity
import com.mashup.healthyup.features.history.HistoryActivity
import com.mashup.healthyup.features.setting.SettingActivity
import com.mashup.healthyup.features.web.WebConstants.Target
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class HealthyUpWebViewActivity :
    BaseActivity<ActivityHealthyUpWebViewBinding>(R.layout.activity_healthy_up_web_view) {

    @Inject
    lateinit var webPreference: WebPreference

    private val viewModel by viewModels<HealthyUpWebViewViewModel>()
    private var backKeyPressedTime: Long = 0

    private val loadUrl by lazy {
        intent?.getStringExtra(Key.LOAD_URL) ?: String.Empty
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeWebViewEvent()
        observeWebRequest()
    }

    override fun initViews() {
        super.initViews()
        setStatusBarColor()
        binding.viewModel = viewModel
        binding.healthyUpWebView.setJavaScriptInterface(webPreference)
        Glide.with(this).load(R.raw.img_loading).into(binding.ivLoading)
        binding.healthyUpWebView.loadUrl(loadUrl)
    }

    private fun setStatusBarColor() {
        val colorResId = when (loadUrl) {
            WebConstants.URL.HOME ->  R.color.color_primary_variant1 // #F9F7FC
            else -> R.color.color_background // #F8F8F8
        }

        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = ContextCompat.getColor(this@HealthyUpWebViewActivity, colorResId)
        }
    }

    private fun observeWebViewEvent() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                HealthyUpWebViewClient.actionFlow.collect { action ->
                    when (action) {
                        RemoveLoadingView -> {
                            binding.cvLoading.isVisible = false
                            binding.ivLoading.isVisible = false
                        }
                    }
                }
            }
        }
    }

    private fun observeWebRequest() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WebAPIController.channelFlow.collect { jsonObject ->
                    when (jsonObject.get(WebConstants.FUNCTION_NAME).asString) {
                        FunctionName.START_ACTIVITY -> startActivityFromWeb(jsonObject)
                        FunctionName.SET_BACK_BUTTON_RECEIVE -> {
                            binding.viewModel?.backButtonReceiveTarget = jsonObject.get("target").asString
                        }
                        FunctionName.CLOSE_WEB_VIEW -> finish()
                    }
                }
            }
        }
    }

    private fun startActivityFromWeb(options: JsonObject) {
        when (options.get("target").asString) {
            Target.SETTING -> {
                SettingActivity.start(this) {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            }
            Target.HISTORY -> {
                HistoryActivity.start(this) {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            }
            Target.HOME -> {
                // TODO: 웹에서 내려온, home url, access token, 만료시간을 파싱해서 처리
                val loadUrl = options.get("loadUrl").asString
                start(this, loadUrl) {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            Target.EXERCISE -> {
                val exerciseArray: JsonArray =
                    Gson().fromJson(options.get("exerciseList").asString, JsonArray::class.java)
                Log.d("HealthyUpWebViewActivity", "exerciseArray: $exerciseArray")

                val exerciseList: ArrayList<Exercise> = Gson().fromJson(
                    exerciseArray,
                    object : TypeToken<ArrayList<Exercise?>?>() {}.type
                )
                ExerciseDashboardActivity.start(this, exerciseList) {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            }
        }
    }

    override fun onBackPressed() {
        handleOnBackPressed(binding.viewModel?.backButtonReceiveTarget ?: Target.ANDROID)
    }

    private fun handleOnBackPressed(target: String) {
        when (target) {
            Target.ANDROID -> {
                if (binding.healthyUpWebView.canGoBack()) {
                    binding.healthyUpWebView.goBack()
                } else if (binding.healthyUpWebView.url == WebConstants.URL.SPLIT) {
                    finish()
                } else {
                    if (backKeyPressedTime + 2500 < System.currentTimeMillis()) {
                        backKeyPressedTime = System.currentTimeMillis()
                        Toast.makeText(this, R.string.back_button_finish_alert, Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        super.onBackPressed()
                    }
                }
            }
            Target.WEB -> {
                WebAPIController.sendNativeEvent(FunctionName.ON_BACK_BUTTON_PRESSED, null)
            }
        }
    }

    companion object {

        fun intent(context: Context): Intent {
            return Intent(context, HealthyUpWebViewActivity::class.java)
        }

        fun start(context: Context, action: Intent.() -> Unit = {}) {
            val intent = Intent(context, HealthyUpWebViewActivity::class.java).apply(action)
            context.startActivity(intent)
        }

        fun start(context: Context, loadUrl: String, action: Intent.() -> Unit = {}) {
            val intent = Intent(context, HealthyUpWebViewActivity::class.java).apply(action)
            intent.putExtra(Key.LOAD_URL, loadUrl)
            context.startActivity(intent)
        }
    }
}
