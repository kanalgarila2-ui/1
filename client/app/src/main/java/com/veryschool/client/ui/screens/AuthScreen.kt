package com.veryschool.client.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veryschool.client.ui.theme.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AuthScreen(
    onLogin: (email: String, password: String, passphrase: String) -> Unit,
    onRegister: (email: String, password: String, username: String, displayName: String, passphrase: String) -> Unit
) {
    val tc = LocalTC.current
    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }

    val fc = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = VSPrimary, unfocusedBorderColor = tc.border,
        focusedTextColor = tc.on, unfocusedTextColor = tc.on,
        cursorColor = VSPrimary, focusedLabelColor = VSPrimary, unfocusedLabelColor = tc.muted
    )

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tc.bg, Color(0xFF130D2A))))) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp))
            Text("VS", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = VSPrimary)
            Text("VerySchool", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = tc.on)
            Text("Защищённый мессенджер", fontSize = 13.sp, color = tc.muted)
            Spacer(Modifier.height(40.dp))

            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = tc.surf), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Tab
                    Row(Modifier.fillMaxWidth().background(tc.card, RoundedCornerShape(12.dp)).padding(4.dp)) {
                        listOf("Войти" to true, "Регистрация" to false).forEach { (label, isL) ->
                            Box(
                                Modifier.weight(1f).background(if (isLogin == isL) VSPrimary else Color.Transparent, RoundedCornerShape(10.dp))
                                    .padding(vertical = 10.dp)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { isLogin = isL },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = if (isLogin == isL) Color.White else tc.muted, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") },
                        colors = fc, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        leadingIcon = { Icon(Icons.Default.Email, null, tint = tc.muted) })

                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Пароль") },
                        colors = fc, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = tc.muted) },
                        trailingIcon = { IconButton(onClick = { showPass = !showPass }) { Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = tc.muted) } })

                    AnimatedVisibility(visible = !isLogin) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Имя пользователя") },
                                colors = fc, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.AlternateEmail, null, tint = tc.muted) })
                            OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Отображаемое имя") },
                                colors = fc, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Person, null, tint = tc.muted) })
                        }
                    }

                    OutlinedTextField(value = passphrase, onValueChange = { passphrase = it }, label = { Text("Ключевая фраза") },
                        colors = fc, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Key, null, tint = tc.muted) },
                        visualTransformation = PasswordVisualTransformation())

                    Button(
                        onClick = {
                            if (isLogin) onLogin(email, password, passphrase)
                            else onRegister(email, password, username, displayName, passphrase)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VSPrimary)
                    ) { Text(if (isLogin) "Войти" else "Создать аккаунт", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}