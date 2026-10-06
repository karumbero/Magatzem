package com.example.magatzem.ui.ajustes

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.ui.common.LabeledTextField

/** Datos de la propia empresa (nombre, NIF/CIF, dirección…), para futuros documentos e informes. */
@Composable
fun DatosEmpresaScreen(viewModel: DatosEmpresaViewModel = viewModel(), onGuardado: () -> Unit = {}) {
    val datosGuardados by viewModel.datos.collectAsState()
    var nombre by rememberSaveable { mutableStateOf("") }
    var nombreComercial by rememberSaveable { mutableStateOf("") }
    var nifCif by rememberSaveable { mutableStateOf("") }
    var direccion by rememberSaveable { mutableStateOf("") }
    var codigoPostal by rememberSaveable { mutableStateOf("") }
    var ciudad by rememberSaveable { mutableStateOf("") }
    var telefono by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var guardado by remember { mutableStateOf(false) }

    LaunchedEffect(datosGuardados) {
        datosGuardados?.let {
            nombre = it.nombre
            nombreComercial = it.nombreComercial.orEmpty()
            nifCif = it.nifCif.orEmpty()
            direccion = it.direccion.orEmpty()
            codigoPostal = it.codigoPostal.orEmpty()
            ciudad = it.ciudad.orEmpty()
            telefono = it.telefono.orEmpty()
            email = it.email.orEmpty()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 4.dp).widthIn(max = 480.dp)) {
            Text(text = "Datos de empresa", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(6.dp))
            LabeledTextField(
                compacto = true,
                bajo = true,
                singleLine = true,
                label = "Nombre / razón social",
                value = nombre,
                onValueChange = { nombre = it; guardado = false },
                isError = viewModel.error != null,
                supportingText = viewModel.error?.let { e -> { Text(e) } },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(2.dp))
            LabeledTextField(
                compacto = true,
                bajo = true,
                singleLine = true,
                label = "Nombre comercial",
                value = nombreComercial,
                onValueChange = { nombreComercial = it; guardado = false },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row {
                LabeledTextField(
                compacto = true,
                bajo = true,
                singleLine = true,
                    label = "NIF/CIF",
                    value = nifCif,
                    onValueChange = { nifCif = it; guardado = false },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                LabeledTextField(
                compacto = true,
                bajo = true,
                singleLine = true,
                    label = "Teléfono",
                    value = telefono,
                    onValueChange = { telefono = it; guardado = false },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            LabeledTextField(
                compacto = true,
                bajo = true,
                singleLine = true,
                label = "Email",
                value = email,
                onValueChange = { email = it; guardado = false },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(2.dp))
            LabeledTextField(
                compacto = true,
                bajo = true,
                singleLine = true,
                label = "Dirección",
                value = direccion,
                onValueChange = { direccion = it; guardado = false },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row {
                LabeledTextField(
                compacto = true,
                bajo = true,
                singleLine = true,
                    label = "C. Postal",
                    value = codigoPostal,
                    onValueChange = { codigoPostal = it; guardado = false },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                LabeledTextField(
                compacto = true,
                bajo = true,
                singleLine = true,
                    label = "Ciudad",
                    value = ciudad,
                    onValueChange = { ciudad = it; guardado = false },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row {
                Button(onClick = {
                    viewModel.guardar(nombre, nombreComercial, nifCif, direccion, codigoPostal, ciudad, telefono, email) {
                        guardado = true
                        onGuardado()
                    }
                }) { Text("Guardar") }
                if (guardado) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Guardado",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }
    }
}
