package com.hamada.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class HamadaAccessibilityService : AccessibilityService() {
 companion object {
  @Volatile private var activeService: HamadaAccessibilityService? = null
  @Volatile private var lastExternalScreen: String = ""
  @Volatile private var lastExternalPackage: String = ""
  fun readCurrentScreen(): String? = activeService?.readVisibleText()
  fun readLastExternalScreen(): String? = lastExternalScreen.takeIf { it.isNotBlank() }
  fun lastExternalApp(): String = lastExternalPackage
  fun goBack(): Boolean? = activeService?.performGlobalAction(GLOBAL_ACTION_BACK)
  fun goHome(): Boolean? = activeService?.performGlobalAction(GLOBAL_ACTION_HOME)
  fun clickText(target: String): Boolean? {
   val service = activeService ?: return null
   if (target.isBlank()) return false
   return service.clickVisibleText(target)
  }
 }
 override fun onServiceConnected() {
  super.onServiceConnected()
  activeService = this
 }
 override fun onDestroy() {
  if (activeService === this) activeService = null
  super.onDestroy()
 }
 override fun onAccessibilityEvent(event: AccessibilityEvent?) {
  val currentEvent = event ?: return
  val pkg = currentEvent.packageName?.toString().orEmpty()
  if (pkg.isBlank() || pkg == packageName) return
  if (currentEvent.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
      currentEvent.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
   val root = rootInActiveWindow ?: return
   val out = StringBuilder()
   collectText(root, out)
   val captured = out.toString().trim()
   if (captured.isNotBlank()) {
    lastExternalScreen = captured.take(5000)
    lastExternalPackage = pkg
   }
  }
 }
 override fun onInterrupt() {}
 fun readVisibleText(): String {
  val root = rootInActiveWindow ?: return "مش قادر أقرأ الشاشة الحالية."
  val out = StringBuilder(); collectText(root, out)
  return out.toString().ifBlank { "مش لاقي نص واضح على الشاشة." }
 }
 private fun collectText(node: AccessibilityNodeInfo?, out: StringBuilder) {
  if (node == null) return
  val text = node.text?.toString()?.trim().orEmpty()
  val desc = node.contentDescription?.toString()?.trim().orEmpty()
  if (text.isNotBlank()) out.append(text).append("\n") else if (desc.isNotBlank()) out.append(desc).append("\n")
  for (i in 0 until node.childCount) collectText(node.getChild(i), out)
 }
 private fun normalizeForMatch(value: String): String = value.lowercase()
  .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
  .replace("ى", "ي").replace("ة", "ه")
  .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
  .replace(Regex("\\s+"), " ").trim()
 fun clickVisibleText(target: String): Boolean {
  val root = rootInActiveWindow ?: return false
  return clickMatchingNode(root, normalizeForMatch(target))
 }
 private fun clickMatchingNode(node: AccessibilityNodeInfo?, target: String): Boolean {
  if (node == null) return false
  val text = normalizeForMatch((node.text?.toString() ?: node.contentDescription?.toString()).orEmpty())
  if (text.contains(target) && node.isVisibleToUser) {
   var candidate: AccessibilityNodeInfo? = node
   while (candidate != null) {
    if (candidate.isVisibleToUser && candidate.isClickable) {
     if (candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
    }
    candidate = candidate.parent
   }
  }
  for (i in 0 until node.childCount) if (clickMatchingNode(node.getChild(i), target)) return true
  return false
 }
}