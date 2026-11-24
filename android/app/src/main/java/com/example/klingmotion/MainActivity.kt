package com.example.klingmotion

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { KlingApp() } }
}

private val http = OkHttpClient.Builder().connectTimeout(60, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).writeTimeout(180, TimeUnit.SECONDS).build()
private data class HistoryItem(val id: String, val status: String, val url: String, val time: Long)

@Composable
fun KlingApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var imageName by remember { mutableStateOf("Belum memilih foto") }
    var videoName by remember { mutableStateOf("Belum memilih video") }
    var prompt by remember { mutableStateOf("") }
    var orientation by remember { mutableStateOf("video") }
    var resolution by remember { mutableStateOf("1080p") }
    var audio by remember { mutableStateOf("original") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Siap digunakan") }
    var taskId by remember { mutableStateOf("") }
    var resultUrl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(loadHistory(context)) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri; imageName = uri?.lastPathSegment ?: if (uri == null) "Belum memilih foto" else "Foto dipilih"
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        videoUri = uri; videoName = uri?.lastPathSegment ?: if (uri == null) "Belum memilih video" else "Video dipilih"
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF9B87FF), background = Color(0xFF10111A), surface = Color(0xFF1B1D2A))) {
        Column(Modifier.fillMaxSize().background(Color(0xFF10111A)).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column { Text("KLING MOTION", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White); Text("PERSONAL MOTION CONTROL", color = Color.LightGray, style = MaterialTheme.typography.labelSmall) }
                Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFB7A7FF), modifier = Modifier.size(34.dp))
            }
            Text("1. Bahan referensi", color = Color.White, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectCard("Foto karakter", imageName, Icons.Default.Image, Modifier.weight(1f)) { imagePicker.launch("image/*") }
                SelectCard("Video gerakan", videoName, Icons.Default.VideoLibrary, Modifier.weight(1f)) { videoPicker.launch("video/*") }
            }
            Text("2. Pengaturan", color = Color.White, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = prompt, onValueChange = { if (it.length <= 2500) prompt = it }, label = { Text("Prompt tambahan (opsional)") }, placeholder = { Text("Contoh: pertahankan pakaian dan tampilan karakter") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Text("Orientasi karakter", color = Color.LightGray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = orientation == "video", onClick = { orientation = "video" }, label = { Text("Ikuti video (maks. 30 dtk)") })
                FilterChip(selected = orientation == "image", onClick = { orientation = "image" }, label = { Text("Ikuti foto (maks. 10 dtk)") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = resolution == "720p", onClick = { resolution = "720p" }, label = { Text("720p") })
                FilterChip(selected = resolution == "1080p", onClick = { resolution = "1080p" }, label = { Text("1080p") })
                FilterChip(selected = audio == "original", onClick = { audio = "original" }, label = { Text("Audio asli") })
                FilterChip(selected = audio == "off", onClick = { audio = "off" }, label = { Text("Tanpa audio") })
            }
            Button(onClick = {
                if (imageUri == null || videoUri == null) { error = "Pilih foto dan video referensi terlebih dahulu."; return@Button }
                if (busy) return@Button
                busy = true; error = ""; resultUrl = ""; status = "Mengunggah bahan..."
                scope.launch {
                    try {
                        val imageUrl = uploadUri(context, imageUri!!)
                        val videoUrl = uploadUri(context, videoUri!!)
                        status = "Mengirim ke Kling..."
                        val create = JSONObject().put("imageUrl", imageUrl).put("videoUrl", videoUrl).put("prompt", prompt).put("characterOrientation", orientation).put("resolution", resolution).put("audio", audio)
                        val created = postJson("api/motion", create)
                        val id = created.getJSONObject("data").getString("id")
                        taskId = id; saveHistory(context, HistoryItem(id, "submitted", "", System.currentTimeMillis())); history = loadHistory(context)
                        var finished = false
                        for (attempt in 0 until 120) {
                            delay(5000)
                            val taskResponse = getJson("api/tasks/$id")
                            val taskArray = taskResponse.optJSONArray("data")
                            val task = taskArray?.optJSONObject(0)
                            val current = task?.optString("status", "processing") ?: "processing"
                            status = "Status: $current"
                            if (current == "succeeded") {
                                val outputs = task?.optJSONArray("outputs")
                                for (i in 0 until (outputs?.length() ?: 0)) {
                                    val output = outputs!!.optJSONObject(i)
                                    if (output.optString("type") == "video") { resultUrl = output.optString("url"); break }
                                }
                                saveHistory(context, HistoryItem(id, current, resultUrl, System.currentTimeMillis())); history = loadHistory(context); finished = true; break
                            }
                            if (current == "failed") {
                                error = task?.optString("message", "Task gagal") ?: "Task gagal"
                                saveHistory(context, HistoryItem(id, current, "", System.currentTimeMillis())); history = loadHistory(context); finished = true; break
                            }
                        }
                        if (!finished && resultUrl.isBlank() && error.isBlank()) status = "Masih diproses. Tekan cek status untuk memperbarui."
                    } catch (e: Exception) { error = e.message ?: "Terjadi kesalahan"; status = "Gagal" }
                    finally { busy = false }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.width(8.dp)); Text(if (busy) "MEMPROSES..." else "GENERATE MOTION", fontWeight = FontWeight.Bold)
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D2A)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("3. Hasil video", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(status, color = Color.LightGray)
                    if (taskId.isNotBlank()) Text("Task ID: $taskId", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    if (resultUrl.isNotBlank()) {
                        AndroidView(factory = { ctx -> VideoView(ctx).apply { setVideoURI(Uri.parse(resultUrl)); setOnPreparedListener { it.isLooping = true; start() } } }, modifier = Modifier.fillMaxWidth().height(220.dp))
                        Button(onClick = { downloadVideo(context, resultUrl) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(6.dp)); Text("Download video") }
                    }
                    if (error.isNotBlank()) Text(error, color = Color(0xFFFF8A80))
                }
            }
            Text("4. Riwayat", color = Color.White, fontWeight = FontWeight.Bold)
            if (history.isEmpty()) Text("Belum ada riwayat generate.", color = Color.Gray)
            history.take(20).forEach { item ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D2A)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) { Text(item.id, color = Color.White, style = MaterialTheme.typography.bodySmall); Text(item.status, color = Color.LightGray) }
                        if (item.url.isNotBlank()) TextButton(onClick = { resultUrl = item.url; taskId = item.id }) { Text("Buka") }
                    }
                }
            }
            Text("Catatan: biaya API Kling mengikuti akun Anda. Hasil Kling memiliki masa simpan terbatas; segera download video.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SelectCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.height(125.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D2A)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = Color(0xFFB7A7FF), modifier = Modifier.size(27.dp))
            Column { Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(subtitle, color = Color.LightGray, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

private suspend fun uploadUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
    val ext = when { mime.contains("png") -> ".png"; mime.contains("jpeg") -> ".jpg"; mime.contains("quicktime") -> ".mov"; else -> ".mp4" }
    val temp = File(context.cacheDir, "upload-${System.currentTimeMillis()}$ext")
    context.contentResolver.openInputStream(uri).use { input -> FileOutputStream(temp).use { output -> input!!.copyTo(output) } }
    try {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", temp.name, temp.asRequestBody(mime.toMediaType())).build()
        val request = Request.Builder().url(BuildConfig.API_BASE_URL + "api/upload").header("Authorization", "Bearer ${BuildConfig.API_ACCESS_TOKEN}").post(body).build()
        http.newCall(request).execute().use { response -> val text = response.body?.string().orEmpty(); if (!response.isSuccessful) throw Exception(JSONObject(text).optString("error", "Upload gagal (${response.code})")); JSONObject(text).getString("url") }
    } finally { temp.delete() }
}

private suspend fun postJson(path: String, json: JSONObject): JSONObject = withContext(Dispatchers.IO) {
    val body = json.toString().toRequestBody("application/json".toMediaType())
    val request = Request.Builder().url(BuildConfig.API_BASE_URL + path).header("Authorization", "Bearer ${BuildConfig.API_ACCESS_TOKEN}").post(body).build()
    http.newCall(request).execute().use { response -> val text = response.body?.string().orEmpty(); val obj = JSONObject(text); if (!response.isSuccessful) throw Exception(obj.optString("error", "Server error ${response.code}")); obj }
}
private suspend fun getJson(path: String): JSONObject = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(BuildConfig.API_BASE_URL + path).header("Authorization", "Bearer ${BuildConfig.API_ACCESS_TOKEN}").get().build()
    http.newCall(request).execute().use { response -> val text = response.body?.string().orEmpty(); val obj = JSONObject(text); if (!response.isSuccessful) throw Exception(obj.optString("error", "Server error ${response.code}")); obj }
}

private fun saveHistory(context: Context, item: HistoryItem) {
    val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)
    val old = prefs.getString("items", "[]") ?: "[]"
    val arr = org.json.JSONArray(old)
    val next = org.json.JSONArray()
    next.put(JSONObject().put("id", item.id).put("status", item.status).put("url", item.url).put("time", item.time))
    for (i in 0 until arr.length()) { val obj = arr.getJSONObject(i); if (obj.optString("id") != item.id) next.put(obj) }
    prefs.edit().putString("items", next.toString()).apply()
}
private fun loadHistory(context: Context): List<HistoryItem> {
    return try { val arr = org.json.JSONArray(context.getSharedPreferences("history", Context.MODE_PRIVATE).getString("items", "[]")); (0 until arr.length()).map { val o = arr.getJSONObject(it); HistoryItem(o.getString("id"), o.getString("status"), o.optString("url", ""), o.optLong("time")) } } catch (_: Exception) { emptyList() }
}
private fun downloadVideo(context: Context, url: String) {
    val request = DownloadManager.Request(Uri.parse(url)).setTitle("Kling Motion video").setDescription("Mengunduh hasil video").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "Kling-${System.currentTimeMillis()}.mp4").setAllowedOverMetered(true).setAllowedOverRoaming(true)
    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
}
