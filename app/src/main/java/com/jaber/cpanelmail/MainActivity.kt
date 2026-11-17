package com.jaber.cpanelmail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.security.SecureRandom

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme {
                    Surface(Modifier.fillMaxSize()) { App() }
                }
            }
        }
    }
}

private val ltr = TextStyle(textDirection = TextDirection.Ltr)

@Composable
fun App(vm: MainViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (vm.config == null) LoginScreen(vm) else MailScreen(vm)
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
fun LoginScreen(vm: MainViewModel) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("2083") }
    var user by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var useToken by remember { mutableStateOf(true) }
    var insecure by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ورود به cPanel", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            host, { host = it }, label = { Text("آدرس سرور (مثل example.com)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = ltr,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            port, { port = it.filter(Char::isDigit) }, label = { Text("پورت") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = ltr,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            user, { user = it.trim() }, label = { Text("نام کاربری cPanel") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = ltr,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(useToken, { useToken = it })
            Text("  ورود با API Token (پیشنهادی)")
        }
        OutlinedTextField(
            secret, { secret = it.trim() },
            label = { Text(if (useToken) "API Token" else "رمز عبور") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = ltr,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(insecure, { insecure = it })
            Text("  نادیده گرفتن خطای SSL (گواهی self-signed)")
        }
        androidx.compose.material3.Button(
            onClick = {
                val h = host.trim().removePrefix("https://").removePrefix("http://")
                    .substringBefore("/").substringBefore(":")
                vm.login(Config(h, port.toIntOrNull() ?: 2083, user, secret, useToken, insecure))
            },
            enabled = !vm.loading && host.isNotBlank() && user.isNotBlank() && secret.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("ورود") }
        Text(
            "API Token را در cPanel از بخش Security ← Manage API Tokens بسازید.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailScreen(vm: MainViewModel) {
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var pwTarget by remember { mutableStateOf<Mailbox?>(null) }
    var quotaTarget by remember { mutableStateOf<Mailbox?>(null) }
    var delTarget by remember { mutableStateOf<Mailbox?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.config?.host ?: "") },
                actions = {
                    TextButton(onClick = { vm.refresh() }) { Text("بروزرسانی") }
                    TextButton(onClick = { vm.logout() }) { Text("خروج") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "افزودن") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 12.dp)) {
            OutlinedTextField(
                query, { query = it }, label = { Text("جستجو") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = ltr,
            )
            val shown = vm.mailboxes.filter { it.email.contains(query.trim(), true) }
            Text("${shown.size} حساب", Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.email }) { m ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(m.email, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr))
                            val quota = if (m.unlimited) "نامحدود" else "${m.quotaMb} MB"
                            Text("مصرف: ${m.usedMb} MB از $quota", style = MaterialTheme.typography.bodySmall)
                            Row {
                                TextButton(onClick = { pwTarget = m }) { Text("تغییر رمز") }
                                TextButton(onClick = { quotaTarget = m }) { Text("تغییر حجم") }
                                TextButton(onClick = { delTarget = m }) { Text("حذف") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) AddDialog(vm.domains, { showAdd = false }) { u, d, p, q ->
        showAdd = false
        vm.add(u, d, p, q)
    }
    pwTarget?.let { m ->
        var pw by remember(m) { mutableStateOf("") }
        FormDialog("تغییر رمز ${m.email}", "ذخیره", { pwTarget = null }, {
            if (pw.length >= 8) { vm.changePassword(m, pw); pwTarget = null }
        }) {
            PasswordField(pw) { pw = it }
        }
    }
    quotaTarget?.let { m ->
        var q by remember(m) { mutableStateOf(if (m.unlimited) "0" else m.quotaMb.substringBefore(".")) }
        FormDialog("حجم ${m.email}", "ذخیره", { quotaTarget = null }, {
            vm.changeQuota(m, q.toIntOrNull() ?: 0); quotaTarget = null
        }) {
            OutlinedTextField(
                q, { q = it.filter(Char::isDigit) }, label = { Text("حجم (MB) — 0 = نامحدود") },
                singleLine = true, textStyle = ltr, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
    delTarget?.let { m ->
        AlertDialog(
            onDismissRequest = { delTarget = null },
            title = { Text("حذف حساب") },
            text = { Text("حساب ${m.email} و همه ایمیل‌هایش برای همیشه حذف می‌شود. مطمئنید؟") },
            confirmButton = { TextButton(onClick = { vm.delete(m); delTarget = null }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { delTarget = null }) { Text("انصراف") } },
        )
    }
}

@Composable
fun PasswordField(value: String, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, onChange, label = { Text("رمز عبور (حداقل ۸ کاراکتر)") }, singleLine = true, textStyle = ltr,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "پنهان" else "نمایش") } },
    )
}

@Composable
fun AddDialog(
    domains: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Int) -> Unit,
) {
    var user by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf(domains.firstOrNull() ?: "") }
    var pw by remember { mutableStateOf("") }
    var quota by remember { mutableStateOf("1024") }
    var expanded by remember { mutableStateOf(false) }

    FormDialog("ایمیل جدید", "ساخت", onDismiss, {
        if (user.isNotBlank() && domain.isNotBlank() && pw.length >= 8) {
            onConfirm(user.trim(), domain.trim(), pw, quota.toIntOrNull() ?: 0)
        }
    }) {
        OutlinedTextField(
            user, { user = it.trim() }, label = { Text("نام کاربری (قبل از @)") },
            singleLine = true, textStyle = ltr,
        )
        if (domains.isEmpty()) {
            OutlinedTextField(
                domain, { domain = it.trim() }, label = { Text("دامنه") },
                singleLine = true, textStyle = ltr,
            )
        } else {
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text("@" + domain.ifBlank { "انتخاب دامنه" }) }
                DropdownMenu(expanded, { expanded = false }) {
                    domains.forEach {
                        DropdownMenuItem(text = { Text(it) }, onClick = { domain = it; expanded = false })
                    }
                }
            }
        }
        PasswordField(pw) { pw = it }
        TextButton(onClick = { pw = randomPassword() }) { Text("ساخت رمز تصادفی") }
        OutlinedTextField(
            quota, { quota = it.filter(Char::isDigit) }, label = { Text("حجم (MB) — 0 = نامحدود") },
            singleLine = true, textStyle = ltr, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
fun FormDialog(
    title: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private fun randomPassword(length: Int = 16): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#%^*-_"
    val rnd = SecureRandom()
    return (1..length).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
}
