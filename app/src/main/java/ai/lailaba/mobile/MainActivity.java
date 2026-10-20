package ai.lailaba.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ActivityNotFoundException;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.StatFs;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.media.AudioManager;
import android.service.notification.StatusBarNotification;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MainActivity extends Activity {
    private static final String DEFAULT_BASE = "https://100.57.251.241.nip.io/api/v1";
    private static final String PREFS = "lailaba_settings";
    private LinearLayout root, messagesBox;
    private ScrollView scroll;
    private EditText promptInput, baseInput, keyInput, modelInput;
    private TextView status;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final JSONArray messages = new JSONArray();
    private JSONArray pendingCalls;
    private int pendingCallIndex;
    private int turnDepth;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        appendMessage("Lailaba", "I'm your Android device assistant. I can inspect device, battery, network, and storage status; find and open apps; open settings, web searches, and maps; open the camera; prepare alarms, timers, and calendar events; share text; search contacts; prepare calls/messages; and use optional on-screen actions. Android permissions and system protections still apply; every device action requires your approval.");
        loadPrefs();
    }

    private void buildUi() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(18, 18, 18, 12); root.setBackgroundColor(Color.BLACK);
        TextView title = new TextView(this); title.setText("LAILABA AI  ·  DEVICE AGENT"); title.setTextColor(Color.WHITE); title.setTextSize(19); title.setGravity(Gravity.CENTER_VERTICAL); title.setPadding(0, 4, 0, 12); title.setTypeface(null, 1); root.addView(title);
        status = new TextView(this); status.setText("● Ready · Device actions require permission"); status.setTextColor(Color.LTGRAY); status.setTextSize(12); status.setPadding(0, 0, 0, 10); root.addView(status);
        scroll = new ScrollView(this); scroll.setFillViewport(false); messagesBox = new LinearLayout(this); messagesBox.setOrientation(LinearLayout.VERTICAL); messagesBox.setPadding(0, 8, 0, 8); scroll.addView(messagesBox);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        promptInput = new EditText(this); promptInput.setHint("Tell the agent what you want to do…"); promptInput.setTextColor(Color.WHITE); promptInput.setHintTextColor(0xFF999999); promptInput.setBackgroundColor(0xFF2B2B2B); promptInput.setPadding(12, 10, 12, 10); promptInput.setMinLines(2); promptInput.setGravity(Gravity.TOP); root.addView(promptInput, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        Button send = button("Send task"); send.setOnClickListener(v -> sendPrompt()); row.addView(send, new LinearLayout.LayoutParams(0, -2, 1));
        Button settings = button("Provider setup"); settings.setOnClickListener(v -> showProviderDialog()); row.addView(settings, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(row);
        LinearLayout perms = new LinearLayout(this); perms.setOrientation(LinearLayout.HORIZONTAL);
        Button contacts = button("Allow contacts"); contacts.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 101)); perms.addView(contacts, new LinearLayout.LayoutParams(0, -2, 1));
        Button sms = button("Allow SMS read"); sms.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.READ_SMS}, 102)); perms.addView(sms, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(perms);
        Button accessibility = button("Enable on-screen actions in Android Settings"); accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))); root.addView(accessibility);
        Button notificationAccess = button("Configure notification access"); notificationAccess.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))); root.addView(notificationAccess);
        setContentView(root);
    }

    private Button button(String text) { Button b = new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2B2B2B)); return b; }
    private void loadPrefs() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (p.getString("base", null) == null) p.edit().putString("base", DEFAULT_BASE).apply();
        updateStatus();
    }
    private void updateStatus() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String base = p.getString("base", DEFAULT_BASE);
        String model = p.getString("model", "");
        status.setText("● " + (SecretStore.getApiKey(this).isEmpty() ? "API key not configured" : "API key saved") + " · " + (base.contains("100.57.251.241.nip.io") ? "Lailaba AI" : "Custom provider") + (model.isEmpty() ? " · model auto-discovery" : " · " + model) + " · accessibility " + (LailabaAccessibilityService.isEnabled() ? "enabled" : "off") + " · notification access " + (LailabaNotificationListener.isEnabled() ? "enabled" : "off"));
    }

    private void showProviderDialog() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(20, 10, 20, 0);
        baseInput = field("API base URL", p.getString("base", DEFAULT_BASE), false); form.addView(baseInput);
        keyInput = field("API key (stored encrypted on this device)", SecretStore.getApiKey(this), true); form.addView(keyInput);
        modelInput = field("Model name (leave blank for Lailaba auto-discovery)", p.getString("model", ""), false); form.addView(modelInput);
        new AlertDialog.Builder(this).setTitle("Provider setup").setMessage("Use Lailaba AI or an OpenAI-compatible provider. HTTPS is required. Provider credentials are sent only to the base URL you enter.").setView(form)
            .setNegativeButton("Cancel", null).setPositiveButton("Validate & save", (d, w) -> {
                String base = baseInput.getText().toString().trim().replaceAll("/+$", ""); String key = keyInput.getText().toString().trim(); String model = modelInput.getText().toString().trim();
                if (!base.startsWith("https://") || key.isEmpty()) { toast("Enter an HTTPS base URL and API key."); return; }
                executor.execute(() -> validateAndSave(base, key, model));
            }).show();
    }
    private EditText field(String hint, String value, boolean secret) { EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setTextColor(Color.WHITE); e.setHintTextColor(0xFF999999); e.setSingleLine(true); e.setInputType(secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); return e; }
    private void validateAndSave(String base, String key, String model) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(base + "/models").openConnection(); c.setRequestMethod("GET"); c.setConnectTimeout(10000); c.setReadTimeout(12000); c.setRequestProperty("Authorization", "Bearer " + key); int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("Provider validation failed (HTTP " + code + "). Check the URL and key.");
            String body = readBody(c.getInputStream()); JSONObject json = new JSONObject(body);
            if (model.isEmpty() && !base.contains("100.57.251.241.nip.io")) {
                JSONArray data = json.optJSONArray("data"); if (data != null && data.length() > 0) model = data.getJSONObject(0).optString("id", "");
                if (model.isEmpty()) throw new Exception("No model returned. Enter a model name in Provider setup.");
            }
            SecretStore.saveApiKey(this, key); getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("base", base).putString("model", model).apply();
            String finalModel = model;
            runOnUiThread(() -> { updateStatus(); toast("Provider saved and API key validated" + (finalModel.isEmpty() ? "" : " · model " + finalModel)); });
        } catch (Exception e) { runOnUiThread(() -> toast(e.getMessage() == null ? "Provider validation failed" : e.getMessage())); }
    }

    private void sendPrompt() {
        String prompt = promptInput.getText().toString().trim(); if (prompt.isEmpty()) return;
        if (SecretStore.getApiKey(this).isEmpty()) { showProviderDialog(); return; }
        promptInput.setText(""); appendMessage("You", prompt);
        if (messages.length() == 0) messages.put(systemMessage());
        messages.put(new JSONObjectSafe().message("user", prompt)); turnDepth = 0; status.setText("◉ Agent working…"); requestAgent();
    }
    private JSONObject systemMessage() {
        JSONObject o = new JSONObject();
        try { o.put("role", "system"); o.put("content", "You are Lailaba AI Device Agent on the user's Android phone. Use only provided tools for device actions and never claim an action succeeded unless its tool result supports that. Ask concise clarification when a person, app, time, number, or action is ambiguous. Every tool call requires user approval in the app. Contacts and SMS are sensitive; only read them when specifically requested. Calls open the dialer and never place calls automatically; SMS opens a draft and the user must press Send. Calendar events, alarms, timers, camera, browser, maps, settings, and share-sheet actions open Android UI for the user to review. Never bypass Android security, permissions, accessibility consent, or app sandboxing. Do not claim unrestricted control, root, arbitrary shell, hidden monitoring, or background actions. Use the least-privileged available tool."); } catch (Exception ignored) { }
        return o;
    }
    private void requestAgent() {
        if (++turnDepth > 8) { status.setText("● Ready"); appendMessage("Lailaba", "Stopped after the tool-call safety limit. Send a follow-up if more work is needed."); return; }
        status.setText("◉ Thinking…");
        executor.execute(() -> {
            try {
                SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE); String base = p.getString("base", DEFAULT_BASE); String key = SecretStore.getApiKey(this); String model = p.getString("model", "");
                if (model.isEmpty()) model = discoverModel(base, key);
                JSONObject payload = new JSONObject(); payload.put("model", model); payload.put("messages", messages); payload.put("tools", toolSchemas()); payload.put("tool_choice", "auto"); payload.put("temperature", 0.2);
                JSONObject response = postJson(base + "/chat/completions", key, payload); JSONArray choices = response.getJSONArray("choices"); JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                JSONArray calls = message.optJSONArray("tool_calls");
                if (calls != null && calls.length() > 0) {
                    messages.put(message); pendingCalls = calls; pendingCallIndex = 0; runOnUiThread(this::processNextToolCall);
                } else {
                    String content = message.optString("content", "(No text response)"); messages.put(message); runOnUiThread(() -> { appendMessage("Lailaba", content); status.setText("● Ready"); updateStatus(); });
                }
            } catch (Exception e) { runOnUiThread(() -> { appendMessage("Error", e.getMessage() == null ? "Request failed" : e.getMessage()); status.setText("● Request failed"); }); }
        });
    }
    private String discoverModel(String base, String key) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + "/models").openConnection(); c.setRequestMethod("GET"); c.setConnectTimeout(10000); c.setReadTimeout(12000); c.setRequestProperty("Authorization", "Bearer " + key); int code = c.getResponseCode(); if (code < 200 || code >= 300) throw new Exception("Model discovery failed (HTTP " + code + ")");
        JSONArray data = new JSONObject(readBody(c.getInputStream())).optJSONArray("data"); if (data == null || data.length() == 0) throw new Exception("Provider returned no models; configure a model name."); return data.getJSONObject(0).optString("id", "");
    }
    private JSONObject postJson(String url, String key, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(); c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(60000); c.setDoOutput(true); c.setRequestProperty("Authorization", "Bearer " + key); c.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        int code = c.getResponseCode(); InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream(); String response = readBody(stream); if (code < 200 || code >= 300) throw new Exception("API error HTTP " + code + ": " + response.substring(0, Math.min(response.length(), 500))); return new JSONObject(response);
    }
    private String readBody(InputStream in) throws Exception { StringBuilder b = new StringBuilder(); try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) { String line; while ((line = r.readLine()) != null) b.append(line); } return b.toString(); }

    private JSONArray toolSchemas() throws Exception {
        JSONArray a = new JSONArray();
        a.put(tool("get_device_info", "Read basic, non-sensitive device model and Android version information.", new JSONObject(), new String[]{}));
        a.put(tool("get_battery_status", "Read current battery percentage and charging state.", new JSONObject(), new String[]{}));
        a.put(tool("get_network_status", "Read the current network transport type and whether internet capability is reported; does not read traffic or passwords.", new JSONObject(), new String[]{}));
        a.put(tool("get_storage_status", "Read approximate total and free space on the device data partition.", new JSONObject(), new String[]{}));
        a.put(tool("list_apps", "List launchable apps visible to this app through Android package visibility rules.", props("query", "string", "Optional app-name filter"), new String[]{}));
        a.put(tool("open_url", "Open a web address in the user's browser. Only http and https URLs are accepted.", props("url", "string", "Web URL beginning with https:// or http://"), new String[]{"url"}));
        a.put(tool("share_text", "Open Android's share sheet so the user can choose where to send text.", props("text", "string", "Text to share"), new String[]{"text"}));
        a.put(tool("search_web", "Open a browser search for the supplied query. Does not submit credentials or perform purchases.", props("query", "string", "Search terms"), new String[]{"query"}));
        a.put(tool("search_maps", "Open a map app with a place or address search for the user to review.", props("query", "string", "Place, address, or destination"), new String[]{"query"}));
        a.put(tool("open_camera", "Open an installed camera app. Capture and saving remain under user control.", new JSONObject(), new String[]{}));
        a.put(tool("set_alarm", "Open the Android clock app to create an alarm at a specified hour and minute; user reviews the alarm.", props3("hour", "integer", "Hour from 0 to 23", "minute", "integer", "Minute from 0 to 59", "message", "string", "Optional alarm label"), new String[]{"hour", "minute"}));
        a.put(tool("set_timer", "Open the Android clock app to create a timer in seconds; user reviews the timer.", props2("seconds", "integer", "Timer duration in seconds", "message", "string", "Optional timer label"), new String[]{"seconds"}));
        a.put(tool("create_calendar_event", "Open the calendar app's event editor prefilled with title and optional start/end epoch milliseconds; user reviews and saves.", props3("title", "string", "Event title", "begin_time_ms", "integer", "Optional start time in epoch milliseconds", "end_time_ms", "integer", "Optional end time in epoch milliseconds"), new String[]{"title"}));
        a.put(tool("get_contacts", "Search contacts by name. Requires Contacts permission and user approval.", props("query", "string", "Name or partial name"), new String[]{"query"}));
        a.put(tool("read_sms", "Read recent SMS messages matching a sender/query. Requires SMS permission and user approval.", props("query", "string", "Optional sender/name/number filter"), new String[]{}));
        a.put(tool("dial_number", "Open Android dialer with a number filled in. Does not place the call automatically.", props("number", "string", "Phone number"), new String[]{"number"}));
        a.put(tool("compose_sms", "Open SMS app with recipient and message filled in. User must review and press Send.", props2("number", "string", "Phone number", "message", "string", "Message text"), new String[]{"number", "message"}));
        a.put(tool("open_settings", "Open an Android settings page such as wifi, bluetooth, display, sound, battery, apps, notifications, notification_access, location, accessibility, or general. User changes settings manually.", props("page", "string", "wifi, bluetooth, display, sound, battery, apps, notifications, notification_access, location, accessibility, or general"), new String[]{"page"}));
        a.put(tool("open_app", "Launch an installed app by its Android package name, e.g. com.android.settings.", props("package_name", "string", "Installed Android package name"), new String[]{"package_name"}));
        a.put(tool("tap_text", "Tap a visible matching on-screen label using the explicitly user-enabled Accessibility service. Never use this to bypass security, consent, or payment prompts.", props("text", "string", "Visible label to tap"), new String[]{"text"}));
        a.put(tool("get_active_notifications", "Read up to 15 currently active notifications only after the user enables Android Notification Access. Notification contents may be sensitive.", props("package_filter", "string", "Optional package-name filter"), new String[]{}));
        a.put(tool("set_volume", "Set a device audio stream volume after explicit approval. Values are normalized from 0 to 100 percent.", props2("stream", "string", "media, ring, alarm, or notification", "level", "integer", "Volume percentage from 0 to 100"), new String[]{"stream", "level"}));
        a.put(tool("request_uninstall_app", "Open Android's system uninstall confirmation for a supplied package. Android and the user decide whether uninstall is allowed.", props("package_name", "string", "Package name of the app to request uninstalling"), new String[]{"package_name"}));
        return a;
    }
    private JSONObject tool(String name, String description, JSONObject properties, String[] required) throws Exception { JSONObject fn = new JSONObject(); fn.put("name", name); fn.put("description", description); JSONObject params = new JSONObject(); params.put("type", "object"); params.put("properties", properties); JSONArray req = new JSONArray(); for (String r : required) req.put(r); params.put("required", req); fn.put("parameters", params); JSONObject wrap = new JSONObject(); wrap.put("type", "function"); wrap.put("function", fn); return wrap; }
    private JSONObject props(String name, String type, String desc) throws Exception { JSONObject p = new JSONObject(); JSONObject x = new JSONObject(); x.put("type", type); x.put("description", desc); p.put(name, x); return p; }
    private JSONObject props2(String n1, String t1, String d1, String n2, String t2, String d2) throws Exception { JSONObject p = props(n1,t1,d1); JSONObject x = new JSONObject(); x.put("type",t2); x.put("description",d2); p.put(n2,x); return p; }
    private JSONObject props3(String n1, String t1, String d1, String n2, String t2, String d2, String n3, String t3, String d3) throws Exception { JSONObject p = props2(n1,t1,d1,n2,t2,d2); JSONObject x = new JSONObject(); x.put("type",t3); x.put("description",d3); p.put(n3,x); return p; }

    private void processNextToolCall() {
        if (pendingCalls == null || pendingCallIndex >= pendingCalls.length()) { pendingCalls = null; requestAgent(); return; }
        try {
            JSONObject call = pendingCalls.getJSONObject(pendingCallIndex); JSONObject fn = call.getJSONObject("function"); String name = fn.getString("name"); JSONObject args = new JSONObject(fn.optString("arguments", "{}"));
            String summary = name + "\n\n" + args.toString(2) + "\n\nAllow Lailaba to perform this action?";
            new AlertDialog.Builder(this).setTitle("Approve device action").setMessage(summary).setNegativeButton("Reject", (d,w) -> finishToolCall(call, "Action rejected by user."))
                .setPositiveButton("Approve", (d,w) -> executor.execute(() -> { String result = executeTool(name, args); runOnUiThread(() -> finishToolCall(call, result)); })).setOnCancelListener(d -> finishToolCall(call, "Action cancelled by user.")).show();
        } catch (Exception e) { appendMessage("Error", "Invalid tool call: " + e.getMessage()); pendingCallIndex++; processNextToolCall(); }
    }
    private void finishToolCall(JSONObject call, String result) {
        try { JSONObject toolMessage = new JSONObject(); toolMessage.put("role", "tool"); toolMessage.put("tool_call_id", call.getString("id")); toolMessage.put("content", result); messages.put(toolMessage); appendMessage("Tool", result); } catch (Exception ignored) { }
        pendingCallIndex++; processNextToolCall();
    }
    private String executeTool(String name, JSONObject a) {
        try {
            switch (name) {
                case "get_device_info": return getDeviceInfo();
                case "get_battery_status": return getBatteryStatus();
                case "get_network_status": return getNetworkStatus();
                case "get_storage_status": return getStorageStatus();
                case "list_apps": return listApps(a.optString("query", ""));
                case "open_url": return openUrl(a.getString("url"));
                case "share_text": return shareText(a.getString("text"));
                case "search_web": return openUrl("https://www.google.com/search?q=" + Uri.encode(a.getString("query")));
                case "search_maps": return openMaps(a.getString("query"));
                case "open_camera": return openCamera();
                case "set_alarm": return setAlarm(a);
                case "set_timer": return setTimer(a);
                case "create_calendar_event": return createCalendarEvent(a);
                case "get_contacts": return getContacts(a.optString("query", ""));
                case "read_sms": return readSms(a.optString("query", ""));
                case "dial_number": { String n = a.getString("number").trim(); if (n.isEmpty()) return "Phone number is empty."; return launchIntent(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(n))), "Opened the dialer for " + n + ". The user must press Call.", "No dialer app is available."); }
                case "compose_sms": { String n = a.getString("number").trim(), body = a.getString("message"); if (n.isEmpty()) return "Recipient number is empty."; Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(n))); i.putExtra("sms_body", body); return launchIntent(i, "Opened the SMS composer for " + n + ". The user must review and press Send.", "No SMS app is available."); }
                case "open_settings": return openSettings(a.optString("page", "general"));
                case "open_app": return openApp(a.getString("package_name"));
                case "tap_text": { String t = a.getString("text"); boolean ok = LailabaAccessibilityService.requestTapText(t); if (!ok) { runOnUiThread(() -> toast("Accessibility service is off or the text is not currently visible.")); return "Tap was not completed. Ask the user to enable the Lailaba accessibility service and bring the target app to the foreground."; } return "Android accepted the click action for visible label: " + t; }
                case "get_active_notifications": return getActiveNotifications(a.optString("package_filter", ""));
                case "set_volume": return setVolume(a.getString("stream"), a.getInt("level"));
                case "request_uninstall_app": return requestUninstallApp(a.getString("package_name"));
                default: return "Unsupported tool: " + name;
            }
        } catch (SecurityException e) { return "Permission denied. Ask the user to grant the required Android permission in the app."; }
        catch (Exception e) { return "Tool failed: " + (e.getMessage() == null ? e.toString() : e.getMessage()); }
    }

    private String getActiveNotifications(String packageFilter) {
        if (!LailabaNotificationListener.isEnabled()) return "Notification Access is not enabled. Ask the user to open Configure notification access in Lailaba Device Agent settings and explicitly enable it, then retry.";
        StatusBarNotification[] active = LailabaNotificationListener.getActiveNotificationsSafe();
        if (active == null || active.length == 0) return "There are no active notifications available.";
        String filter = packageFilter == null ? "" : packageFilter.trim().toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(); int count = 0;
        for (StatusBarNotification sbn : active) {
            if (sbn == null || ( !filter.isEmpty() && !sbn.getPackageName().toLowerCase(Locale.ROOT).contains(filter))) continue;
            android.app.Notification n = sbn.getNotification();
            CharSequence title = n.extras == null ? null : n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE);
            CharSequence text = n.extras == null ? null : n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT);
            out.append("Package: ").append(sbn.getPackageName()).append("\nTitle: ").append(title == null ? "(no title)" : title).append("\nText: ").append(text == null ? "(no text)" : text).append("\n---\n");
            if (++count >= 15) break;
        }
        return count == 0 ? "No active notifications matched that package filter." : "Active notifications (limited to 15; contents may be private):\n" + out;
    }

    private String setVolume(String stream, int percent) {
        if (percent < 0 || percent > 100) return "Volume level must be between 0 and 100 percent.";
        int streamType;
        switch (stream == null ? "" : stream.trim().toLowerCase(Locale.ROOT)) {
            case "media": streamType = AudioManager.STREAM_MUSIC; break;
            case "ring": streamType = AudioManager.STREAM_RING; break;
            case "alarm": streamType = AudioManager.STREAM_ALARM; break;
            case "notification": streamType = AudioManager.STREAM_NOTIFICATION; break;
            default: return "Unsupported volume stream. Use media, ring, alarm, or notification.";
        }
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio == null) return "Audio service is unavailable.";
        int max = audio.getStreamMaxVolume(streamType);
        int target = Math.round(max * (percent / 100f));
        audio.setStreamVolume(streamType, target, 0);
        int actual = audio.getStreamVolume(streamType);
        return "Requested " + stream + " volume " + percent + "%. Android reports " + Math.round(actual * 100f / Math.max(1, max)) + "% (" + actual + "/" + max + "). Device policy or Do Not Disturb may affect audible output.";
    }

    private String requestUninstallApp(String packageName) {
        String pkg = packageName == null ? "" : packageName.trim();
        if (pkg.isEmpty() || pkg.equals(getPackageName())) return "Provide another app's package name; this tool will not request uninstalling Lailaba Device Agent itself.";
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + Uri.encode(pkg)));
        return launchIntent(i, "Opened Android's uninstall confirmation for " + pkg + ". The system and user must confirm; some apps cannot be removed.", "Android does not offer an uninstall confirmation for " + pkg + ". The app may be protected or unavailable.");
    }

    private String getDeviceInfo() {
        return "Manufacturer: " + Build.MANUFACTURER + "\nModel: " + Build.MODEL +
            "\nAndroid: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")" +
            "\nApp version: " + appVersionName() + "\nAccessibility bridge: " +
            (LailabaAccessibilityService.isEnabled() ? "enabled by user" : "disabled");
    }


    private String getNetworkStatus() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return "Network status unavailable.";
        Network network = cm.getActiveNetwork();
        if (network == null) return "No active network is reported.";
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) return "An active network exists, but capabilities are unavailable.";
        String transport = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi" :
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "mobile data" :
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "Ethernet" :
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) ? "Bluetooth" : "other network";
        return "Active transport: " + transport + "\nInternet capability: " + (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ? "reported" : "not reported") +
            "\nValidated internet: " + (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? "yes" : "not confirmed");
    }

    private String getStorageStatus() {
        try {
            StatFs stats = new StatFs(android.os.Environment.getDataDirectory().getAbsolutePath());
            long total = stats.getTotalBytes(), free = stats.getAvailableBytes();
            return String.format(Locale.ROOT, "Data partition total: %.2f GB\nAvailable: %.2f GB\nUsed: %.2f GB", total / 1e9, free / 1e9, (total - free) / 1e9);
        } catch (Exception e) { return "Storage status unavailable: " + e.getMessage(); }
    }

    private String openMaps(String query) {
        if (query == null || query.trim().isEmpty()) return "Provide a place or destination to search.";
        Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(query.trim()));
        return launchIntent(new Intent(Intent.ACTION_VIEW, uri), "Opened a map search for: " + query + ". Review the destination in the map app.", "No compatible maps app is available.");
    }

    private String openCamera() {
        return launchIntent(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), "Opened the camera app. The user controls capture and saving.", "No compatible camera app is available.");
    }

    private String setAlarm(JSONObject args) throws Exception {
        int hour = args.getInt("hour"), minute = args.getInt("minute");
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return "Invalid time. Hour must be 0–23 and minute 0–59.";
        Intent i = new Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hour).putExtra(AlarmClock.EXTRA_MINUTES, minute).putExtra(AlarmClock.EXTRA_SKIP_UI, false);
        String label = args.optString("message", "").trim(); if (!label.isEmpty()) i.putExtra(AlarmClock.EXTRA_MESSAGE, label);
        return launchIntent(i, String.format(Locale.ROOT, "Opened the clock app to review an alarm at %02d:%02d.", hour, minute), "No compatible clock app is available.");
    }

    private String setTimer(JSONObject args) throws Exception {
        int seconds = args.getInt("seconds");
        if (seconds < 1 || seconds > 86400) return "Timer duration must be between 1 and 86400 seconds.";
        Intent i = new Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, seconds).putExtra(AlarmClock.EXTRA_SKIP_UI, false);
        String label = args.optString("message", "").trim(); if (!label.isEmpty()) i.putExtra(AlarmClock.EXTRA_MESSAGE, label);
        return launchIntent(i, "Opened the clock app to review a timer for " + seconds + " seconds.", "No compatible clock app is available.");
    }

    private String createCalendarEvent(JSONObject args) throws Exception {
        String title = args.getString("title").trim();
        if (title.isEmpty()) return "Event title cannot be empty.";
        Intent i = new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE, title);
        long begin = args.optLong("begin_time_ms", -1), end = args.optLong("end_time_ms", -1);
        if (begin > 0) i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin);
        if (end > 0 && (begin <= 0 || end >= begin)) i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end);
        return launchIntent(i, "Opened a calendar event editor for: " + title + ". The user must review and save it.", "No compatible calendar app is available.");
    }

    private String appVersionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "unknown"; }
    }

    private String getBatteryStatus() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return "Battery status is unavailable on this device.";
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int statusCode = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        String charging = statusCode == BatteryManager.BATTERY_STATUS_CHARGING ? "charging" :
            statusCode == BatteryManager.BATTERY_STATUS_FULL ? "full" :
            statusCode == BatteryManager.BATTERY_STATUS_DISCHARGING ? "discharging" : "not charging / unknown";
        return "Battery: " + ((level >= 0 && scale > 0) ? (level * 100 / scale) + "%" : "unknown") +
            "\nState: " + charging;
    }

    private String listApps(String query) {
        Intent launcher = new Intent(Intent.ACTION_MAIN, null);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        java.util.List<android.content.pm.ResolveInfo> found = getPackageManager().queryIntentActivities(launcher, 0);
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        java.util.TreeSet<String> lines = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (android.content.pm.ResolveInfo info : found) {
            CharSequence label = info.loadLabel(getPackageManager());
            String name = label == null ? info.activityInfo.packageName : label.toString();
            if (q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q) || info.activityInfo.packageName.toLowerCase(Locale.ROOT).contains(q))
                lines.add(name + " — " + info.activityInfo.packageName);
            if (lines.size() >= 100) break;
        }
        if (lines.isEmpty()) return "No matching launchable apps were visible to Android's package-visibility query.";
        StringBuilder out = new StringBuilder();
        for (String line : lines) out.append(line).append('\n');
        return out.toString();
    }

    private String openUrl(String raw) {
        Uri uri = Uri.parse(raw == null ? "" : raw.trim());
        String scheme = uri.getScheme();
        if (uri.getHost() == null || scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http")))
            return "Rejected URL. Provide a valid http:// or https:// address with a host.";
        return launchIntent(new Intent(Intent.ACTION_VIEW, uri), "Requested Android to open: " + uri + ". Review the destination before entering information.", "No compatible browser or app is available for this URL.");
    }

    private String shareText(String text) {
        if (text == null || text.trim().isEmpty()) return "Nothing to share: text was empty.";
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        return launchIntent(Intent.createChooser(send, "Share with"), "Opened Android's share sheet. You choose the destination and confirm sharing.", "Android could not open the share sheet.");
    }

    // Runs external activities on the UI thread and returns a truthful result to the tool caller.
    private String launchIntent(Intent intent, String success, String unavailable) {
        FutureTask<String> task = new FutureTask<>(() -> {
            try {
                startActivity(intent);
                return success;
            } catch (ActivityNotFoundException e) {
                return unavailable;
            } catch (SecurityException e) {
                return "Android blocked this action: " + e.getMessage();
            } catch (Exception e) {
                return "Could not launch the requested Android interface: " + (e.getMessage() == null ? e.toString() : e.getMessage());
            }
        });
        runOnUiThread(task);
        try {
            return task.get(8, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return "Android did not confirm the launch in time. Check the device screen before retrying.";
        } catch (Exception e) {
            return "Could not complete the launch request: " + (e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private String getContacts(String query) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return "Contacts permission is not granted. Ask user to tap Allow contacts, then repeat the request.";
        StringBuilder out = new StringBuilder(); int count = 0;
        try (Cursor c = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")) {
            if (c != null) while (c.moveToNext() && count < 30) { String n = c.getString(0), number = c.getString(1); if (query.isEmpty() || n.toLowerCase().contains(query.toLowerCase()) || number.contains(query)) { out.append(n).append(" — ").append(number).append('\n'); count++; } }
        } catch (Exception e) { return "Unable to read contacts: " + e.getMessage(); }
        return count == 0 ? "No matching contacts found." : out.toString();
    }
    private String readSms(String query) {
        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return "SMS read permission is not granted. Ask user to tap Allow SMS read, then repeat the request.";
        StringBuilder out = new StringBuilder(); int count = 0;
        try (Cursor c = getContentResolver().query(Uri.parse("content://sms/inbox"), new String[]{"address", "body", "date"}, null, null, "date DESC")) {
            if (c != null) while (c.moveToNext() && count < 20) { String address = c.getString(0), body = c.getString(1); if (query.isEmpty() || address.contains(query) || body.toLowerCase().contains(query.toLowerCase())) { out.append("From ").append(address).append(": ").append(body).append('\n'); count++; } }
        } catch (Exception e) { return "Unable to read SMS: " + e.getMessage(); }
        return count == 0 ? "No matching inbox messages found." : out.toString();
    }
    private String openSettings(String page) {
        String p = page == null ? "general" : page.trim().toLowerCase(Locale.ROOT);
        String action;
        switch (p) {
            case "wifi": action = Settings.ACTION_WIFI_SETTINGS; break;
            case "bluetooth": action = Settings.ACTION_BLUETOOTH_SETTINGS; break;
            case "display": action = Settings.ACTION_DISPLAY_SETTINGS; break;
            case "sound": action = Settings.ACTION_SOUND_SETTINGS; break;
            case "battery": action = Settings.ACTION_BATTERY_SAVER_SETTINGS; break;
            case "apps": action = Settings.ACTION_APPLICATION_SETTINGS; break;
            case "notifications": action = Settings.ACTION_APP_NOTIFICATION_SETTINGS; break;
            case "notification_access": action = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS; break;
            case "location": action = Settings.ACTION_LOCATION_SOURCE_SETTINGS; break;
            case "accessibility": action = Settings.ACTION_ACCESSIBILITY_SETTINGS; break;
            default: p = "general"; action = Settings.ACTION_SETTINGS;
        }
        Intent settingsIntent = new Intent(action);
        if ("notifications".equals(p)) settingsIntent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        return launchIntent(settingsIntent, "Opened Android " + p + " settings. You must review and make changes manually.", "Android does not provide a compatible settings screen for " + p + ".");
    }

    private String openApp(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return "Provide an installed app's package name.";
        Intent i = getPackageManager().getLaunchIntentForPackage(packageName.trim());
        if (i == null) return "No launchable app found for package " + packageName + ". Android package-visibility rules may limit discovery.";
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return launchIntent(i, "Requested launch of app " + packageName + ".", "Android could not launch app " + packageName + ".");
    }
    private void appendMessage(String who, String text) {
        TextView tv = new TextView(this); tv.setText(who + "\n" + text); tv.setTextColor(who.equals("You") ? Color.WHITE : 0xFFE0E0E0); tv.setTextSize(14); tv.setPadding(12, 10, 12, 10); tv.setBackgroundColor(who.equals("You") ? 0xFF202020 : 0xFF111111);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, 8); messagesBox.addView(tv, lp); if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    @Override protected void onResume() { super.onResume(); if (status != null) updateStatus(); }
    @Override protected void onDestroy() { super.onDestroy(); executor.shutdownNow(); }

    private static final class JSONObjectSafe {
        JSONObject message(String role, String content) { JSONObject o = new JSONObject(); try { o.put("role", role); o.put("content", content); } catch (Exception ignored) {} return o; }
    }
}
