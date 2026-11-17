package com.jaber.cpanelmail

import android.annotation.SuppressLint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

data class Config(
    val host: String,
    val port: Int,
    val user: String,
    val secret: String,
    val useToken: Boolean,
    val insecureSsl: Boolean,
)

data class Mailbox(
    val email: String,
    val user: String,
    val domain: String,
    val usedMb: String,
    val quotaMb: String,
) {
    val unlimited: Boolean
        get() = quotaMb.isBlank() || quotaMb == "0" || quotaMb.equals("unlimited", true)
}

class ApiException(message: String) : Exception(message)

class CpanelApi(private val cfg: Config) {

    private val client: OkHttpClient = buildClient(cfg.insecureSsl)

    private val authHeader: String =
        if (cfg.useToken) "cpanel ${cfg.user}:${cfg.secret}"
        else Credentials.basic(cfg.user, cfg.secret)

    private val base: String = "https://${cfg.host}:${cfg.port}/execute"

    private suspend fun call(
        module: String,
        function: String,
        params: Map<String, String> = emptyMap(),
    ): JSONObject = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().apply { params.forEach { (k, v) -> add(k, v) } }.build()
        val request = Request.Builder()
            .url("$base/$module/$function")
            .header("Authorization", authHeader)
            .post(body)
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.code == 401 || resp.code == 403) {
                    throw ApiException("احراز هویت ناموفق بود (نام کاربری/رمز/توکن را بررسی کنید)")
                }
                val json = try {
                    JSONObject(text)
                } catch (e: Exception) {
                    throw ApiException("پاسخ نامعتبر از سرور (HTTP ${resp.code})")
                }
                if (json.optInt("status", 0) != 1) {
                    val errs = json.optJSONArray("errors")
                    val msg = buildString {
                        if (errs != null) for (i in 0 until errs.length()) {
                            if (i > 0) append("\n")
                            append(errs.optString(i))
                        }
                    }.ifBlank { "خطای نامشخص از cPanel" }
                    throw ApiException(msg)
                }
                json
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: javax.net.ssl.SSLException) {
            throw ApiException("خطای SSL. اگر گواهی سرور self-signed است گزینه «نادیده گرفتن خطای SSL» را روشن کنید.")
        } catch (e: java.net.UnknownHostException) {
            throw ApiException("آدرس سرور پیدا نشد")
        } catch (e: java.io.IOException) {
            throw ApiException("خطای اتصال: ${e.message}")
        }
    }

    suspend fun listMailboxes(): List<Mailbox> {
        val data = call("Email", "list_pops").optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val o = data.optJSONObject(i) ?: return@mapNotNull null
            val email = o.optString("email")
            if (email.isBlank()) return@mapNotNull null
            Mailbox(
                email = email,
                user = o.optString("user", email.substringBefore("@")),
                domain = o.optString("domain", email.substringAfter("@", "")),
                usedMb = o.optString("diskused", "0"),
                quotaMb = o.optString("diskquota", "0"),
            )
        }
    }

    suspend fun listDomains(): List<String> {
        val data = call("Email", "list_mail_domains").optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { data.optJSONObject(it)?.optString("domain") }
            .filter { it.isNotBlank() }
    }

    suspend fun addMailbox(user: String, domain: String, password: String, quotaMb: Int) {
        call(
            "Email", "add_pop",
            mapOf(
                "email" to user,
                "domain" to domain,
                "password" to password,
                "quota" to quotaMb.toString(),
            ),
        )
    }

    suspend fun changePassword(m: Mailbox, password: String) {
        call("Email", "passwd_pop", mapOf("email" to m.user, "domain" to m.domain, "password" to password))
    }

    suspend fun changeQuota(m: Mailbox, quotaMb: Int) {
        call("Email", "edit_pop_quota", mapOf("email" to m.user, "domain" to m.domain, "quota" to quotaMb.toString()))
    }

    suspend fun deleteMailbox(m: Mailbox) {
        call("Email", "delete_pop", mapOf("email" to m.user, "domain" to m.domain))
    }

    companion object {
        @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
        private fun buildClient(insecure: Boolean): OkHttpClient {
            val b = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
            if (insecure) {
                val tm = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
                val ctx = SSLContext.getInstance("TLS")
                ctx.init(null, arrayOf(tm), SecureRandom())
                b.sslSocketFactory(ctx.socketFactory, tm)
                b.hostnameVerifier { _, _ -> true }
            }
            return b.build()
        }
    }
}
