package com.hamada.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
 private var tts: TextToSpeech? = null
 private var speechResult by mutableStateOf("اضغط الميكروفون وقل: يا حمادة، افتح يوتيوب")
 private var status by mutableStateOf("جاهز")
 private val askSpeech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
  val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
  if (!spoken.isNullOrBlank()) { speechResult = spoken; handleCommand(spoken) } else status = "لم أستقبل كلامًا واضحًا"
 }
 private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) startListening() else speak("لازم تسمح لحمادة باستخدام الميكروفون.") }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState); tts = TextToSpeech(this, this)
  setContent { MaterialTheme(colorScheme = darkColorScheme()) { HamadaScreen(speechResult, status, { if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening() else askMic.launch(Manifest.permission.RECORD_AUDIO) }, { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) } }
 }
 override fun onInit(result: Int) { if (result == TextToSpeech.SUCCESS) { val ar = tts?.setLanguage(Locale("ar","EG")) ?: TextToSpeech.LANG_NOT_SUPPORTED; if (ar == TextToSpeech.LANG_NOT_SUPPORTED || ar == TextToSpeech.LANG_MISSING_DATA) tts?.setLanguage(Locale.US) } }
 private fun startListening() {
  val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG"); putExtra(RecognizerIntent.EXTRA_PROMPT, "اتكلم يا معلم") }
  try { askSpeech.launch(intent) } catch (_: Exception) { status = "خدمة التعرف على الصوت غير موجودة على الجهاز"; speak("التعرف على الصوت مش متوفر على الموبايل ده.") }
 }
 private fun handleCommand(raw: String) {
  val c = raw.lowercase(Locale("ar","EG")).replace("يا حمادة","").trim()
  when {
   c.contains("افتح") && (c.contains("يوتيوب") || c.contains("youtube")) -> launchPackage("com.google.android.youtube","حاضر، بفتح يوتيوب.")
   c.contains("افتح") && (c.contains("واتساب") || c.contains("whatsapp")) -> launchPackage("com.whatsapp","حاضر، بفتح واتساب.")
   c.contains("افتح") && (c.contains("سبوتيفاي") || c.contains("spotify")) -> launchPackage("com.spotify.music","حاضر، بفتح سبوتيفاي.")
   c.contains("إعدادات") || c.contains("الاعدادات") -> { status = "فتح الإعدادات"; startActivity(Intent(Settings.ACTION_SETTINGS)); speak("حاضر، بفتح الإعدادات.") }
   else -> { status = "الأمر ده لسه مش مضاف في الإصدار الأول"; speak("سمعتك. الأمر ده لسه بنتعلمه في الإصدار الأول.") }
  }
 }
 private fun launchPackage(pkg: String, message: String) { val i = packageManager.getLaunchIntentForPackage(pkg); if (i != null) { status = message; speak(message); startActivity(i) } else { status = "التطبيق مش متثبت على الموبايل"; speak("التطبيق ده مش متثبت على الموبايل.") } }
 private fun speak(text: String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hamada-response") }
 override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}

@Composable private fun HamadaScreen(speech: String, status: String, onListen: () -> Unit, onAccessibility: () -> Unit) {
 val bg = Brush.verticalGradient(listOf(Color(0xFF020817), Color(0xFF07152D), Color(0xFF02040A)))
 Surface(Modifier.fillMaxSize(), color = Color(0xFF020817)) {
  Row(Modifier.fillMaxSize().background(bg).padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
   Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Text("حمادة", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFF66C7FF)); Text("مساعدك الشخصي", color = Color(0xFF9DB8D8), fontSize = 14.sp)
    Spacer(Modifier.height(18.dp)); Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) { RobotEye(); RobotEye() }
    Spacer(Modifier.height(18.dp)); Text(speech, color = Color.White, fontSize = 17.sp, maxLines = 2); Spacer(Modifier.height(6.dp)); Text(status, color = Color(0xFF79BFFF), fontSize = 13.sp); Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Button(onClick = onListen) { Text("🎙 اتكلم مع حمادة") }; OutlinedButton(onClick = onAccessibility) { Text("تفعيل التحكم بالشاشة") } }
   }
   Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { QuickItem("افتح يوتيوب"); QuickItem("افتح واتساب"); QuickItem("افتح سبوتيفاي"); QuickItem("افتح الإعدادات"); Text("الإصدار 0.1", color = Color.Gray, fontSize = 11.sp) }
  }
 }
}
@Composable private fun RobotEye() { Box(Modifier.size(width=100.dp,height=122.dp).background(Color(0xFF061C3D),RoundedCornerShape(50)).padding(7.dp).background(Brush.radialGradient(listOf(Color(0xFF8AE6FF),Color(0xFF168BFF),Color(0xFF06306F))),CircleShape), contentAlignment=Alignment.Center) { Box(Modifier.size(47.dp).background(Color(0xFF020817),CircleShape)); Box(Modifier.offset(x=13.dp,y=(-13).dp).size(13.dp).background(Color.White,CircleShape)) } }
@Composable private fun QuickItem(text: String) { Surface(color=Color(0xFF0A1A31),shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()) { Text(text,color=Color(0xFFB9DFFF),fontSize=12.sp,modifier=Modifier.padding(10.dp)) } }