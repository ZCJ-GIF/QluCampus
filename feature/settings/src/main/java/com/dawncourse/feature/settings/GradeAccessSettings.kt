// QluCampus 0.2.0, GPL-3.0.
package com.dawncourse.feature.settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dawncourse.core.domain.repository.GradeAccessRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GradeAccessViewModel @Inject constructor(private val access: GradeAccessRepository) : ViewModel() {
    val unlocked = access.unlocked
    var error by mutableStateOf<String?>(null); private set
    fun unlock(password: String, success: () -> Unit) = viewModelScope.launch {
        if (access.unlock(password)) { error = null; success() } else error = "密码不正确，请重试"
    }
    fun lock() = viewModelScope.launch { runCatching { access.lock() }.onFailure { error = it.message } }
}

@Composable
fun GradeAccessSettings(version: String, vm: GradeAccessViewModel = hiltViewModel()) {
    val unlocked by vm.unlocked.collectAsState()
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0) }
    var prompt by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    Text("齐鲁课表 $version · 安装包更新", modifier = Modifier.clickable {
        val now = android.os.SystemClock.elapsedRealtime()
        taps = if (now - lastTap > 5000) 1 else taps + 1; lastTap = now
        if (taps >= 5) { taps = 0; password = ""; prompt = true }
    }.padding(16.dp))
    if (unlocked) TextButton(onClick = { vm.lock() }) { Text("锁定并隐藏平时成绩") }
    if (prompt) AlertDialog(onDismissRequest = { password = ""; prompt = false }, title = { Text("功能解锁") }, text = {
        Column {
            OutlinedTextField(password, { password = it.filter(Char::isDigit).take(6) }, label = { Text("请输入密码") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { vm.unlock(password) { password = ""; prompt = false } }, enabled = password.length == 6) { Text("开启") } },
        dismissButton = { TextButton(onClick = { password = ""; prompt = false }) { Text("取消") } })
}
