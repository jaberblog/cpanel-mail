package com.jaber.cpanelmail

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store: ConfigStore? = try { ConfigStore(app) } catch (e: Exception) { null }

    var config by mutableStateOf<Config?>(store?.load())
        private set
    var mailboxes by mutableStateOf<List<Mailbox>>(emptyList())
        private set
    var domains by mutableStateOf<List<String>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    private var api: CpanelApi? = config?.let { CpanelApi(it) }

    init {
        if (api != null) refresh()
    }

    private fun launchOp(success: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            loading = true
            try {
                block()
                if (success != null) message = success
            } catch (e: Exception) {
                message = e.message ?: "خطای ناشناخته"
            } finally {
                loading = false
            }
        }
    }

    private suspend fun reload() {
        val a = api ?: return
        mailboxes = a.listMailboxes()
        domains = runCatching { a.listDomains() }.getOrDefault(domains)
    }

    fun login(cfg: Config) = launchOp {
        val a = CpanelApi(cfg)
        val list = a.listMailboxes()
        api = a
        store?.save(cfg)
        config = cfg
        mailboxes = list
        domains = runCatching { a.listDomains() }.getOrDefault(emptyList())
    }

    fun logout() {
        store?.clear()
        api = null
        config = null
        mailboxes = emptyList()
        domains = emptyList()
    }

    fun refresh() = launchOp { reload() }

    fun add(user: String, domain: String, password: String, quotaMb: Int) =
        launchOp("حساب ایمیل ساخته شد") {
            api!!.addMailbox(user, domain, password, quotaMb)
            reload()
        }

    fun changePassword(m: Mailbox, password: String) =
        launchOp("رمز عوض شد") { api!!.changePassword(m, password) }

    fun changeQuota(m: Mailbox, quotaMb: Int) =
        launchOp("حجم تغییر کرد") {
            api!!.changeQuota(m, quotaMb)
            reload()
        }

    fun delete(m: Mailbox) =
        launchOp("حساب حذف شد") {
            api!!.deleteMailbox(m)
            reload()
        }
}
