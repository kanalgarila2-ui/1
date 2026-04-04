package com.veryschool.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veryschool.client.ui.theme.*

@Composable
fun ConnectScreen(onConnect: (String, String) -> Unit) {
    var ip by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(VSBackground, VSSurfaceVariant))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("VerySchool", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = VSPrimary)
            Text("Мессенджер", fontSize = 14.sp, color = VSOnSurfaceMuted)
            Spacer(Modifier.height(40.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = VSSurface),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("Подключение к серверу", fontWeight = FontWeight.Bold, color = VSOnSurface, fontSize = 18.sp)
                    Spacer(Modifier.height(20.dp))

                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it; error = "" },
                        label = { Text("IP:PORT", color = VSOnSurfaceMuted) },
                        placeholder = { Text("192.168.1.1:8080", color = VSOnSurfaceMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VSPrimary, unfocusedBorderColor = VSBorder,
                            focusedTextColor = VSOnSurface, unfocusedTextColor = VSOnSurface, cursorColor = VSPrimary
                        ),
                        singleLine = true, shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it; error = "" },
                        label = { Text("Ключевая фраза", color = VSOnSurfaceMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VSPrimary, unfocusedBorderColor = VSBorder,
                            focusedTextColor = VSOnSurface, unfocusedTextColor = VSOnSurface, cursorColor = VSPrimary
                        ),
                        singleLine = true, shape = RoundedCornerShape(12.dp)
                    )

                    if (error.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = VSRed, fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (ip.isBlank()) { error = "Введите IP и порт"; return@Button }
                            if (passphrase != "22sch") { error = "Неверная ключевая фраза"; return@Button }
                            onConnect(ip.trim(), passphrase)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VSPrimary)
                    ) {
                        Text("Подключиться", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
