package org.dhis2.usescases.sync

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.content.res.AppCompatResources
import androidx.databinding.DataBindingUtil
import androidx.work.WorkInfo
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.codec.binary.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dhis2.App
import org.dhis2.R
import org.dhis2.bindings.Bindings
import org.dhis2.bindings.userComponent
import org.dhis2.databinding.ActivitySynchronizationBinding
import org.dhis2.usescases.general.ActivityGlobalAbstract
import org.dhis2.usescases.login.LoginActivity
import org.dhis2.usescases.main.MainActivity
import org.dhis2.utils.OnDialogClickListener
import org.dhis2.utils.extension.navigateTo
import org.dhis2.utils.extension.share
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

class SyncActivity : ActivityGlobalAbstract(), SyncView {

    lateinit var binding: ActivitySynchronizationBinding

    @Inject
    lateinit var presenter: SyncPresenter

    @Inject
    lateinit var animations: SyncAnimations

    override fun onCreate(savedInstanceState: Bundle?) {
        val serverComponent = (applicationContext as App).serverComponent()
        userComponent()?.plus(SyncModule(this, serverComponent))?.inject(this) ?: finish()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_synchronization)
        binding.presenter = presenter
        //getAllProgrammes()
        presenter.sync()
    }

    override fun onResume() {
        super.onResume()
        presenter.observeSyncProcess().observe(this) { workInfoList: List<WorkInfo> ->
            presenter.handleSyncInfo(workInfoList)
        }
    }

    override fun setMetadataSyncStarted() {
        Bindings.setDrawableEnd(
            binding.metadataText,
            AppCompatResources.getDrawable(
                this,
                R.drawable.animator_sync,
            ),
        )
    }

    override fun setMetadataSyncSucceed() {
        binding.metadataText.text = getString(R.string.configuration_ready)
        Bindings.setDrawableEnd(
            binding.metadataText,
            AppCompatResources.getDrawable(
                this,
                R.drawable.animator_done,
            ),
        )
        presenter.onMetadataSyncSuccess()
    }

    override fun showMetadataFailedMessage(message: String?) {
        showInfoDialog(
            getString(R.string.something_wrong),
            getString(R.string.metada_first_sync_error),
            getString(R.string.share),
            getString(R.string.go_back),
            object : OnDialogClickListener {
                override fun onPositiveClick() {
                    message?.let { share(it) }
                }

                override fun onNegativeClick() {
                    presenter.onLogout()
                }
            },
        )
    }

    override fun onStart() {
        super.onStart()
        animations.startLottieAnimation(binding.lottieView)
    }

    override fun onStop() {
        binding.lottieView.cancelAnimation()
        presenter.onDetach()
        super.onStop()
    }

    override fun setServerTheme(themeId: Int) {
        animations.startThemeAnimation(this, { super.setTheme(themeId) }) { colorValue ->
            binding.logo.setBackgroundColor(colorValue)
        }
    }

    override fun setFlag(flagName: String?) {
        binding.logoFlag.setImageResource(
            resources.getIdentifier(flagName, "drawable", packageName),
        )
        animations.startFlagAnimation { value: Float? ->
            binding.apply {
                logoFlag.alpha = value!!
                dhisLogo.alpha = 0f
            }
        }
    }

    override fun goToMain() {
        startActivity(
            MainActivity.intent(this, launchDataSync = true).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
        )
        finish()
    }

    override fun goToLogin() {
        navigateTo<LoginActivity>(true, flagsToApply = Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun getAllProgrammes(){
            val username = "admin"
            val password = "district"
            val auth = "Basic " + android.util.Base64.encodeToString("$username:$password".toByteArray(), android.util.Base64.NO_WRAP)

            CoroutineScope(Dispatchers.IO).launch {
                val urlString = "https://play.im.dhis2.org/stable-2-38-6/api/programs"
                val url = URL(urlString)
                var urlConnection: HttpURLConnection? = null

                try {
                    urlConnection = url.openConnection() as HttpURLConnection
                    urlConnection.requestMethod = "GET"
                    urlConnection.setRequestProperty("Authorization", auth)
                    urlConnection.connect()

                    val responseCode = urlConnection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val inputStream = urlConnection.inputStream
                        val response = inputStream.bufferedReader().use { it.readText() }

                        // Switch to Main thread to log the data
                        withContext(Dispatchers.Main) {
                            Timber.d("All Programs JSON Response: %s", response)
                        }
                    } else {
                        Timber.e("Error fetching data: HTTP $responseCode")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Network failure")
                } finally {
                    urlConnection?.disconnect()
                }
            }
        }


}
