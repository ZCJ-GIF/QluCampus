package com.dawncourse.core.data.campus

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.dawncourse.core.domain.repository.SchoolNetworkRepository
import com.dawncourse.core.domain.repository.SchoolNetworkState
import com.dawncourse.core.domain.repository.detectSchoolNetwork
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class QluSchoolNetworkRepository @Inject constructor(@ApplicationContext context: Context) : SchoolNetworkRepository {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    // 独立匿名探测，不读取学校会话、不跟随登录跳转、不缓存学校响应。
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).callTimeout(3, TimeUnit.SECONDS).build()

    override suspend fun check(): SchoolNetworkState = detectSchoolNetwork(
        vpnConnected = {
            runCatching {
                connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }.getOrDefault(false)
        }, schoolReachable = ::canReachSchool
    )

    private suspend fun canReachSchool(): Boolean = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(QluSchoolApi.BASE).head().build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(false)
            }
            override fun onResponse(call: Call, response: Response) {
                // 即使学校返回登录跳转、拒绝 HEAD 或服务错误，也已证明此 HTTPS 主机可达。
                // 不将连通性解释为接口、登录或成绩查询成功。
                response.close()
                if (continuation.isActive) continuation.resume(true)
            }
        })
    }
}
