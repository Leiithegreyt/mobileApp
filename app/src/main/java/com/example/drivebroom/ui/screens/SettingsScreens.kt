package com.example.drivebroom.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.provider.OpenableColumns
import com.example.drivebroom.network.ChangePasswordRequest
import com.example.drivebroom.network.NetworkClient
import com.example.drivebroom.network.UpdateProfileRequest
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.drivebroom.repository.DriverRepository
import com.example.drivebroom.utils.TokenManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onChangePassword: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val api = remember { NetworkClient(tokenManager).apiService }
    val scope = rememberCoroutineScope()
    // removed from Edit Profile: status editing is in Settings only
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isLoadingProfile by remember { mutableStateOf(false) }
    var isActive by remember { mutableStateOf(true) }
    var canEditActive by remember { mutableStateOf(false) }

    // Load current active + approval gate
    LaunchedEffect(Unit) {
        isLoadingProfile = true
        try {
            val prof = api.getDriverProfileDetails()
            val approved = (prof.approval_status ?: "").equals("approved", ignoreCase = true)
            val active = prof.is_active == true
            isActive = active
            canEditActive = approved
        } catch (_: Exception) {
            // Keep default; show no toast to avoid noise
        } finally {
            isLoadingProfile = false
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Active toggle (approved only)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Active", style = MaterialTheme.typography.titleMedium)
                Switch(checked = isActive, onCheckedChange = {
                    if (!canEditActive) return@Switch
                    isActive = it
                }, enabled = canEditActive)
            }
            if (!canEditActive) {
                Text("Active toggle locked (not approved)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Availability control removed: backend now uses only is_active
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    scope.launch {
                        statusMessage = null
                        val resp = api.updateProfileJson(UpdateProfileRequest(is_active = isActive))
                        if (resp.isSuccessful) {
                            // Re-fetch to reflect backend source of truth
                            runCatching { api.getDriverProfileDetails() }
                                .onSuccess { prof ->
                                    val approved = (prof.approval_status ?: "").equals("approved", ignoreCase = true)
                                    val active = prof.is_active == true
                                    isActive = active
                                    canEditActive = approved
                                }
                            statusMessage = if (isActive) "Active: On" else "Active: Off"
                        } else {
                            statusMessage = resp.errorBody()?.string() ?: "Failed to update"
                        }
                    }
                }, enabled = canEditActive) { Text("Apply") }
                statusMessage?.let { Text(it) }
            }

            Button(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) { Text("Edit Profile") }
            Button(onClick = onChangePassword, modifier = Modifier.fillMaxWidth()) { Text("Change Password") }
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Logout") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    currentName: String?,
    currentPhone: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val api = remember { NetworkClient(tokenManager).apiService }
    val repository = remember { DriverRepository(api) }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(currentName.orEmpty()) }
    var phone by remember { mutableStateOf(currentPhone.orEmpty()) }
    var license by remember { mutableStateOf("") }
    var base64Image by remember { mutableStateOf<String?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var previewBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedMime by remember { mutableStateOf<String?>(null) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("available") }
    var statusExpanded by remember { mutableStateOf(false) }
    val statusOptions = remember { listOf("available", "unavailable") }
    var isSaving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isLoadingProfile by remember { mutableStateOf(false) }

    // Load current profile from backend when screen opens
    LaunchedEffect(Unit) {
        isLoadingProfile = true
        try {
            val prof = api.getDriverProfileDetails()
            name = prof.name.orEmpty()
            phone = prof.phone.orEmpty()
            license = prof.license_number.orEmpty()
            // status removed from edit form
        } catch (_: Exception) {
            // keep existing values on failure
        } finally {
            isLoadingProfile = false
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedImageUri = uri
        if (uri != null) {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                val bytes = stream?.use { it.readBytes() }
                previewBytes = bytes
                // Derive mime type and filename
                selectedMime = context.contentResolver.getType(uri) ?: "image/jpeg"
                var displayName: String? = null
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) displayName = c.getString(idx)
                }
                selectedName = displayName ?: "profile.jpg"
            } catch (_: Exception) {
                message = "Failed to read selected image"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = license, onValueChange = { license = it }, label = { Text("License Number") }, modifier = Modifier.fillMaxWidth())

            // status UI removed from edit form
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { imagePicker.launch("image/*") }) { Text("Choose Photo") }
            }
            previewBytes?.let { bytes ->
                val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                if (bmp != null) {
                    Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(96.dp))
                }
            }

            message?.let { Text(it) }

            Button(
                onClick = {
                    if (isSaving) return@Button
                    isSaving = true
                    message = null
                    scope.launch {
                        try {
                            val hasPhoto = selectedImageUri != null
                            val ok = if (hasPhoto) {
                                // Build multipart parts
                                val phonePart = phone.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())
                                val licensePart = license.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())
                                // Compress and downscale to stay within backend limits (~4MB)
                                fun compress(bytes: ByteArray): ByteArray {
                                    val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
                                    // Downscale if needed (max dimension 1600px)
                                    val maxDim = 1600
                                    val ratio = maxOf(original.width, original.height).toFloat() / maxDim
                                    val scaled: Bitmap = if (ratio > 1f) {
                                        val w = (original.width / ratio).toInt()
                                        val h = (original.height / ratio).toInt()
                                        Bitmap.createScaledBitmap(original, w, h, true)
                                    } else original
                                    val out = ByteArrayOutputStream()
                                    // Always send JPEG for best compression
                                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                                    return out.toByteArray()
                                }
                                val bytes = previewBytes?.let { compress(it) }
                                val photoPart = bytes?.let {
                                    MultipartBody.Part.createFormData(
                                        name = "profile_photo",
                                        filename = (selectedName ?: "profile.jpg"),
                                        body = it.toRequestBody("image/jpeg".toMediaTypeOrNull())
                                    )
                                }
                                val resp = api.updateProfileMultipart(
                                    phone = phonePart,
                                    licenseNumber = licensePart,
                                    status = null,
                                    profile_photo = photoPart
                                )
                                resp.isSuccessful
                            } else {
                                val req = UpdateProfileRequest(name = name, phone = phone, license_number = license)
                                val resp = api.updateProfileJson(req)
                                resp.isSuccessful
                            }
                            if (ok) {
                                // Reload profile so values persist when returning later
                                try {
                                    val prof = api.getDriverProfileDetails()
                                    name = prof.name.orEmpty()
                                    phone = prof.phone.orEmpty()
                                    license = prof.license_number.orEmpty()
                                    // status not used in edit form
                                } catch (_: Exception) {}
                                message = "Saved"
                            } else {
                                message = "Failed to save"
                            }
                        } catch (e: Exception) {
                            message = e.message ?: "Failed to save"
                        } finally {
                            isSaving = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text("Save")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val api = remember { NetworkClient(tokenManager).apiService }
    val scope = rememberCoroutineScope()

    var current by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Change Password") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(value = current, onValueChange = { current = it }, label = { Text("Current Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = newPass, onValueChange = { newPass = it }, label = { Text("New Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = confirm, onValueChange = { confirm = it }, label = { Text("Confirm New Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())

            message?.let { Text(it) }

            Button(
                onClick = {
                    if (isSaving) return@Button
                    if (newPass != confirm) { message = "Passwords do not match"; return@Button }
                    isSaving = true
                    message = null
                    val req = ChangePasswordRequest(current_password = current, new_password = newPass, new_password_confirmation = confirm)
                    scope.launch {
                        val resp = api.changePassword(req)
                        if (resp.isSuccessful) {
                            message = "Password changed"
                            onChanged()
                        } else {
                            message = resp.errorBody()?.string() ?: "Failed to change password"
                        }
                        isSaving = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text("Change Password")
            }
        }
    }
}


