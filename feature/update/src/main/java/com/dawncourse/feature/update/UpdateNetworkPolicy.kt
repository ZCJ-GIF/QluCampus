// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
/**
 * 文件说明：统一定义更新检查的安全网络策略与可信元数据入口。
 * 自建节点已随服务端迁移改为 HTTPS，与其他节点、安装包链接一致仅允许 TLS。
 */
package com.dawncourse.feature.update

import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.ConnectionSpec

/**
 * 更新节点配置。
 *
 * @property label 节点名称，用于错误诊断展示
 * @property baseUrl Retrofit 使用的基础地址
 */
data class UpdateEndpointConfig(
    val label: String,
    val baseUrl: String,
    val versionInfoPath: String = "version.json",
    val requestTimeoutSeconds: Long = DEFAULT_UPDATE_METADATA_TIMEOUT_SECONDS
) {
    val versionInfoUrl: String
        get() = "$baseUrl$versionInfoPath"
}

/** 所有更新元数据节点均失败，并保留每次请求的结构化诊断。 */
internal class UpdateEndpointsExhaustedException(
    val failures: List<UpdateEndpointRequestException>
) : Exception("所有更新节点均不可用", failures.lastOrNull())

/**
 * 构建更新下载与元数据节点共用的 TLS 连接策略列表。
 *
 * 顺序说明：
 * 1. 优先使用现代 TLS；
 * 2. 兼容仍符合 HTTPS 要求的旧 TLS 服务端。
 */
fun buildUpdateConnectionSpecs(): List<ConnectionSpec> {
    return listOf(
        ConnectionSpec.MODERN_TLS,
        ConnectionSpec.COMPATIBLE_TLS
    )
}

/**
 * 构建更新检查的节点列表。
 *
 * 仅使用齐鲁课表配置的 HTTPS 来源；不回退到上游作者或第三方镜像。
 */
fun buildUpdateEndpointConfigs(sourceUrl: String = BuildConfig.DEFAULT_UPDATE_URL): List<UpdateEndpointConfig> {
    if (sourceUrl.isBlank()) return emptyList()
    val url = URI(requireNotNull(normalizeUpdateSourceUrl(sourceUrl)) { "请填写有效的 HTTPS 更新地址" })
    return listOf(UpdateEndpointConfig("齐鲁课表发布源", "${url.scheme}://${url.rawAuthority}/",
        url.rawPath.removePrefix("/") + (url.rawQuery?.let { "?$it" } ?: "")))
}

/** 支持直接版本文件，或 GitHub 公开仓库（默认 main 分支的 version.json）。 */
fun normalizeUpdateSourceUrl(value: String): String? = runCatching {
    val uri = URI(value.trim())
    require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null)
    require(uri.port == -1 || uri.port in 1..65535)
    val parts = uri.path.trim('/').split('/').filter(String::isNotBlank)
    if (uri.host.equals("github.com", true) && parts.size == 2 && uri.query == null) {
        "https://raw.githubusercontent.com/${parts[0]}/${parts[1].removeSuffix(".git")}/main/version.json"
    } else {
        val path = uri.rawPath.orEmpty().let { if (it.isEmpty() || it.endsWith('/')) "${it}version.json" else it }
        "https://${uri.rawAuthority}/" + path.removePrefix("/") + (uri.rawQuery?.let { "?$it" } ?: "")
    }
}.getOrNull()

/**
 * 检查重定向后的更新元数据响应仍位于配置节点的同一来源和协议。
 *
 * 路径可以由服务端调整，但协议、主机和端口必须保持一致，且预期地址必须是 HTTPS。
 */
internal fun isExpectedUpdateMetadataResponseUrl(
    expectedUrl: String,
    actualUrl: String
): Boolean = runCatching {
    val expected = URI(expectedUrl)
    val actual = URI(actualUrl)
    expected.scheme.equals("https", ignoreCase = true) &&
        actual.scheme.equals(expected.scheme, ignoreCase = true) &&
        !expected.host.isNullOrBlank() &&
        expected.userInfo == null &&
        actual.userInfo == null &&
        expected.host.equals(actual.host, ignoreCase = true) &&
        effectivePort(expected) == effectivePort(actual)
}.getOrDefault(false)

private fun effectivePort(uri: URI): Int {
    if (uri.port != -1) return uri.port
    return if (uri.scheme.equals("https", ignoreCase = true)) HTTPS_DEFAULT_PORT else HTTP_DEFAULT_PORT
}

/**
 * 按配置顺序请求更新元数据，单节点失败不会终止整个检查。
 *
 * 请求实现负责校验响应内容；本函数只处理节点切换和诊断聚合。
 */
internal suspend fun resolveUpdateInfoFromEndpoints(
    endpoints: List<UpdateEndpointConfig>,
    request: suspend (UpdateEndpointConfig) -> UpdateInfo
): UpdateInfo {
    require(endpoints.isNotEmpty()) { "至少需要一个更新节点" }
    val failures = mutableListOf<UpdateEndpointRequestException>()
    endpoints.forEach { endpoint ->
        currentCoroutineContext().ensureActive()
        try {
            return request(endpoint)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: UpdateEndpointRequestException) {
            failures += failure
        } catch (failure: Exception) {
            failures += UpdateEndpointRequestException(
                endpointLabel = endpoint.label,
                endpointUrl = endpoint.versionInfoUrl,
                stage = "request",
                detail = failure.message ?: "unknown_error",
                cause = failure
            )
        }
    }
    throw UpdateEndpointsExhaustedException(failures.toList())
}

/**
 * 判断远端元数据提供的下载链接能否安全交给系统浏览器。
 *
 * 不对输入做补全或修正：任何非 HTTPS、缺少主机名或包含 userInfo 的链接都直接拒绝，
 * 以免攻击者借元数据将 Intent 导向本地文件、ContentProvider 或自定义 Scheme。
 */
fun isValidUpdateDownloadUrl(downloadUrl: String): Boolean = runCatching {
    val uri = URI(downloadUrl)
    uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrBlank() &&
        uri.userInfo == null
}.getOrDefault(false)

/** 应用内安装必须有完整的 SHA-256，不能把完整性校验降级为可选。 */
fun isValidUpdateSha256(sha256: String?): Boolean {
    return sha256?.trim()?.matches(Regex("^[0-9a-fA-F]{64}$")) == true
}

/** 只有下载链接和完整性校验值都可信的元数据才可继续向 UI 传播。 */
fun validateUpdateInfo(updateInfo: UpdateInfo): UpdateInfo? {
    return runCatching { updateInfo.takeIf {
        it.applicationId == "com.qlucampus.app" && it.versionCode > 0 && !it.versionName.isNullOrBlank() &&
            isValidUpdateDownloadUrl(it.downloadUrl) && isValidUpdateSha256(it.sha256)
    }?.copy(type = updateInfo.type ?: UpdateType.STANDARD, isForce = false) }.getOrNull()
}

private const val DEFAULT_UPDATE_METADATA_TIMEOUT_SECONDS = 15L
private const val HTTP_DEFAULT_PORT = 80
private const val HTTPS_DEFAULT_PORT = 443
