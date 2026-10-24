package com.hamada.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
 private var speechRecognizer: SpeechRecognizer? = null
 private var speechResult by mutableStateOf("اضغط زر الميكروفون الأزرق وقل: يا حمادة، افتح يوتيوب")
 private var status by mutableStateOf("جاهز — اضغط الميكروفون للتحدث")
 private var listening by mutableStateOf(false)
 private var screenText by mutableStateOf("لسه ما قريناش الشاشة.")
 private var clickTarget by mutableStateOf("")
 private var showApps by mutableStateOf(false)
 private var installedApps by mutableStateOf(listOf<AppEntry>())
 private var recordingRoutine by mutableStateOf(false)
 private var recordingName by mutableStateOf("")
 private var savedRoutineNames by mutableStateOf(listOf<String>())
 private val routineSteps = mutableListOf<String>()
 private val routineHandler = Handler(Looper.getMainLooper())

 private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
  if (granted) startListening() else { status = "إذن الميكروفون مرفوض"; speak("لازم تسمح لحمادة باستخدام الميكروفون.") }
 }

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  tts = TextToSpeech(this, this)
  refreshAppList()
  refreshRoutineNames()
  if (SpeechRecognizer.isRecognitionAvailable(this)) {
   speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
   speechRecognizer?.setRecognitionListener(object : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) { status = "سامعك يا معلم، اتكلم دلوقتي"; speak("سامعك يا معلم، اتكلم دلوقتي") }
    override fun onBeginningOfSpeech() { status = "بسمعك..." }
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { listening = false; status = "بحاول أفهم كلامك..." }
    override fun onError(error: Int) {
     listening = false
     status = when (error) {
      SpeechRecognizer.ERROR_NO_MATCH -> "مسمعتش كلام واضح. قرّب من الموبايل وجرّب تاني."
      SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "مستني صوتك؛ اضغط الميكروفون واتكلم بعد ما يقول سامعك."
      SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "إذن الميكروفون مش متاح لحمادة."
      SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "التعرف على الصوت مشغول؛ استنى ثانية وجرب."
      SpeechRecognizer.ERROR_NETWORK -> "خدمة Google أو الإنترنت مش متاحين دلوقتي."
      SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "الاتصال بخدمة الصوت اتأخر. جرّب تاني."
      else -> "خدمة الصوت رجعت خطأ رقم $error. جرّب تاني."
     }
     speak(status)
    }
    override fun onResults(results: Bundle?) {
     listening = false
     val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
     if (!spoken.isNullOrBlank()) { speechResult = spoken; handleCommand(spoken) }
     else status = "ماوصلنيش كلام واضح"
    }
    override fun onPartialResults(partialResults: Bundle?) {
     val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
     if (!partial.isNullOrBlank()) speechResult = partial
    }
    override fun onEvent(eventType: Int, params: Bundle?) {}
   })
  } else {
   status = "خدمة التعرف على الصوت مش متاحة على الجهاز"
  }
  setContent {
   MaterialTheme(colorScheme = darkColorScheme()) {
    HamadaScreen(speechResult, status, listening, {
     if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening()
     else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }, { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
     { screenText = HamadaAccessibilityService.readCurrentScreen() ?: "فعّل خدمة حمادة من إعدادات إمكانية الوصول، وبعدها ارجع واضغط اقرأ الشاشة." ; status = if (HamadaAccessibilityService.readCurrentScreen() == null) "خدمة التحكم بالشاشة مش مفعّلة" else "تم جلب نص الشاشة" ; speak(screenText.take(250)) },
     { target -> val result = HamadaAccessibilityService.clickText(target); status = when (result) { true -> "حاولت أضغط على: $target"; false -> "ملقتش زر قابل للضغط اسمه: $target"; null -> "فعّل خدمة حمادة من إعدادات إمكانية الوصول الأول" }; speak(status) },
     screenText, clickTarget, { clickTarget = it },
     { refreshAppList(); showApps = true }, installedApps, showApps, { showApps = it })
   }
  }
 }

 override fun onInit(result: Int) {
  if (result == TextToSpeech.SUCCESS) {
   val ar = tts?.setLanguage(Locale("ar", "EG")) ?: TextToSpeech.LANG_NOT_SUPPORTED
   if (ar == TextToSpeech.LANG_NOT_SUPPORTED || ar == TextToSpeech.LANG_MISSING_DATA) tts?.setLanguage(Locale.US)
  }
 }

 private fun startListening() {
  val recognizer = speechRecognizer
  if (recognizer == null) {
   status = "خدمة التعرف على الصوت مش متاحة على الجهاز"
   speak("التعرف على الصوت مش متوفر على الموبايل ده.")
   return
  }
  try {
   val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-EG")
    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
   }
   speechResult = "اتكلم يا معلم..."
   status = "بشغّل خدمة التعرّف على الكلام..."
   listening = true
   recognizer.startListening(intent)
  } catch (_: Exception) {
   listening = false
   status = "مش قادر أبدأ التعرف على الصوت على الجهاز ده"
  }
 }

 private fun normalizeCommand(raw: String): String {
  return raw.lowercase(Locale("ar", "EG"))
   .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
   .replace("ى", "ي").replace("ة", "ه")
   .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
   .replace("يا حماده", "")
   .replace(Regex("[،,.!?؟:;]+"), " ")
   .replace(Regex("\\s+"), " ")
   .trim()
 }

 private fun handleCommand(raw: String) {
  val c = normalizeCommand(raw)
  if (recordingRoutine) {
   if (c == "خلص الروتين" || c == "انهي الروتين") {
    recordingRoutine = false
    getSharedPreferences("hamada_routines", MODE_PRIVATE).edit().putString(recordingName, JSONArray(routineSteps).toString()).apply()
    refreshRoutineNames()
    status = "حفظت الروتين $recordingName وفيه ${routineSteps.size} خطوات"
    speak(status)
   } else {
    routineSteps.add(raw)
    status = "سجلت خطوة ${routineSteps.size}. قول الأمر التالي أو خلص الروتين"
    speak(status)
   }
   return
  }
  when {
   c.startsWith("احفظ روتين ") || c.startsWith("سجل روتين ") -> {
    recordingName = c.removePrefix("احفظ روتين ").removePrefix("سجل روتين ").trim()
    if (recordingName.isBlank()) { status = "قول اسم الروتين"; speak(status) }
    else { routineSteps.clear(); recordingRoutine = true; status = "بدأت تسجيل $recordingName. قل الأوامر بالترتيب ثم قول خلص الروتين"; speak(status) }
   }
   c.startsWith("شغل روتين ") || c.startsWith("نفذ روتين ") -> {
    runRoutine(c.removePrefix("شغل روتين ").removePrefix("نفذ روتين ").trim())
   }
   c.contains("اقرا") && (c.contains("شاشه") || c.contains("الشاشه")) -> {
    val captured = HamadaAccessibilityService.readLastExternalScreen()
    if (captured != null) {
     screenText = captured
     status = "دي آخر شاشة رصدها حمادة من تطبيق تاني"
     speak("دي النصوص اللي قدرت أقرأها من آخر تطبيق. " + captured.take(220))
    } else {
     status = "لسه مفيش شاشة من تطبيق تاني اتقرت"
     speak("فعّل عين حمادة من إمكانية الوصول، افتح التطبيق اللي عايزني أقرأه، وبعدها ارجع وقول اقرأ الشاشة.")
    }
   }
   c.contains("ارجع") || c.contains("رجوع") || c == "back" || c.contains("الصفحه اللي فاتت") -> {
    val result = HamadaAccessibilityService.goBack()
    status = if (result == true) "أندرويد قبل أمر الرجوع" else "الرجوع مش متاح؛ فعّل عين حمادة"
    speak(status)
   }
   c.contains("الشاشه الرئيسيه") || c.contains("روح للرئيسيه") || c == "home" || c.contains("افتح الهوم") -> {
    val result = HamadaAccessibilityService.goHome()
    status = if (result == true) "أندرويد قبل أمر الشاشة الرئيسية" else "مش قادر أروح للرئيسية؛ فعّل عين حمادة"
    speak(status)
   }
   c.startsWith("اضغط") || c.startsWith("دوس") || c.startsWith("اكبس") || c.startsWith("انقر") -> {
    val target = c
     .removePrefix("اضغط على ").removePrefix("اضغط ").removePrefix("دوس على ").removePrefix("دوس ")
     .removePrefix("اكبس على ").removePrefix("اكبس ").removePrefix("انقر على ").removePrefix("انقر ")
     .removePrefix("على ").removePrefix("زر ").trim()
    if (target.isBlank()) {
     status = "قول اسم العنصر بعد كلمة اضغط، زي اضغط على بحث"
     speak(status)
    } else {
     val result = HamadaAccessibilityService.clickText(target)
     status = when (result) {
      true -> "أندرويد قبل محاولة الضغط على $target؛ راجع الشاشة للتأكد"
      false -> "مش لاقي عنصر ظاهر اسمه $target"
      null -> "فعّل عين حمادة من إعدادات إمكانية الوصول الأول"
     }
     speak(status)
    }
   }
   c.startsWith("افتح ") || c.startsWith("شغل ") || c.startsWith("روح ") -> {
    val requested = c.removePrefix("افتح ").removePrefix("شغل ").removePrefix("روح ").trim()
    val app = installedApps.firstOrNull { normalizeCommand(it.label).contains(requested) || requested.contains(normalizeCommand(it.label)) }
    if (app == null) {
     refreshAppList()
     val match = installedApps.firstOrNull { normalizeCommand(it.label).contains(requested) || requested.contains(normalizeCommand(it.label)) }
     if (match != null) launchDiscoveredApp(match) else { status = "مش لاقي تطبيق اسمه $requested"; speak(status) }
    } else launchDiscoveredApp(app)
   }
   c.contains("اعدادات") || c.contains("الاعدادات") -> {
    status = "بفتح إعدادات أندرويد"
    speak(status)
    startActivity(Intent(Settings.ACTION_SETTINGS))
   }
   else -> {
    status = "فهمت الكلام، لكن الأمر ده مش مدعوم لسه"
    speak("فهمت كلامك، بس لسه ما ضفتش تنفيذ الأمر ده. جرب افتح يوتيوب، ارجع، أو اضغط على بحث.")
   }
  }
 }
 private fun refreshRoutineNames() { savedRoutineNames = getSharedPreferences("hamada_routines", MODE_PRIVATE).all.keys.sorted() }
 private fun runRoutine(name: String) {
  val stored = getSharedPreferences("hamada_routines", MODE_PRIVATE).getString(name, null)
  if (stored == null) { status = "مش لاقي روتين اسمه $name"; speak(status); return }
  val steps = try { val arr = JSONArray(stored); (0 until arr.length()).map { arr.getString(it) } } catch (_: Exception) { emptyList() }
  if (steps.isEmpty()) { status = "الروتين فاضي"; speak(status); return }
  status = "بدأت تنفيذ الروتين $name، وعدد الخطوات ${steps.size}"
  speak(status)
  steps.forEachIndexed { index, step -> routineHandler.postDelayed({ status = "الخطوة ${index + 1} من ${steps.size}"; handleCommand(step) }, index * 3000L) }
 }
 private fun refreshAppList() {
  val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  installedApps = packageManager.queryIntentActivities(launcher, 0)
   .map { AppEntry(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
   .distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
 }
 private fun launchDiscoveredApp(app: AppEntry) {
  val intent = packageManager.getLaunchIntentForPackage(app.packageName)
  if (intent == null) { status = "التطبيق ملوش شاشة تشغيل مباشرة"; speak(status); return }
  status = "بحاول أفتح ${app.label}"
  speak(status)
  try { startActivity(intent) } catch (_: Exception) { status = "ماقدرتش أفتح ${app.label}"; speak(status) }
 }
 private fun launchPackage(pkg: String, message: String) {
  val i = packageManager.getLaunchIntentForPackage(pkg)
  if (i != null) { status = message; speak(message); startActivity(i) }
  else { status = "التطبيق مش متثبت على الموبايل"; speak("التطبيق ده مش متثبت على الموبايل.") }
 }
 private fun speak(text: String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hamada-response") }
 override fun onDestroy() { speechRecognizer?.destroy(); tts?.stop(); tts?.shutdown(); super.onDestroy() }
}

private data class AppEntry(val label: String, val packageName: String)

@Composable private fun HamadaScreen(speech: String, status: String, listening: Boolean, onListen: () -> Unit, onAccessibility: () -> Unit, onReadScreen: () -> Unit, onClickText: (String) -> Unit, screenText: String, clickTarget: String, onClickTargetChange: (String) -> Unit, onShowApps: () -> Unit, installedApps: List<AppEntry>, showApps: Boolean, setShowApps: (Boolean) -> Unit) {
 val bg = Brush.verticalGradient(listOf(Color(0xFF020817), Color(0xFF07152D), Color(0xFF02040A)))
 Surface(Modifier.fillMaxSize(), color = Color(0xFF020817)) {
  Row(Modifier.fillMaxSize().background(bg).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
   Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Text("حمادة", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFF66C7FF))
    Text("مساعدك الشخصي", color = Color(0xFF9DB8D8), fontSize = 14.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) { RobotEye(); RobotEye() }
    Text(speech, color = Color.White, fontSize = 16.sp, maxLines = 2)
    Text(status, color = if (listening) Color(0xFF70F0C0) else Color(0xFF79BFFF), fontSize = 12.sp, maxLines = 2)
    Button(onClick = onListen, modifier = Modifier.height(58.dp), shape = RoundedCornerShape(30.dp), colors = ButtonDefaults.buttonColors(containerColor = if (listening) Color(0xFFB3261E) else Color(0xFF087CFA))) {
     Text(if (listening) "🎙  بسمعك... اضغط للتجربة تاني" else "🎙  اضغط هنا واتكلم", fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
     OutlinedButton(onClick = onAccessibility) { Text("تفعيل عين حمادة") }
     Button(onClick = onReadScreen) { Text("اقرأ الشاشة") }
    }
    OutlinedTextField(value = clickTarget, onValueChange = onClickTargetChange, label = { Text("اسم الزر الظاهر") }, placeholder = { Text("مثال: بحث") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedButton(onClick = { onClickText(clickTarget) }, enabled = clickTarget.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("اضغط على الاسم") }
    OutlinedButton(onClick = onShowApps, modifier = Modifier.fillMaxWidth()) { Text("اعرف التطبيقات الموجودة على الموبايل") }
    Text(if (recordingRoutine) "تسجيل روتين: $recordingName — قول خلص الروتين" else "الروتينات المحفوظة: ${savedRoutineNames.joinToString().ifBlank { "لا يوجد" }}", color = Color(0xFF70F0C0), fontSize = 11.sp)
    Text("النص المقروء:", color = Color(0xFF66C7FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
    Surface(color = Color(0xFF0A1A31), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 105.dp)) {
     Text(screenText, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(8.dp), maxLines = 7)
    }
   }
   Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Text("جرّب تقول", color = Color(0xFF66C7FF), fontWeight = FontWeight.Bold)
    QuickItem("افتح يوتيوب")
    QuickItem("افتح واتساب")
    QuickItem("افتح سبوتيفاي")
    QuickItem("افتح الإعدادات")
    Text("الإصدار 0.2", color = Color.Gray, fontSize = 11.sp)
   }
  }
  if (showApps) {
   AlertDialog(onDismissRequest = { setShowApps(false) }, title = { Text("التطبيقات المتاحة على الجهاز (${installedApps.size})") }, text = {
    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
     installedApps.forEach { app -> Text("${app.label} · ${app.packageName}", color = Color.White, modifier = Modifier.padding(vertical = 5.dp)) }
    }
   }, confirmButton = { TextButton(onClick = { setShowApps(false) }) { Text("إغلاق") } })
  }
 }
}
@Composable private fun RobotEye() {
 Box(Modifier.size(width=88.dp,height=108.dp).background(Color(0xFF061C3D),RoundedCornerShape(50)).padding(7.dp).background(Brush.radialGradient(listOf(Color(0xFF8AE6FF),Color(0xFF168BFF),Color(0xFF06306F))),CircleShape), contentAlignment=Alignment.Center) {
  Box(Modifier.size(43.dp).background(Color(0xFF020817),CircleShape))
  Box(Modifier.offset(x=12.dp,y=(-12).dp).size(12.dp).background(Color.White,CircleShape))
 }
}
@Composable private fun QuickItem(text: String) {
 Surface(color=Color(0xFF0A1A31),shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()) {
  Text(text,color=Color(0xFFB9DFFF),fontSize=12.sp,modifier=Modifier.padding(10.dp))
 }
}