package app.firstvpn

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.util.Base64
//import android.util.Base64
import android.view.View
import androidx.preference.PreferenceDataStore
import app.firstvpn.R
//import com.github.oezeb.cypher_connect.R
import com.github.oezeb.cypher_connect.design.Http
import com.github.oezeb.cypher_connect.design.MainDesign
import com.github.shadowsocks.Core
import com.github.shadowsocks.aidl.IShadowsocksService
import com.github.shadowsocks.aidl.ShadowsocksConnection
import com.github.shadowsocks.aidl.TrafficStats
import com.github.shadowsocks.bg.BaseService.State
import com.github.shadowsocks.database.Profile
import com.github.shadowsocks.database.ProfileManager
import com.github.shadowsocks.preference.DataStore
import com.github.shadowsocks.preference.OnPreferenceDataStoreChangeListener
import com.github.shadowsocks.utils.Key
import timber.log.Timber
import kotlin.concurrent.thread
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.net.URL
import java.net.URLDecoder

class MainActivity : MainDesign(), ShadowsocksConnection.Callback,
    OnPreferenceDataStoreChangeListener {
    companion object {
        var stateListener: ((state: State, profileName: String?) -> Unit)? = null
        const val MAX_CONCURRENT_TEST = 10
    }

    private fun getBestProfile(): Profile? {
        val profiles = ProfileManager.getActiveProfiles() ?: emptyList()

        val groups = profiles.chunked(MAX_CONCURRENT_TEST)
        var best = Pair<Profile?, Int>(null, Int.MAX_VALUE)
        for (group in groups) {
            val delayArray = testProfiles(group)
            val minDelayIndex = delayArray.indexOfFirst { it == delayArray.minOrNull() }
            if (best.second > delayArray[minDelayIndex]) {
                best = group[minDelayIndex] to delayArray[minDelayIndex]
            }
        }
        return best.first
    }

    private val connection = ShadowsocksConnection(true)
    private var currentProfileId = -1L
    private var state = State.Idle
    private val syncProfilesThread = thread(false) { syncProfiles() }

    private var bestProfile: Profile? = null
    private var bestProfileThread = thread(false) { bestProfile = getBestProfile() }

    private val handler = Handler(Looper.getMainLooper())

    override val launchLocationListActivityIntent: Intent
        get() = Intent(this, LocationListActivity::class.java)

    override fun getCurrentIp(): String {
        for (url in resources.getStringArray(R.array.current_ip_providers)) {
            try {
                val res = Http.get(url, timeout = 1000)
                if (res.ok) {
                    val text = res.text
                    if (text.isNotEmpty()) return text
                } else {
                    Timber.e("GET $url - code ${res.code} - data: ${res.text}")
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
        return "Error"
    }

    override fun onClickConnectButton(v: View) {
        if (state == State.Connected) {
            if (state.canStop) Core.stopService()
        } else if (state == State.Stopped || state == State.Idle) {
            if (currentProfileId < 0) {
                thread {
                    handler.post { showLoadingProgressBar() }
                    if (bestProfileThread.isAlive) bestProfileThread.join()
                    bestProfile?.let {
                        Core.switchProfile(it.id)
                    }
                    handler.post { hideLoadingProgressBar() }
                }
            }
            Core.startService()
        }
    }

    override fun onProfileChanged(id: Long) {
        if (id < 0) {
            thread {
                handler.post { showLoadingProgressBar() }
                if (bestProfileThread.isAlive) bestProfileThread.join()
                bestProfile?.let {
                    Core.switchProfile(it.id)
                    if (state == State.Connected) Core.reloadService()
                }

                bestProfileThread = thread { bestProfile = getBestProfile() }
                handler.post { hideLoadingProgressBar() }
            }
        } else if (currentProfileId != id) {
            Core.switchProfile(id)
            if (state == State.Connected) Core.reloadService()
        }

        if (state == State.Connected) {
            updateLastConnectedProfileId(this, id)
        }

        currentProfileId = id
    }

    override fun launchLocationListActivity() {
        showLoadingProgressBar()
        thread {
            if (syncProfilesThread.isAlive) syncProfilesThread.join()
            handler.post {
                hideLoadingProgressBar()
                super.launchLocationListActivity()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Me
        val appConfState = loadAppConfState(this)
        currentProfileId = appConfState?.lastConnectedProfileId ?: -1

        syncProfilesThread.start()
        thread {
            syncProfilesThread.join()
            bestProfileThread.start()
        }

        try {
            connection.connect(this, this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        DataStore.publicStore.registerChangeListener(this)

        stateListener = {state, _ ->
            handler.post {
                when (state) {
                    State.Connecting -> showConnectingStatePage()
                    State.Connected -> showConnectedStatePage()
                    State.Stopping -> showStoppingStatePage()
                    else -> showNotConnectedStatePage()
                }
            }
        }
    }

    private fun syncProfiles() {
        val jsonRes = fetchJsonFromUrl("https://artifacts5.s3.ir-thr-at1.arvanstorage.ir/servers-raw.json")
        if (jsonRes != null) {
            KKApp.saveToSharedPreferences(this, "sslistme-s3", jsonRes)
        } else {
            return
           // jsonRes = KKApp.getFromSharedPreferences(this,"sslistme-s3")
        }
        val response = try {
            Gson().fromJson(jsonRes, ListResponse::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        if (response == null) {
            return
        }

        response?.let { resp ->
            try {
                ProfileManager.getAllProfiles()?.forEach { ProfileManager.delProfile(it.id) }
                resp.servers.map {
                    var rr = fromShadowsocksUrl(it)
                    if(rr != null) {
                        ProfileManager.createProfile(rr)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    private fun syncProfiles_old() {
        KKApp.saveToSharedPreferences(this,"sslistme", ssListMe);
        for (url in resources.getStringArray(R.array.proxies_url)) {
            try {
//                val res = Http.get(url, timeout = 1000)
//                val text = if (res.ok) res.text else ""
                val text = KKApp.getFromSharedPreferences(this,"sslistme");
                ProfileManager.getAllProfiles()?.forEach { ProfileManager.delProfile(it.id) }
                Profile.findAllUrls(text).forEach { ProfileManager.createProfile(it) }
                break
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    private fun changeState(state: State, profileName: String? = null) {
        this.state = state
        stateListener?.invoke(state, profileName)
    }

    override fun stateChanged(state: State, profileName: String?, msg: String?) =
        changeState(if (msg == null) state else State.Idle, profileName)

    override fun onServiceConnected(service: IShadowsocksService) = changeState(try {
        State.values()[service.state]
    } catch (_: RemoteException) {
        State.Idle
    })

    override fun trafficUpdated(profileId: Long, stats: TrafficStats) {
        if (state != State.Stopping) {
            if (profileId != 0L) {
                updateTraffic(stats.txRate, stats.rxRate)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        connection.bandwidthTimeout = 500
    }

    override fun onStop() {
        connection.bandwidthTimeout = 0
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        DataStore.publicStore.unregisterChangeListener(this)
        connection.disconnect(this)
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        when (key) {
            Key.serviceMode -> {
                connection.disconnect(this)
                connection.connect(this, this)
            }
        }
    }

    override fun onBinderDied() {
        connection.disconnect(this)
        connection.connect(this, this)
    }


}

val ssList = """ss://YWVzLTI1Ni1nY206UENubkg2U1FTbmZvUzI3@38.121.43.71:8091#US-Texas
ss://YWVzLTI1Ni1nY206cEtFVzhKUEJ5VFZUTHRN@38.121.43.71:443#US-Texas%202
ss://YWVzLTI1Ni1nY206a0RXdlhZWm9UQmNHa0M0@38.121.43.71:8882#US-Texas%203
ss://YWVzLTI1Ni1nY206WTZSOXBBdHZ4eHptR0M@38.121.43.71:8888#US-Texas%204
ss://YWVzLTI1Ni1nY206WTZSOXBBdHZ4eHptR0M@38.121.43.71:5001#US-Texas%205
ss://YWVzLTI1Ni1nY206VEV6amZBWXEySWp0dW9T@38.121.43.71:6697#US-Texas%206
ss://YWVzLTI1Ni1nY206WTZSOXBBdHZ4eHptR0M@38.121.43.71:5000#US-Texas%207
ss://YWVzLTI1Ni1jZmI6YXNkS2thc2tKS2Zuc2E@162.19.59.162:443#FR-Hauts-de-France
ss://YWVzLTI1Ni1nY206WTZSOXBBdHZ4eHptR0M@38.121.43.71:3306#US-Texas%208
ss://YWVzLTI1Ni1nY206UENubkg2U1FTbmZvUzI3@142.202.48.45:8091#US-New%20York
ss://YWVzLTI1Ni1nY206S2l4THZLendqZWtHMDBybQ@142.202.48.45:5500#US-New%20York%202
ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTp0T3dPeXZsWGlZNUFUSkFVT3BYTlBO@5.35.34.107:55990#NL-North%20Holland
ss://YWVzLTI1Ni1nY206WEtGS2wyclVMaklwNzQ@54.36.174.181:8008#PL-Lower%20Silesia
ss://YWVzLTI1Ni1jZmI6ZG91Yi5pbw@54.199.83.239:2333#JP-Tokyo
ss://YWVzLTI1Ni1nY206WTZSOXBBdHZ4eHptR0M@ak1751.www.outline.network.fr8678825324247b8176d59f83c30bd94d23d2e3ac5cd4a743bkwqeikvdyufr.cyou:5600#PL-Lower%20Silesia%202
ss://YWVzLTI1Ni1nY206ZmFCQW9ENTRrODdVSkc3@38.68.134.69:2376#US-Texas%209
ss://YWVzLTI1Ni1jZmI6YXNkS2thc2tKS2Zuc2E@162.19.59.165:443#FR-Hauts-de-France%202
ss://YWVzLTI1Ni1jZmI6YXNkS2thc2tKS2Zuc2E@37.59.21.132:443#FR-Hauts-de-France%203
ss://YWVzLTI1Ni1jZmI6YXNkS2thc2tKS2Zuc2E@51.158.200.160:443#FR-%C3%8Ele-de-France
ss://YWVzLTI1Ni1jZmI6YXNkS2thc2tKS2Zuc2E@162.19.204.83:443#DE-Hesse
ss://YWVzLTI1Ni1jZmI6YXNkS2thc2tKS2Zuc2E@162.19.204.81:443#DE-Hesse%202
ss://YWVzLTI1Ni1jZmI6YXNkS2thc2tKS2Zuc2E@51.159.222.35:443#FR-%C3%8Ele-de-France%202
ss://YWVzLTI1Ni1jZmI6YXNkS2thc2tKS2Zuc2E@51.158.202.187:443#NL-North%20Holland%202
ss://YWVzLTI1Ni1jZmI6YXNkS2thc2tKS2Zuc2E@57.129.1.20:443#DE-Hesse%203
ss://YWVzLTI1Ni1nY206WTZSOXBBdHZ4eHptR0M@54.36.174.181:5000#PL-Lower%20Silesia%203
ss://YWVzLTI1Ni1nY206WTZSOXBBdHZ4eHptR0M@54.36.174.181:3389#PL-Lower%20Silesia%204
ss://YWVzLTI1Ni1jZmI6YXNkS2thc2tKS2Zuc2E@146.59.110.240:443#PL-Mazovia
ss://YWVzLTI1Ni1nY206Rm9PaUdsa0FBOXlQRUdQ@54.36.174.181:7307#PL-Lower%20Silesia%205
ss://YWVzLTI1Ni1nY206S2l4THZLendqZWtHMDBybQ@54.36.174.181:8000#PL-Lower%20Silesia%206
ss://YWVzLTI1Ni1nY206ZTRGQ1dyZ3BramkzUVk@54.36.174.181:9102#PL-Lower%20Silesia%207
ss://YWVzLTI1Ni1nY206UmV4bkJnVTdFVjVBRHhH@54.36.174.181:7002#PL-Lower%20Silesia%208
ss://YWVzLTI1Ni1nY206ZmFCQW9ENTRrODdVSkc3@54.36.174.181:2376#PL-Lower%20Silesia%209
ss://YWVzLTI1Ni1nY206S2l4THZLendqZWtHMDBybQ@54.36.174.181:5500#PL-Lower%20Silesia%2010
ss://YWVzLTI1Ni1nY206WTZSOXBBdHZ4eHptR0M@54.36.174.181:5600#PL-Lower%20Silesia%2011
ss://YWVzLTI1Ni1nY206VEV6amZBWXEySWp0dW9T@54.36.174.181:6697#PL-Lower%20Silesia%2012
ss://YWVzLTI1Ni1nY206UENubkg2U1FTbmZvUzI3@54.36.174.181:8091#PL-Lower%20Silesia%2013
ss://YWVzLTI1Ni1nY206UENubkg2U1FTbmZvUzI3@54.36.174.181:8090#PL-Lower%20Silesia%2014
ss://YWVzLTI1Ni1nY206Y2RCSURWNDJEQ3duZklO@ak1732.www.outline.network.fr8678825324247b8176d59f83c30bd94d23d2e3ac5cd4a743bkwqeikvdyufr.cyou:8119#PL-Lower%20Silesia%2015
ss://YWVzLTI1Ni1nY206cEtFVzhKUEJ5VFZUTHRN@54.36.174.181:4444#PL-Lower%20Silesia%2016
ss://YWVzLTI1Ni1jZmI6YXNkS2thc2tKS2Zuc2E@51.158.203.4:443#FR-%C3%8Ele-de-France%203
ss://YWVzLTI1Ni1jZmI6YXNkS2thc2tKS2Zuc2E@141.95.126.100:443#DE-Hesse%204
ss://YWVzLTI1Ni1jZmI6YXNkS2thc2tKS2Zuc2E@51.158.203.2:443#FR-%C3%8Ele-de-France%204
ss://YWVzLTI1Ni1jZmI6YXNkS2thc2tKS2Zuc2E@51.158.202.188:443#NL-North%20Holland%203
ss://YWVzLTI1Ni1jZmI6YXNkS2thc2tKS2Zuc2E@51.158.200.50:443#FR-%C3%8Ele-de-France%205
ss://YWVzLTI1Ni1jZmI6YXNkS2thc2tKS2Zuc2E@51.158.204.70:443#FR-%C3%8Ele-de-France%206
ss://YWVzLTI1Ni1nY206ZVBvcFR2THMyWDNqeQ==@s1.api3.fun:443#DE-Germany%202%20+%F0%9F%87%A9%F0%9F%87%AA
ss://YWVzLTI1Ni1nY206ZVBvcFR2THMyWDNqeQ==@91.107.143.205:443#DE-Germany%201%20+%F0%9F%87%A9%F0%9F%87%AA  
""".trimIndent();

val ssListMe = """ss://YWVzLTI1Ni1nY206ZVBvcFR2THMyWDNqeQ==@s1.api3.fun:443#DE-Germany%202%20+%F0%9F%87%A9%F0%9F%87%AA
ss://YWVzLTI1Ni1nY206ZVBvcFR2THMyWDNqeQ==@91.107.143.205:443#DE-Germany%201%20+%F0%9F%87%A9%F0%9F%87%AA  
""".trimIndent();

data class AndroidConfig(
    @SerializedName("ColCp") val colCp: Boolean,
    @SerializedName("ColNf") val colNf: Boolean
)

data class ListResponse(
    @SerializedName("Servers") val servers: List<String>,
    @SerializedName("Config") val config: AndroidConfig?
)

fun fetchAndDecodeJsonFromUrl(url: String): ListResponse? {
    return try {
        val jsonText = URL(url).readText()
        Gson().fromJson(jsonText, ListResponse::class.java)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun fetchJsonFromUrl(url: String): String? {
    return try {
        val jsonText = URL(url).readText()
        jsonText
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun fromShadowsocksUrl(ssUrl: String): Profile? {
    return try {
        val base64Part = ssUrl.substringAfter("ss://").substringBefore("@")
        val decodedUserInfo = String(Base64.decode(base64Part, Base64.URL_SAFE or Base64.NO_WRAP))
        val methodAndPassword = decodedUserInfo.split(":")

        val hostAndPort = ssUrl.substringAfter("@").substringBefore("#").split(":")
        val host = hostAndPort[0]
        val port = hostAndPort[1].toInt()

        val tag = ssUrl.substringAfter("#").let { URLDecoder.decode(it, "UTF-8") }

        Profile(
            // Assign values to the Profile properties
            name = tag,
            host = host,
            remotePort = port,
            method = methodAndPassword[0],
            password = methodAndPassword[1]
            // Add other fields if necessary
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun fromShadowsocksUrl_old(ssUrl: String): Profile? {
    return try {
        val decodedUrl = String(Base64.decode(ssUrl.substringAfter("ss://"), Base64.NO_PADDING or Base64.URL_SAFE))
        val parts = decodedUrl.split("@")
        val methodAndPassword = parts[0].split(":")
        val hostAndPort = parts[1].split(":")

        Profile().apply {
            method = methodAndPassword[0]
            password = methodAndPassword[1]
            host = hostAndPort[0]
            remotePort = hostAndPort[1].toInt()
            name = ssUrl.substringAfterLast("#")
//            uri = ssUrl
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
