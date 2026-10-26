package com.veryschool.client.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veryschool.client.ui.theme.*

// BUG-16,17,18 FIX: валидация полей перед отправкой
private fun validateEmail(email: String): String? {
    if (email.isBlank()) return "Email не может быть пустым"
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) return "Некорректный email"
    return null
}
private fun validatePassword(pw: String): String? {
    if (pw.isBlank()) return "Введите пароль"
    if (pw.length < 6)  return "Минимум 6 символов"
    return null
}
private fun validateUsername(u: String): String? {
    if (u.isBlank()) return "Введите имя пользователя"
    if (u.length < 3)  return "Минимум 3 символа"
    if (u.length > 30) return "Максимум 30 символов"
    if (!u.matches(Regex("[a-z0-9_.]+"))) return "Только a-z, 0-9, _ и ."
    return null
}
private fun validateDisplayName(dn: String): String? {
    if (dn.isBlank()) return "Введите отображаемое имя"
    if (dn.length > 60) return "Максимум 60 символов"
    return null
}
private fun validatePassphrase(p: String): String? {
    if (p.isBlank()) return "Введите ключевую фразу"
    return null
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AuthScreen(
    onLogin: (email: String, password: String, passphrase: String) -> Unit,
    onRegister: (email: String, password: String, username: String, displayName: String, passphrase: String) -> Unit,
    isLoading: Boolean = false
) {
    val tc = LocalTC.current
    val focusManager = LocalFocusManager.current
    var isLogin by remember { mutableStateOf(true) }
    var email       by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var username    by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var passphrase  by remember { mutableStateOf("") }
    var showPass    by remember { mutableStateOf(false) }

    // BUG-16,17,18 FIX: inline ошибки под полями (НОВАЯ ФИЧА #9)
    var errEmail       by remember { mutableStateOf<String?>(null) }
    var errPassword    by remember { mutableStateOf<String?>(null) }
    var errUsername    by remember { mutableStateOf<String?>(null) }
    var errDisplayName by remember { mutableStateOf<String?>(null) }
    var errPassphrase  by remember { mutableStateOf<String?>(null) }

    fun clearErrors() { errEmail = null; errPassword = null; errUsername = null; errDisplayName = null; errPassphrase = null }

    fun submit() {
        if (isLoading) return
        clearErrors()
        errEmail      = validateEmail(email)
        errPassword   = validatePassword(password)
        errPassphrase = validatePassphrase(passphrase)
        if (!isLogin) {
            errUsername    = validateUsername(username.lowercase().trim())
            errDisplayName = validateDisplayName(displayName.trim())
        }
        val hasErr = errEmail != null || errPassword != null || errPassphrase != null ||
            (!isLogin && (errUsername != null || errDisplayName != null))
        if (hasErr) return
        focusManager.clearFocus()
        if (isLogin) onLogin(email.trim(), password, passphrase)
        else onRegister(email.trim(), password, username.lowercase().trim(), displayName.trim(), passphrase)
    }

    val fc = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = VSPrimary, unfocusedBorderColor = tc.border,
        focusedTextColor = tc.on, unfocusedTextColor = tc.on,
        cursorColor = VSPrimary, focusedLabelColor = VSPrimary, unfocusedLabelColor = tc.muted,
        errorBorderColor = VSRed, errorLabelColor = VSRed, errorCursorColor = VSRed,
        errorLeadingIconColor = VSRed
    )

    // НОВАЯ ФИЧА #10: helper для TextField с кнопкой очистки
    @Composable
    fun ClearableField(
        value: String, onValueChange: (String) -> Unit, label: String,
        error: String?, icon: @Composable () -> Unit,
        keyboardType: KeyboardType = KeyboardType.Text,
        imeAction: ImeAction = ImeAction.Next,
        visualTransformation: VisualTransformation = VisualTransformation.None,
        trailingIcon: @Composable (() -> Unit)? = null
    ) {
        val trail: @Composable () -> Unit = trailingIcon ?: {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Clear, null, tint = tc.muted, modifier = Modifier.size(18.dp))
                }
            }
        }
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            label = { Text(label) }, colors = fc,
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(12.dp),
            isError = error != null,
            supportingText = if (error != null) ({ Text(error, color = VSRed, fontSize = 11.sp) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onDone = { submit() }
            ),
            leadingIcon = icon,
            trailingIcon = trail,
            visualTransformation = visualTransformation,
            enabled = !isLoading
        )
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tc.bg, Color(0xFF130D2A))))) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp))

            // Логотип
            Text("VS", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = VSPrimary)
            Text("VerySchool", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = tc.on)
            Text("Защищённый мессенджер", fontSize = 13.sp, color = tc.muted)
            Spacer(Modifier.height(40.dp))

            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = tc.surf),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // Переключатель Войти / Регистрация
                    Row(Modifier.fillMaxWidth().background(tc.card, RoundedCornerShape(12.dp)).padding(4.dp)) {
                        listOf("Войти" to true, "Регистрация" to false).forEach { (label, isL) ->
                            Box(
                                modifier = Modifier.weight(1f)
                                    .background(if (isLogin == isL) VSPrimary else Color.Transparent, RoundedCornerShape(10.dp))
                                    .padding(vertical = 10.dp)
                                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                        isLogin = isL; clearErrors()
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text(label, color = if (isLogin == isL) Color.White else tc.muted, fontWeight = FontWeight.SemiBold) }
                        }
                    }

                    // Email
                    ClearableField(
                        value = email, onValueChange = { email = it; errEmail = null },
                        label = "Email", error = errEmail,
                        icon = { Icon(Icons.Default.Email, null, tint = if (errEmail != null) VSRed else tc.muted) },
                        keyboardType = KeyboardType.Email
                    )

                    // Пароль
                    ClearableField(
                        value = password, onValueChange = { password = it; errPassword = null },
                        label = "Пароль", error = errPassword,
                        icon = { Icon(Icons.Default.Lock, null, tint = if (errPassword != null) VSRed else tc.muted) },
                        keyboardType = KeyboardType.Password,
                        imeAction = if (isLogin) ImeAction.Next else ImeAction.Next,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row {
                                if (password.isNotEmpty()) {
                                    IconButton(onClick = { showPass = !showPass }) {
                                        Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = tc.muted)
                                    }
                                }
                            }
                        }
                    )

                    // Поля только для регистрации
                    AnimatedVisibility(visible = !isLogin, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ClearableField(
                                value = username, onValueChange = { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }; errUsername = null },
                                label = "Имя пользователя (@login)", error = errUsername,
                                icon = { Icon(Icons.Default.AlternateEmail, null, tint = if (errUsername != null) VSRed else tc.muted) }
                            )
                            ClearableField(
                                value = displayName, onValueChange = { displayName = it; errDisplayName = null },
                                label = "Отображаемое имя", error = errDisplayName,
                                icon = { Icon(Icons.Default.Person, null, tint = if (errDisplayName != null) VSRed else tc.muted) }
                            )
                        }
                    }

                    // Ключевая фраза
                    ClearableField(
                        value = passphrase, onValueChange = { passphrase = it; errPassphrase = null },
                        label = "Ключевая фраза", error = errPassphrase,
                        icon = { Icon(Icons.Default.Key, null, tint = if (errPassphrase != null) VSRed else tc.muted) },
                        imeAction = ImeAction.Done,
                        visualTransformation = PasswordVisualTransformation()
                    )

                    // Кнопка
                    Button(
                        onClick = { submit() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VSPrimary),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(if (isLogin) "Войти" else "Создать аккаунт", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
