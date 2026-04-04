package com.veryschool.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veryschool.client.ui.theme.*

@Composable
fun AuthScreen(
    onLogin: (String, String, String) -> Unit,
    onRegister: (String, String, String, String) -> Unit
) {
    var isLogin by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(VSBackground, VSSurfaceVariant))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("VerySchool", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = VSPrimary)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .background(VSSurface, RoundedCornerShape(16.dp))
                    .padding(4.dp)
            ) {
                listOf("Войти" to true, "Регистрация" to false).forEach { (label, isL) ->
                    Button(
                        onClick = { isLogin = isL; error = "" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLogin == isL) VSPrimary else VSSurface,
                            contentColor = if (isLogin == isL) MaterialTheme.colorScheme.onPrimary else VSOnSurfaceMuted
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) { Text(label, fontWeight = FontWeight.SemiBold) }
                }
            }

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = VSSurface),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(Modifier.padding(24.dp)) {
                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VSPrimary, unfocusedBorderColor = VSBorder,
                        focusedTextColor = VSOnSurface, unfocusedTextColor = VSOnSurface, cursorColor = VSPrimary
                    )
                    val fieldShape = RoundedCornerShape(12.dp)

                    if (!isLogin) {
                        OutlinedTextField(
                            value = displayName, onValueChange = { displayName = it; error = "" },
                            label = { Text("Имя для отображения", color = VSOnSurfaceMuted) },
                            modifier = Modifier.fillMaxWidth(), colors = fieldColors, singleLine = true, shape = fieldShape
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }; error = "" },
                        label = { Text("Имя пользователя", color = VSOnSurfaceMuted) },
                        placeholder = { Text("только буквы, цифры, _", color = VSOnSurfaceMuted) },
                        modifier = Modifier.fillMaxWidth(), colors = fieldColors, singleLine = true, shape = fieldShape
                    )
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = password, onValueChange = { password = it; error = "" },
                        label = { Text("Пароль", color = VSOnSurfaceMuted) },
                        modifier = Modifier.fillMaxWidth(), colors = fieldColors, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), shape = fieldShape
                    )
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = passphrase, onValueChange = { passphrase = it; error = "" },
                        label = { Text("Ключевая фраза", color = VSOnSurfaceMuted) },
                        modifier = Modifier.fillMaxWidth(), colors = fieldColors, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), shape = fieldShape
                    )

                    if (error.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = VSRed, fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (username.isBlank() || password.isBlank()) { error = "Заполните все поля"; return@Button }
                            if (isLogin) onLogin(username, password, passphrase)
                            else {
                                if (displayName.isBlank()) { error = "Введите имя для отображения"; return@Button }
                                onRegister(username, password, displayName, passphrase)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VSPrimary)
                    ) {
                        Text(if (isLogin) "Войти" else "Создать аккаунт", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
