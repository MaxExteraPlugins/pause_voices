package com.exteraplugins.send_pause;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public class Main {
    private static final String TAG = "SendPause";
    private static volatile boolean loaded = false;
    private static volatile boolean enabled = true;
    private static final Set<Long> pausedIds = new HashSet<Long>();
    private static final List<XC_MethodHook.Unhook> hooks = new ArrayList<XC_MethodHook.Unhook>();
    private static final Map<String, Method> zeroArgCache = new HashMap<String, Method>();

    private static Class<?> moClass;

    // ============================================================
// lifecycle (called from Python)
// ============================================================

    private static void showPauseDialogJava(final long id, final boolean inDelay, final int account) {
        if (mainHandler == null) mainHandler = new Handler(android.os.Looper.getMainLooper());
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    // Find mo to determine title
                    Object mo = null;
                    for (int a = 0; a < 4; a++) {
                        Object smh = getSmh(a);
                        if (smh == null) continue;
                        try {
                            Field f = findField(smh.getClass(), "delayedMessages");
                            if (f == null) continue;
                            Object map = f.get(smh);
                            if (!(map instanceof Map)) continue;
                            for (Object e0 : ((Map<?, ?>) map).entrySet()) {
                                Object v = ((Map.Entry<?, ?>) e0).getValue();
                                if (!(v instanceof ArrayList)) continue;
                                for (Object cand : (ArrayList<?>) v) {
                                    Object obj = gf(cand, "obj");
                                    if (obj != null && msgId(obj) == id) {
                                        mo = obj;
                                        break;
                                    }
                                }
                                if (mo != null) break;
                            }
                            if (mo != null) break;
                        } catch (Throwable t) {}
                    }
                    if (mo == null) {
                        // Check sendingMessages
                        for (int a = 0; a < 4; a++) {
                            Object smh = getSmh(a);
                            if (smh == null) continue;
                            try {
                                Field fs = findField(smh.getClass(), "sendingMessages");
                                if (fs != null) {
                                    Object sm = fs.get(smh);
                                    if (sm instanceof Map) {
                                        for (Object e0 : ((Map<?, ?>) sm).entrySet()) {
                                            Object msg = ((Map.Entry<?, ?>) e0).getValue();
                                            if (msg != null && msgId(msg) == id) {
                                                mo = msg;
                                                break;
                                            }
                                        }
                                        if (mo != null) break;
                                    }
                                }
                            } catch (Throwable t) {}
                        }
                    }
                    android.app.Activity activity = findTopChatActivity(account);
                    if (activity == null) {
                        log("showPauseDialogJava: no activity");
                        return;
                    }
                    String title;
                    if (mo != null) {
                        if (boolOf(mo, "isVoice")) title = "Голосовое сообщение";
                        else if (boolOf(mo, "isRoundVideo")) title = "Видеосообщение";
                        else if (boolOf(mo, "isVideo")) title = "Видео";
                        else title = "Сообщение";
                    } else {
                        title = "Сообщение";
                    }
                    final String[] items = new String[]{"Отменить", "Сохранить", "Пауза", "Отправить"};
                    showCustomActionDialog(activity, title, items, new ActionDialogCallback() {
                        @Override
                        public void onChoice(String choice) {
                            if (choice.equals("Отменить")) {
                                standardCancel(id, false);
                            } else if (choice.equals("Сохранить")) {
                                standardCancel(id, true);
                            } else if (choice.equals("Пауза")) {
                                setPause(id);
                            } else if (choice.equals("Отправить")) {
                                resumeAfterDialog(id, true);
                            }
                        }

                        @Override
                        public void onDismissedWithoutChoice() {
                            resumeAfterDialog(id, inDelay);
                        }
                    });
                } catch (Throwable t) {
                    log("showPauseDialogJava: " + t);
                }
            }
        });
    }

    private static void showPauseDialogPausedJava(final long id, final int account) {
        if (mainHandler == null) mainHandler = new Handler(android.os.Looper.getMainLooper());
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    // Find mo to determine title
                    Object mo = null;
                    for (int a = 0; a < 4; a++) {
                        Object smh = getSmh(a);
                        if (smh == null) continue;
                        try {
                            Field f = findField(smh.getClass(), "delayedMessages");
                            if (f == null) continue;
                            Object map = f.get(smh);
                            if (!(map instanceof Map)) continue;
                            for (Object e0 : ((Map<?, ?>) map).entrySet()) {
                                Object v = ((Map.Entry<?, ?>) e0).getValue();
                                if (!(v instanceof ArrayList)) continue;
                                for (Object cand : (ArrayList<?>) v) {
                                    Object obj = gf(cand, "obj");
                                    if (obj != null && msgId(obj) == id) {
                                        mo = obj;
                                        break;
                                    }
                                }
                                if (mo != null) break;
                            }
                            if (mo != null) break;
                        } catch (Throwable t) {}
                    }
                    if (mo == null) {
                        for (int a = 0; a < 4; a++) {
                            Object smh = getSmh(a);
                            if (smh == null) continue;
                            try {
                                Field fs = findField(smh.getClass(), "sendingMessages");
                                if (fs != null) {
                                    Object sm = fs.get(smh);
                                    if (sm instanceof Map) {
                                        for (Object e0 : ((Map<?, ?>) sm).entrySet()) {
                                            Object msg = ((Map.Entry<?, ?>) e0).getValue();
                                            if (msg != null && msgId(msg) == id) {
                                                mo = msg;
                                                break;
                                            }
                                        }
                                        if (mo != null) break;
                                    }
                                }
                            } catch (Throwable t) {}
                        }
                    }
                    android.app.Activity activity = findTopChatActivity(account);
                    if (activity == null) {
                        log("showPauseDialogPausedJava: no activity");
                        return;
                    }
                    String title;
                    if (mo != null) {
                        if (boolOf(mo, "isVoice")) title = "Голосовое сообщение (на паузе)";
                        else if (boolOf(mo, "isRoundVideo")) title = "Видеосообщение (на паузе)";
                        else if (boolOf(mo, "isVideo")) title = "Видео (на паузе)";
                        else title = "Сообщение (на паузе)";
                    } else {
                        title = "Сообщение (на паузе)";
                    }
                    final String[] items = new String[]{"Отменить", "Сохранить", "Продолжить", "Отправить"};
                    showCustomActionDialog(activity, title, items, new ActionDialogCallback() {
                        @Override
                        public void onChoice(String choice) {
                            if (choice.equals("Отменить")) {
                                standardCancel(id, false);
                            } else if (choice.equals("Сохранить")) {
                                standardCancel(id, true);
                            } else if (choice.equals("Продолжить")) {
                                resumeAfterDialog(id, false);
                            } else if (choice.equals("Отправить")) {
                                resumeAfterDialog(id, true);
                            }
                        }

                        @Override
                        public void onDismissedWithoutChoice() {
                            resumeAfterDialog(id, false);
                        }
                    });
                } catch (Throwable t) {
                    log("showPauseDialogPausedJava: " + t);
                }
            }
        });
    }

    // ============================================================
    // custom (non-Telegram) action dialog
    // ============================================================

    private interface ActionDialogCallback {
        void onChoice(String choice);
        void onDismissedWithoutChoice();
    }

    private static void showCustomActionDialog(final android.app.Activity activity,
                                                 final String title,
                                                 final String[] items,
                                                 final ActionDialogCallback callback) {
        try {
            final android.content.Context ctx = activity;
            final float density = ctx.getResources().getDisplayMetrics().density;

            final android.app.Dialog dialog = new android.app.Dialog(ctx, android.R.style.Theme_Black_NoTitleBar);
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

            android.widget.LinearLayout root = new android.widget.LinearLayout(ctx);
            root.setOrientation(android.widget.LinearLayout.VERTICAL);
            int pad = (int) (20 * density);
            root.setPadding(pad, pad, pad, pad);

            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFF1E1E1E);
            bg.setCornerRadius(14 * density);
            root.setBackground(bg);

            android.widget.TextView titleView = new android.widget.TextView(ctx);
            titleView.setText(title);
            titleView.setTextColor(0xFFFFFFFF);
            titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 17);
            titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            root.addView(titleView);

            android.widget.TextView subtitleView = new android.widget.TextView(ctx);
            subtitleView.setText("Выберите действие:");
            subtitleView.setTextColor(0xFFAAAAAA);
            subtitleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
            android.widget.LinearLayout.LayoutParams subtitleLp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            subtitleLp.topMargin = (int) (4 * density);
            subtitleLp.bottomMargin = (int) (14 * density);
            root.addView(subtitleView, subtitleLp);

            final boolean[] choiceMade = {false};

            for (final String item : items) {
                android.widget.TextView btn = new android.widget.TextView(ctx);
                btn.setText(item);
                btn.setTextColor(0xFFFFFFFF);
                btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
                int btnPadV = (int) (12 * density);
                btn.setPadding(0, btnPadV, 0, btnPadV);
                btn.setClickable(true);
                btn.setFocusable(true);

                android.graphics.drawable.GradientDrawable itemBg = new android.graphics.drawable.GradientDrawable();
                itemBg.setColor(0x00000000);
                itemBg.setCornerRadius(8 * density);
                btn.setBackground(itemBg);

                btn.setOnClickListener(new android.view.View.OnClickListener() {
                    @Override
                    public void onClick(android.view.View v) {
                        choiceMade[0] = true;
                        try { dialog.dismiss(); } catch (Throwable t) {}
                        try { callback.onChoice(item); } catch (Throwable t) { log("showCustomActionDialog onChoice: " + t); }
                    }
                });

                root.addView(btn, new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
            }

            dialog.setContentView(root);
            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);
            dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(android.content.DialogInterface d) {
                    if (!choiceMade[0]) {
                        try { callback.onDismissedWithoutChoice(); } catch (Throwable t) {
                            log("showCustomActionDialog onDismissedWithoutChoice: " + t);
                        }
                    }
                }
            });

            android.view.Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                android.view.WindowManager.LayoutParams lp = window.getAttributes();
                lp.width = (int) (300 * density);
                lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
                window.setAttributes(lp);
            }

            dialog.show();
        } catch (Throwable t) {
            log("showCustomActionDialog: " + t);
            if (callback != null) {
                try { callback.onDismissedWithoutChoice(); } catch (Throwable t2) {}
            }
        }
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        try {
            enabled = true;
            moClass = Class.forName("org.telegram.messenger.MessageObject");
            loadPrefs();
            loadPaused();
            setupHooks();
            log("load: hooks installed");
        } catch (Throwable t) {
            log("load error: " + t);
        }
    }

    public static void unload() {
        if (!loaded) return;
        loaded = false;
        for (XC_MethodHook.Unhook h : hooks) {
            try { h.unhook(); } catch (Throwable t) {}
        }
        hooks.clear();
        synchronized (pausedIds) { pausedIds.clear(); }
        synchronized (deferred) { deferred.clear(); }
        synchronized (savedIds) { savedIds.clear(); }
        bypassCancelIds.clear();
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean isPaused(long id) {
        synchronized (pausedIds) { return pausedIds.contains(id); }
    }

    // ============================================================
    // prefs: send delay (sec) and save-canceled-to-Downloads
    // ============================================================

    private static volatile int delayPref = 0;
    private static volatile boolean savePref = false;
    private static final Object deferredLock = new Object();
    private static final Map<Long, DelayJob> deferred = new HashMap<Long, DelayJob>();
    private static final Set<Long> savedIds = new HashSet<Long>();
    private static final Map<String, Long> recentFire = new HashMap<String, Long>();
    private static Handler mainHandler;
    // Per-id bypass instead of a single global flag: a global boolean caused a race
    // where a standardCancel() on one message could momentarily suppress the cancel
    // hook for a totally different message being canceled at the same time.
    private static final Set<Long> bypassCancelIds = java.util.Collections.synchronizedSet(new HashSet<Long>());
    private static final Map<Integer, android.app.Activity> chatActivityMap = new HashMap<Integer, android.app.Activity>();

    private static class DelayJob {
        Object self;
        Object[] args;
        int acc;
        long id;
        int kind;
        DelayJob(Object s, Object[] a, int account, long mid, int k) {
            self = s; args = a; acc = account; id = mid; kind = k;
        }
    }

    public static void setPrefs(long delaySeconds, boolean saveCanceled) {
        delayPref = delaySeconds > 0 ? (int) Math.min(delaySeconds, 120L) : 0;
        savePref = saveCanceled;
        persistPrefs();
        log("setPrefs: delay=" + delayPref + " save=" + savePref);
    }

    private static void persistPrefs() {
        try {
            android.content.SharedPreferences p = prefs();
            if (p == null) return;
            p.edit().putInt("delay", delayPref).putBoolean("save", savePref).apply();
        } catch (Throwable t) {}
    }

    private static void loadPrefs() {
        try {
            android.content.SharedPreferences p = prefs();
            if (p == null) return;
            delayPref = p.getInt("delay", 0);
            savePref = p.getBoolean("save", false);
            log("loadPrefs: delay=" + delayPref + " save=" + savePref);
        } catch (Throwable t) {
            log("loadPrefs: " + t);
        }
    }

    private static android.content.SharedPreferences prefs() {
        try {
            Context ctx = appContext();
            if (ctx == null) return null;
            return ctx.getSharedPreferences("send_pause_prefs", 0);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void persistPaused() {
        try {
            android.content.SharedPreferences p = prefs();
            if (p == null) return;
            StringBuilder sb = new StringBuilder();
            synchronized (pausedIds) {
                for (long id : pausedIds) {
                    if (sb.length() > 0) sb.append(',');
                    sb.append(id);
                }
            }
            p.edit().putString("paused_ids", sb.toString()).apply();
        } catch (Throwable t) {}
    }

    private static void loadPaused() {
        try {
            android.content.SharedPreferences p = prefs();
            if (p == null) return;
            String raw = p.getString("paused_ids", "");
            if (raw == null || raw.isEmpty()) return;
            int n = 0;
            synchronized (pausedIds) {
                for (String part : raw.split(",")) {
                    try {
                        pausedIds.add(Long.parseLong(part.trim()));
                        n++;
                    } catch (Throwable t) {}
                }
            }
            log("loadPaused: restored " + n);
        } catch (Throwable t) {
            log("loadPaused: " + t);
        }
    }

    private static void hookRetryBlock() {
        try {
            Class<?> cls = Class.forName("org.telegram.messenger.SendMessagesHelper");
            int hooked = 0;
            for (Method m : cls.getDeclaredMethods()) {
                if (!m.getName().equals("retrySendMessage")) continue;
                if (m.getParameterTypes().length == 0) continue;
                if (!m.getParameterTypes()[0].isAssignableFrom(moClass)) continue;
                XC_MethodHook.Unhook h = XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    public void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (!enabled) return;
                            Object mo = param.args[0];
                            long id = msgId(mo);
                            synchronized (pausedIds) {
                                if (pausedIds.contains(id)) {
                                    param.setResult(Boolean.FALSE);
                                    log("retryBlock: paused id=" + id + " -> skip auto-resend");
                                }
                            }
                        } catch (Throwable t) {}
                    }
                });
                if (h != null) { hooks.add(h); hooked++; }
            }
            log("hookRetryBlock: hooked " + hooked);
        } catch (Throwable t) {
            log("hookRetryBlock: " + t);
        }
    }

    private static void hookSendBlock() {
        try {
            Class<?> cls = Class.forName("org.telegram.messenger.SendMessagesHelper");
            int hooked = 0;
            for (Method m : cls.getDeclaredMethods()) {
                if (!m.getName().equals("sendMessage")) continue;
                if (m.getParameterTypes().length == 0) continue;
                if (!m.getParameterTypes()[0].isAssignableFrom(moClass)) continue;
                XC_MethodHook.Unhook h = XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    public void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (!enabled) return;
                            Object mo = param.args[0];
                            long id = msgId(mo);
                            synchronized (pausedIds) {
                                if (pausedIds.contains(id)) {
                                    param.setResult(Boolean.FALSE);
                                    log("sendBlock: paused id=" + id + " -> skip auto-send");
                                }
                            }
                        } catch (Throwable t) {}
                    }
                });
                if (h != null) { hooks.add(h); hooked++; }
            }
            log("hookSendBlock: hooked " + hooked);
        } catch (Throwable t) {
            log("hookSendBlock: " + t);
        }
    }

    private static void hookUnsentBlock() {
        try {
            Class<?> cls = Class.forName("org.telegram.messenger.SendMessagesHelper");
            int hooked = 0;
            for (Method m : cls.getDeclaredMethods()) {
                String mn = m.getName();
                if (!mn.equals("sendMessagesInternal") && !mn.equals("didGetUnsentMessages")) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length == 0) continue;
                if (!ArrayList.class.isAssignableFrom(pt[0])) continue;
                XC_MethodHook.Unhook h = XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    public void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (!enabled) return;
                            Object listObj = param.args[0];
                            if (!(listObj instanceof ArrayList)) return;
                            ArrayList<?> list = (ArrayList<?>) listObj;
                            boolean removed = false;
                            for (int i = list.size() - 1; i >= 0; i--) {
                                Object mo = list.get(i);
                                long id = msgId(mo);
                                synchronized (pausedIds) {
                                    if (pausedIds.contains(id)) {
                                        list.remove(i);
                                        removed = true;
                                        log("unsentBlock: id=" + id + " removed from list");
                                    }
                                }
                            }
                            if (removed && list.isEmpty()) {
                                param.setResult(null);
                                log("unsentBlock: list emptied -> skip");
                            }
                        } catch (Throwable t) {}
                    }
                });
                if (h != null) { hooks.add(h); hooked++; log("hookUnsentBlock: hooked " + mn); }
            }
            log("hookUnsentBlock: total " + hooked);
        } catch (Throwable t) {
            log("hookUnsentBlock: " + t);
        }
    }

    private static void hookChatActivityTracking() {
        try {
            Class<?> chatActivityClass = Class.forName("org.telegram.ui.ChatActivity");
            // Hook onResume
            Method onResume = findDeclared(chatActivityClass, "onResume", new Class<?>[0]);
            if (onResume != null) {
                XC_MethodHook.Unhook h = XposedBridge.hookMethod(onResume, new XC_MethodHook() {
                    @Override
                    public void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Object activity = param.thisObject;
                            if (activity == null) return;
                            int acc = 0;
                            Method getAccount = findMethod(activity.getClass(), "getCurrentAccount", new Class<?>[0]);
                            if (getAccount != null) {
                                Object accObj = getAccount.invoke(activity);
                                if (accObj instanceof Integer) acc = (Integer) accObj;
                            }
                            synchronized (chatActivityMap) {
                                chatActivityMap.put(acc, (android.app.Activity) activity);
                            }
                            log("ChatActivity onResume: acc=" + acc);
                        } catch (Throwable t) {}
                    }
                });
                if (h != null) hooks.add(h);
            }
            // Hook onPause
            Method onPause = findDeclared(chatActivityClass, "onPause", new Class<?>[0]);
            if (onPause != null) {
                XC_MethodHook.Unhook h = XposedBridge.hookMethod(onPause, new XC_MethodHook() {
                    @Override
                    public void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Object activity = param.thisObject;
                            if (activity == null) return;
                            int acc = 0;
                            Method getAccount = findMethod(activity.getClass(), "getCurrentAccount", new Class<?>[0]);
                            if (getAccount != null) {
                                Object accObj = getAccount.invoke(activity);
                                if (accObj instanceof Integer) acc = (Integer) accObj;
                            }
                            synchronized (chatActivityMap) {
                                android.app.Activity stored = chatActivityMap.get(acc);
                                if (stored == activity) {
                                    chatActivityMap.remove(acc);
                                }
                            }
                        } catch (Throwable t) {}
                    }
                });
                if (h != null) hooks.add(h);
            }
            log("hookChatActivityTracking: installed");
            // Scan existing activities for already-resumed ChatActivity
            scanExistingChatActivities();
        } catch (Throwable t) {
            log("hookChatActivityTracking: " + t);
        }
    }

    private static void scanExistingChatActivities() {
        try {
            log("scanExistingChatActivities: start");
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentThread = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentThread.setAccessible(true);
            Object thread = currentThread.invoke(null);
            if (thread == null) {
                log("scanExistingChatActivities: thread=null");
                return;
            }
            Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            Object activitiesObj = activitiesField.get(thread);
            if (activitiesObj instanceof Map) {
                Map<?, ?> activities = (Map<?, ?>) activitiesObj;
                log("scanExistingChatActivities: activities size=" + activities.size());
                for (Object record : activities.values()) {
                    try {
                        Field activityField = record.getClass().getDeclaredField("activity");
                        activityField.setAccessible(true);
                        Object activity = activityField.get(record);
                        if (activity instanceof android.app.Activity) {
                            String className = activity.getClass().getName();
                            log("scanExistingChatActivities: Activity class=" + className);
                            // Store LaunchActivity (main activity in single-activity arch)
                            if (className.contains("LaunchActivity")) {
                                int acc = 0;
                                Method getAccount = findMethod(activity.getClass(), "getCurrentAccount", new Class<?>[0]);
                                if (getAccount != null) {
                                    Object accObj = getAccount.invoke(activity);
                                    if (accObj instanceof Integer) acc = (Integer) accObj;
                                }
                                synchronized (chatActivityMap) {
                                    chatActivityMap.put(acc, (android.app.Activity) activity);
                                }
                                log("scanExistingChatActivities: stored LaunchActivity acc=" + acc);
                            }
                        }
                    } catch (Throwable t) {}
                }
            } else {
                log("scanExistingChatActivities: activities not Map, class=" + (activitiesObj == null ? "null" : activitiesObj.getClass().getName()));
            }
        } catch (Throwable t) {
            log("scanExistingChatActivities: " + t);
        }
    }

    private static android.app.Activity findTopChatActivity(int account) {
        // Try map first
        android.app.Activity act = getChatActivity(account);
        if (act != null) return act;
        // Scan ActivityThread on-demand — return any visible Activity (LaunchActivity works)
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentThread = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentThread.setAccessible(true);
            Object thread = currentThread.invoke(null);
            if (thread == null) return null;
            Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            Object activitiesObj = activitiesField.get(thread);
            if (activitiesObj instanceof Map) {
                Map<?, ?> activities = (Map<?, ?>) activitiesObj;
                // Prefer LaunchActivity (main activity in single-activity arch)
                android.app.Activity launchActivity = null;
                for (Object record : activities.values()) {
                    try {
                        Field activityField = record.getClass().getDeclaredField("activity");
                        activityField.setAccessible(true);
                        Object activity = activityField.get(record);
                        if (activity instanceof android.app.Activity) {
                            String className = activity.getClass().getName();
                            if (className.contains("LaunchActivity")) {
                                synchronized (chatActivityMap) {
                                    chatActivityMap.put(account, (android.app.Activity) activity);
                                }
                                log("findTopChatActivity: found LaunchActivity");
                                return (android.app.Activity) activity;
                            }
                            if (launchActivity == null) {
                                launchActivity = (android.app.Activity) activity;
                            }
                        }
                    } catch (Throwable t) {}
                }
                if (launchActivity != null) {
                    synchronized (chatActivityMap) {
                        chatActivityMap.put(account, launchActivity);
                    }
                    log("findTopChatActivity: using fallback Activity " + launchActivity.getClass().getName());
                    return launchActivity;
                }
            }
        } catch (Throwable t) {
            log("findTopChatActivity: " + t);
        }
        return null;
    }

    private static android.app.Activity getChatActivity(int account) {
        synchronized (chatActivityMap) {
            return chatActivityMap.get(account);
        }
    }

    // ============================================================
    // diagnostics (read from Python settings UI)
    // ============================================================

    private static final StringBuilder diag = new StringBuilder();
    private static final int DIAG_MAX = 60;

    private static void diag(String s) {
        synchronized (diag) {
            diag.append(System.currentTimeMillis() % 100000L).append(" ").append(s).append('\n');
            while (diag.length() > 6000) diag.delete(0, diag.indexOf("\n") + 1);
        }
    }

    public static String status() {
        synchronized (diag) {
            return diag.toString();
        }
    }

    public static int hookCount() {
        synchronized (hooks) { return hooks.size(); }
    }

    // ============================================================
    // hooks
    // ============================================================

    private static void setupHooks() {
        hookCancel();
        hookCellTap();
        hookCellDraw();
        hookDeleteMessages();
        hookNotificationCenter();
        hookUploadStart();
        hookRetryBlock();
        hookSendBlock();
        hookUnsentBlock();
        hookChatActivityTracking();
    }

    private static void hookCancel() {
        try {
            Class<?> cls = Class.forName("org.telegram.messenger.SendMessagesHelper");
            Method m1 = findDeclared(cls, "cancelSendingMessage", new Class<?>[]{moClass});
            Method m2 = findDeclared(cls, "cancelSendingMessage", new Class<?>[]{ArrayList.class});
            if (m1 == null && m2 == null) {
                log("hookCancel: no overloads found");
                return;
            }
            if (m1 != null) {
                try {
                    XC_MethodHook.Unhook h = XposedBridge.hookMethod(m1, new XC_MethodHook() {
                        @Override
                        public void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!enabled) return;
                                Object mo = param.args[0];
                                if (mo == null) return;
                                log("cancelHook(MO) body");
                                handleCancel(mo, param);
                            } catch (Throwable t) {
                                log("cancelHook(MO): " + t);
                            }
                        }
                    });
                    if (h != null) { hooks.add(h); log("hookCancel: MessageObject overload hooked"); }
                } catch (Throwable t) {
                    log("hookCancel: MO attach: " + t);
                }
            }
            if (m2 != null) {
                try {
                    XC_MethodHook.Unhook h = XposedBridge.hookMethod(m2, new XC_MethodHook() {
                        @Override
                        public void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!enabled) return;
                                Object l = param.args[0];
                                if (!(l instanceof ArrayList)) return;
                                ArrayList<?> list = (ArrayList<?>) l;
                                if (list.size() != 1) return;
                                log("cancelHook(AL) body size=" + list.size());
                                Object mo = list.get(0);
                                if (mo == null) return;
                                handleCancel(mo, param);
                            } catch (Throwable t) {
                                log("cancelHook(AL): " + t);
                            }
                        }
                    });
                    if (h != null) { hooks.add(h); log("hookCancel: ArrayList overload hooked"); }
                } catch (Throwable t) {
                    log("hookCancel: AL attach: " + t);
                }
            }
        } catch (Throwable t) {
            log("hookCancel: " + t);
        }
    }

    private static void handleCancel(Object mo, XC_MethodHook.MethodHookParam param) {
        long id = msgId(mo);
        if (id == 0) return;
        if (bypassCancelIds.contains(id)) {
            return; // allow original cancelSendingMessage for this specific id
        }
        if (!isPausable(mo)) {
            // Не голос/кружок/видео — не наш случай, отдаём управление
            // оригинальному cancelSendingMessage (не трогаем param.result).
            log("cancel skip (not pausable) id=" + id);
            return;
        }
        log("cancel fired id=" + id + " sending=" + boolOf(mo, "isSending")
                + " voice=" + boolOf(mo, "isVoice")
                + " round=" + boolOf(mo, "isRoundVideo")
                + " video=" + boolOf(mo, "isVideo")
                + " out=" + boolOf(mo, "isOut"));
        // Check if already paused
        boolean alreadyPaused;
        synchronized (pausedIds) {
            alreadyPaused = pausedIds.contains(id);
        }
        // Check if in delay phase. Note: we deliberately do NOT remove the job from
        // `deferred` here. If we removed it and the user then picks "Пауза" in the
        // dialog, the DelayJob (which holds the only reference to the FileLoader
        // self/args needed to resume the *original* uploadFile call) would be lost
        // forever - later "resume" would fall back to sendMessage/retrySendMessage,
        // which is not equivalent. Leaving the job in `deferred` lets fireDeferred()
        // (called explicitly on resume, or from the original scheduled timer) decide
        // what to do, and fireDeferred() now checks pausedIds before firing.
        boolean inDelay;
        synchronized (deferred) {
            inDelay = deferred.containsKey(id);
        }
        if (inDelay) {
            log("cancel during delay id=" + id);
            if (isPausable(mo)) {
                showPauseDialogJava(id, true, accountOf(mo));
            }
            param.setResult(null);
            return;
        }
        if (alreadyPaused) {
            log("cancel on paused id=" + id);
            showPauseDialogPausedJava(id, accountOf(mo));
            param.setResult(null);
            return;
        }
        // Normal sending - cancel upload and remove from delayedMessages
        cancelUploadAndRemoveFromDelayed(mo, id);
        // Show dialog with 4 options ONLY for pausable messages
        if (isPausable(mo)) {
            showPauseDialogJava(id, false, accountOf(mo));
        }
        param.setResult(null);
    }

    private static void cancelUploadAndRemoveFromDelayed(Object mo, long id) {
        try {
            int acc = accountOf(mo);
            Object smh = getSmh(acc);
            if (smh == null) return;
            Field f = findField(smh.getClass(), "delayedMessages");
            if (f == null) return;
            Object map = f.get(smh);
            if (!(map instanceof Map)) return;
            Map<?, ?> m = (Map<?, ?>) map;
            for (Object e0 : m.entrySet()) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) e0;
                Object v = e.getValue();
                if (!(v instanceof ArrayList)) continue;
                ArrayList<?> list = (ArrayList<?>) v;
                for (int i = list.size() - 1; i >= 0; i--) {
                    Object cand = list.get(i);
                    Object obj = gf(cand, "obj");
                    if (obj == null) continue;
                    if (gf(cand, "messageObjects") != null) continue;
                    if (msgId(obj) != id) continue;
                    // Cancel uploads in delayedMessage
                    Object uploads = gf(cand, "uploads");
                    if (uploads instanceof Map) {
                        for (Object op : ((Map<?, ?>) uploads).values()) {
                            try {
                                Method cm = findMethod(op.getClass(), "cancel", new Class<?>[0]);
                                if (cm != null) cm.invoke(op);
                            } catch (Throwable t) {}
                        }
                    }
                    // Cancel file upload
                    Object key = e.getKey();
                    if (key instanceof String) {
                        String loc = (String) key;
                        Object fl = callGetInstance("org.telegram.messenger.FileLoader", acc);
                        if (fl != null) {
                            Method cm = findMethod(fl.getClass(), "cancelFileUpload", new Class<?>[]{String.class, boolean.class});
                            if (cm != null) cm.invoke(fl, loc, Boolean.FALSE);
                        }
                    }
                    list.remove(i);
                    if (list.isEmpty()) {
                        try { m.remove(key); } catch (Throwable t) {}
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            log("cancelUploadAndRemoveFromDelayed: " + t);
        }
    }

    private static void standardCancel(Object mo, long id, boolean save) {
        try {
            bypassCancelIds.add(id);
            // Bug fix: this id must never linger in pausedIds/deferred after a real
            // cancel/save, otherwise it leaks forever (pausedIds) or can wrongly
            // auto-fire later (deferred) for what the user just canceled.
            boolean wasPaused;
            synchronized (pausedIds) { wasPaused = pausedIds.remove(id); }
            if (wasPaused) persistPaused();
            synchronized (deferred) { deferred.remove(id); }
            if (save) {
                saveToDownloads(mo);
            }
            // Call original cancelSendingMessage
            Object smh = getSmh(accountOf(mo));
            if (smh != null) {
                Method m = findMethod(smh.getClass(), "cancelSendingMessage", new Class<?>[]{moClass});
                if (m != null) m.invoke(smh, mo);
            }
            log("standardCancel: id=" + id + " save=" + save);
        } catch (Throwable t) {
            log("standardCancel: " + t);
        } finally {
            bypassCancelIds.remove(id);
        }
    }

    private static void standardCancel(long id, boolean save) {
        // Find mo by id
        for (int a = 0; a < 4; a++) {
            Object smh = getSmh(a);
            if (smh == null) continue;
            try {
                Field f = findField(smh.getClass(), "delayedMessages");
                if (f == null) continue;
                Object map = f.get(smh);
                if (!(map instanceof Map)) continue;
                for (Object e0 : ((Map<?, ?>) map).entrySet()) {
                    Object v = ((Map.Entry<?, ?>) e0).getValue();
                    if (!(v instanceof ArrayList)) continue;
                    for (Object cand : (ArrayList<?>) v) {
                        Object obj = gf(cand, "obj");
                        if (obj != null && msgId(obj) == id) {
                            standardCancel(obj, id, save);
                            return;
                        }
                    }
                }
                // Check sendingMessages
                Field fs = findField(smh.getClass(), "sendingMessages");
                if (fs != null) {
                    Object sm = fs.get(smh);
                    if (sm instanceof Map) {
                        for (Object e0 : ((Map<?, ?>) sm).entrySet()) {
                            Object msg = ((Map.Entry<?, ?>) e0).getValue();
                            if (msg != null && msgId(msg) == id) {
                                standardCancel(msg, id, save);
                                return;
                            }
                        }
                    }
                }
            } catch (Throwable t) {}
        }
        // Message object could not be located - still clear any lingering pause/defer
        // state for this id so it doesn't get stuck or fire unexpectedly later.
        boolean wasPaused;
        synchronized (pausedIds) { wasPaused = pausedIds.remove(id); }
        if (wasPaused) persistPaused();
        synchronized (deferred) { deferred.remove(id); }
        log("standardCancel: id=" + id + " save=" + save + " (no mo)");
    }

    private static void setPause(Object mo, long id) {
        synchronized (pausedIds) {
            pausedIds.add(id);
        }
        persistPaused();
        postUpdate(mo);
        log("setPause: id=" + id);
    }

    private static void setPause(long id) {
        // Find mo by id
        for (int a = 0; a < 4; a++) {
            Object smh = getSmh(a);
            if (smh == null) continue;
            try {
                Field f = findField(smh.getClass(), "delayedMessages");
                if (f == null) continue;
                Object map = f.get(smh);
                if (!(map instanceof Map)) continue;
                for (Object e0 : ((Map<?, ?>) map).entrySet()) {
                    Object v = ((Map.Entry<?, ?>) e0).getValue();
                    if (!(v instanceof ArrayList)) continue;
                    for (Object cand : (ArrayList<?>) v) {
                        Object obj = gf(cand, "obj");
                        if (obj != null && msgId(obj) == id) {
                            setPause(obj, id);
                            return;
                        }
                    }
                }
                // Check sendingMessages
                Field fs = findField(smh.getClass(), "sendingMessages");
                if (fs != null) {
                    Object sm = fs.get(smh);
                    if (sm instanceof Map) {
                        for (Object e0 : ((Map<?, ?>) sm).entrySet()) {
                            Object msg = ((Map.Entry<?, ?>) e0).getValue();
                            if (msg != null && msgId(msg) == id) {
                                setPause(msg, id);
                                return;
                            }
                        }
                    }
                }
            } catch (Throwable t) {}
        }
        // Fallback: just add to pausedIds without postUpdate
        synchronized (pausedIds) { pausedIds.add(id); }
        persistPaused();
        log("setPause: id=" + id + " (no mo)");
    }

    private static void resumeAfterDialog(Object mo, long id, boolean ignoreDelay) {
        // Always clear the paused flag first, or upload/send hooks will keep blocking it.
        boolean wasPaused;
        synchronized (pausedIds) {
            wasPaused = pausedIds.remove(id);
        }
        if (wasPaused) {
            persistPaused();
        }
        // Bug fix: this used to only check the pre-send delay queue for "Отправить"
        // (ignoreDelay=true) and call resume(mo) directly for "Продолжить"
        // (ignoreDelay=false). But a message paused while still in the delay queue
        // (via handleCancel's inDelay branch) only has its DelayJob in `deferred` -
        // it never reached uploadFile/sendMessage. Calling resume(mo) for it bypassed
        // the queue and invoked sendMessage/retrySendMessage on a message whose
        // upload was never started, which is not equivalent and could send incorrectly
        // or silently fail. Always try the delay queue first regardless of which
        // button triggered the resume; only fall back to resume() for messages whose
        // upload had already started (true "late pause").
        //
        // If the message was actually paused (wasPaused), resuming it should behave
        // like a fresh send and go through the delay again (requeueDeferredIfQueued),
        // giving the user another chance to hit the X. If it wasn't paused - this is
        // the dialog shown right at cancel time, before any pause happened - fire
        // immediately as before (fireDeferredIfQueued).
        boolean firedFromQueue = wasPaused ? requeueDeferredIfQueued(id) : fireDeferredIfQueued(id);
        if (!firedFromQueue) {
            resume(mo);
        }
        if (wasPaused) {
            postUpdate(mo);
        }
        log("resumeAfterDialog: id=" + id + " ignoreDelay=" + ignoreDelay + " wasPaused=" + wasPaused
                + " firedFromQueue=" + firedFromQueue);
    }

    private static void resumeAfterDialog(long id, boolean ignoreDelay) {
        // Find mo by id
        for (int a = 0; a < 4; a++) {
            Object smh = getSmh(a);
            if (smh == null) continue;
            try {
                Field f = findField(smh.getClass(), "delayedMessages");
                if (f == null) continue;
                Object map = f.get(smh);
                if (!(map instanceof Map)) continue;
                for (Object e0 : ((Map<?, ?>) map).entrySet()) {
                    Object v = ((Map.Entry<?, ?>) e0).getValue();
                    if (!(v instanceof ArrayList)) continue;
                    for (Object cand : (ArrayList<?>) v) {
                        Object obj = gf(cand, "obj");
                        if (obj != null && msgId(obj) == id) {
                            resumeAfterDialog(obj, id, ignoreDelay);
                            return;
                        }
                    }
                }
                // Check sendingMessages
                Field fs = findField(smh.getClass(), "sendingMessages");
                if (fs != null) {
                    Object sm = fs.get(smh);
                    if (sm instanceof Map) {
                        for (Object e0 : ((Map<?, ?>) sm).entrySet()) {
                            Object msg = ((Map.Entry<?, ?>) e0).getValue();
                            if (msg != null && msgId(msg) == id) {
                                resumeAfterDialog(msg, id, ignoreDelay);
                                return;
                            }
                        }
                    }
                }
            } catch (Throwable t) {}
        }
        // Fallback: message not found in either queue. Still clear the paused flag
        // (otherwise it stays stuck forever) and try both resume paths.
        boolean wasPaused;
        synchronized (pausedIds) {
            wasPaused = pausedIds.remove(id);
        }
        if (wasPaused) {
            persistPaused();
        }
        // Same fix as the mo-present overload above: always try the delay queue,
        // regardless of ignoreDelay, so a job paused during the delay phase isn't
        // silently dropped when mo couldn't be located here either. And same
        // requeue-on-resume-from-pause behavior as above: if it was actually
        // paused, re-arm the delay instead of firing immediately.
        if (wasPaused) {
            requeueDeferredIfQueued(id);
        } else {
            fireDeferredIfQueued(id);
        }
        log("resumeAfterDialog: id=" + id + " ignoreDelay=" + ignoreDelay + " wasPaused=" + wasPaused + " (no mo)");
    }
    // late pause: upload finished, network send already in progress
    // ============================================================

    private static boolean latePause(long id, Object mo) {
        try {
            Object smh = getSmh(accountOf(mo));
            if (smh == null) { log("latePause: no smh"); return false; }
            Object owner = gf(mo, "messageOwner");
            boolean scheduled = owner != null && boolField(owner, "scheduled");
            Field f = findField(smh.getClass(), "sendingMessages");
            if (f == null) { log("latePause: no sendingMessages field"); return false; }
            Object map = f.get(smh);
            Object sendingMsg = null;
            if (map instanceof Map) {
                sendingMsg = ((Map<?, ?>) map).get(Integer.valueOf((int) id));
                if (sendingMsg == null) {
                    for (Object e0 : ((Map<?, ?>) map).entrySet()) {
                        Map.Entry<?, ?> e = (Map.Entry<?, ?>) e0;
                        Object v = e.getValue();
                        if (v != null && msgId(v) == id) { sendingMsg = v; break; }
                    }
                }
            } else {
                log("latePause: map type=" + (map == null ? "null" : map.getClass().getName()));
            }
            if (sendingMsg == null) { log("latePause: not in sendingMessages"); return false; }
            int reqId = intField(sendingMsg, "reqId");
            boolean cancelled = false;
            if (reqId != 0) {
                Method gcm = findMethod(smh.getClass(), "getConnectionsManager", new Class<?>[0]);
                if (gcm == null) {
                    log("latePause: no getConnectionsManager");
                } else {
                    Object cm = gcm.invoke(smh);
                    if (cm != null) {
                        Method cancel = findMethod(cm.getClass(), "cancelRequest", new Class<?>[]{int.class, boolean.class});
                        if (cancel != null) {
                            cancel.invoke(cm, Integer.valueOf(reqId), Boolean.TRUE);
                            cancelled = true;
                        } else {
                            log("latePause: no cancelRequest");
                        }
                    } else {
                        log("latePause: cm null");
                    }
                }
            } else {
                log("latePause: reqId=0 (no active request)");
            }
            synchronized (pausedIds) {
                if (!pausedIds.contains(id)) pausedIds.add(id);
            }
            persistPaused();
            log("latePause: OK id=" + id + " reqId=" + reqId + " cancel=" + cancelled);
            postUpdate(mo);
            return true;
        } catch (Throwable t) {
            log("latePause: " + t);
            return false;
        }
    }

    private static void hookCellTap() {
        try {
            Class<?> cls = Class.forName("org.telegram.ui.Cells.ChatMessageCell");
            Method[] methods = cls.getDeclaredMethods();
            int hooked = 0;
            for (Method m : methods) {
                String n = m.getName();
                if (!"didPressButton".equals(n) && !"didPressedButton".equals(n)) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length < 1 || pt.length > 2 || pt[0] != boolean.class) continue;
                try { m.setAccessible(true); } catch (Throwable t) {}
                try {
                    XC_MethodHook.Unhook h = XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        public void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!enabled) return;
                                Object cell = param.thisObject;
                                Object mo = getMessageObject(cell);
                                if (mo == null) return;
                                long id = msgId(mo);
                                boolean wasPaused;
                                synchronized (pausedIds) {
                                    wasPaused = pausedIds.remove(id);
                                }
                                if (wasPaused) {
                                    persistPaused();
                                    log("tap resume id=" + id);
                                    // If the message was paused while still in the pre-send
                                    // delay queue, its DelayJob is still sitting in `deferred`
                                    // (see handleCancel/fireDeferred). Resuming from a pause
                                    // should behave like a fresh send: re-arm the delay timer
                                    // (requeueDeferredIfQueued) instead of firing immediately,
                                    // so the user gets another window to press the X. Only
                                    // fall back to resume()'s sendMessage/retrySendMessage path
                                    // if the job already left `deferred` (i.e. it was paused
                                    // after the upload had already started - a "late" pause,
                                    // which has no upload phase left to delay).
                                    boolean firedFromQueue = requeueDeferredIfQueued(id);
                                    if (!firedFromQueue) {
                                        resume(mo);
                                    }
                                    param.setResult(null);
                                    invalidateCell(cell);
                                }
                            } catch (Throwable t2) {}
                        }
                    });
                    if (h != null) { hooks.add(h); hooked++; }
                } catch (Throwable t2) {}
            }
            log("hookCellTap: hooked " + hooked);
        } catch (Throwable t) {
            log("hookCellTap: " + t);
        }
    }

    private static void hookCellDraw() {
        try {
            Class<?> cls = Class.forName("org.telegram.ui.Cells.ChatMessageCell");
            Method m = findDeclared(cls, "onDraw", new Class<?>[]{Canvas.class});
            if (m == null) { log("hookCellDraw: onDraw not found"); return; }
            XC_MethodHook.Unhook h = XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                public void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object cell = param.thisObject;
                        Object mo = getMessageObject(cell);
                        if (mo == null) return;
                        long id = msgId(mo);
                        boolean paused;
                        synchronized (pausedIds) { paused = pausedIds.contains(id); }
                        if (!paused) return;
                        drawOverlay(cell, (Canvas) param.args[0]);
                    } catch (Throwable t) {}
                }
            });
            if (h != null) { hooks.add(h); log("hookCellDraw: hooked"); }
        } catch (Throwable t) {
            log("hookCellDraw: " + t);
        }
    }

    private static void hookDeleteMessages() {
        try {
            Class<?> cls = Class.forName("org.telegram.messenger.MessagesController");
            Method[] ms = cls.getDeclaredMethods();
            int hooked = 0;
            for (Method m : ms) {
                if (!"deleteMessages".equals(m.getName())) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length < 2 || pt[0] != ArrayList.class) continue;
                try { m.setAccessible(true); } catch (Throwable t) {}
                try {
                    XC_MethodHook.Unhook h = XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        public void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!enabled) return;
                                Object l = param.args[0];
                                if (!(l instanceof ArrayList)) return;
                                ArrayList<?> ids = (ArrayList<?>) l;
                                boolean hasLocal = false;
                                long paused = -1;
                                StringBuilder sb = null;
                                synchronized (pausedIds) {
                                    for (Object o : ids) {
                                        if (!(o instanceof Number)) continue;
                                        long v = ((Number) o).longValue();
                                        if (v < 0) hasLocal = true;
                                        if (pausedIds.contains(v)) paused = v;
                                        if (sb == null) sb = new StringBuilder("[").append(v);
                                        else sb.append(",").append(v);
                                    }
                                }
                                String idsStr = sb == null ? "?" : (sb.append("]").toString());
                                if (paused >= 0) {
                                    param.setResult(null);
                                    log("del: VETO deleteMessages ids=" + idsStr + " (paused " + paused + ") caller=" + param.method.getName());
                                } else if (hasLocal) {
                                    log("del: deleteMessages ids=" + idsStr);
                                }
                            } catch (Throwable t) {}
                        }
                    });
                    if (h != null) { hooks.add(h); hooked++; }
                } catch (Throwable t) {}
            }
            log("hookDeleteMessages: hooked " + hooked);
        } catch (Throwable t) {
            log("hookDeleteMessages: " + t);
        }
    }

    private static final String[] NC_WATCH = {
            "messagesDeleted", "messageSendError", "sendingMessagesChanged",
            "updateMessageMedia", "replaceMessagesObjects", "fileUploadFailed",
            "messageReceivedByServer"
    };

    private static final Map<String, Integer> ncIds = new HashMap<String, Integer>();

    private static void hookNotificationCenter() {
        try {
            Class<?> nc = Class.forName("org.telegram.messenger.NotificationCenter");
            for (String n : NC_WATCH) {
                try {
                    Field f = nc.getField(n);
                    ncIds.put(n, Integer.valueOf(f.getInt(null)));
                } catch (Throwable t) {}
            }
            log("ncWatch: ids=" + ncIds.size() + "/" + NC_WATCH.length);
            Method[] ms = nc.getDeclaredMethods();
            int hooked = 0;
            for (Method m : ms) {
                if (!"postNotificationName".equals(m.getName())) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length < 2 || pt[0] != int.class) continue;
                try { m.setAccessible(true); } catch (Throwable t) {}
                try {
                    XC_MethodHook.Unhook h = XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        public void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!enabled) return;
                                int id = ((Number) param.args[0]).intValue();
                                String name = null;
                                for (Map.Entry<String, Integer> e : ncIds.entrySet()) {
                                    if (e.getValue().intValue() == id) { name = e.getKey(); break; }
                                }
                                if (name == null) return;
                                StringBuilder sb = new StringBuilder("nc: " + name);
                                for (int i = 1; i < param.args.length; i++) {
                                    sb.append(" | ").append(summarize(param.args[i]));
                                }
                                log(sb.toString());
                                if ("messagesDeleted".equals(name)) {
                                    for (int i = 1; i < param.args.length; i++) {
                                        Object a = param.args[i];
                                        if (a instanceof List) {
                                            for (Object o : (List<?>) a) {
                                                if (o instanceof Number && pausedIds.contains(((Number) o).longValue())) {
                                                    param.setResult(null);
                                                    log("nc: VETO messagesDeleted for paused id");
                                                    return;
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable t) {}
                        }
                    });
                    if (h != null) { hooks.add(h); hooked++; }
                } catch (Throwable t) {}
            }
            log("hookNotificationCenter: hooked " + hooked);
        } catch (Throwable t) {
            log("hookNotificationCenter: " + t);
        }
    }

    private static boolean isMediaVoice(Object mo) {
        if (mo == null) return false;
        return boolOf(mo, "isVoice") || boolOf(mo, "isRoundVideo");
    }

    private static void hookUploadStart() {
        try {
            Class<?> cls = Class.forName("org.telegram.messenger.FileLoader");
            Method[] ms = cls.getDeclaredMethods();
            int hooked = 0;
            for (Method m : ms) {
                if (!"uploadFile".equals(m.getName())) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length < 1 || pt[0] != String.class) continue;
                try { m.setAccessible(true); } catch (Throwable t) {}
                try {
                    XC_MethodHook.Unhook h = XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        public void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!enabled || delayPref <= 0) return;
                                String location = (String) param.args[0];
                                if (location == null) return;
                                long id = 0;
                                int acc = -1;
                                for (int a = 0; a < 4 && id == 0; a++) {
                                    Object smh = getSmh(a);
                                    if (smh == null) continue;
                                    Field f = findField(smh.getClass(), "delayedMessages");
                                    if (f == null) continue;
                                    Object map = f.get(smh);
                                    if (!(map instanceof Map)) continue;
                                    Object list = ((Map<?, ?>) map).get(location);
                                    if (!(list instanceof ArrayList)) continue;
                                    for (Object cand : (ArrayList<?>) list) {
                                        Object o = gf(cand, "obj");
                                        if (isMediaVoice(o)) {
                                            id = msgId(o);
                                            acc = a;
                                            break;
                                        }
                                    }
                                }
                                if (id == 0) return;
                                synchronized (recentFire) {
                                    Long last = recentFire.get(location);
                                    if (last != null && System.currentTimeMillis() - last.longValue() < 20000L) {
                                        log("delay: passthrough (recent proceed) id=" + id + " loc=" + location);
                                        return;
                                    }
                                }
                                synchronized (deferred) {
                                    if (deferred.containsKey(id)) return;
                                    deferred.put(id, new DelayJob(param.thisObject, param.args, acc, id, 0));
                                }
                                param.setResult(null);
                                log("delay: HOLD upload id=" + id + " sec=" + delayPref + " loc=" + location);
                                scheduleFire(id);
                            } catch (Throwable t) {}
                        }
                    });
                    if (h != null) { hooks.add(h); hooked++; }
                } catch (Throwable t) {}
            }
            log("hookUploadStart: hooked " + hooked);
        } catch (Throwable t) {
            log("hookUploadStart: " + t);
        }
    }

    private static void scheduleFire(final long id) {
        try {
            if (mainHandler == null) mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    fireDeferred(id);
                }
            }, delayPref * 1000L);
        } catch (Throwable t) {
            log("delay: schedule err " + t);
        }
    }

    private static boolean fireDeferredIfQueued(long id) {
        synchronized (deferred) {
            if (!deferred.containsKey(id)) return false;
        }
        fireDeferred(id);
        return true;
    }

    // Resume from an explicit pause (cell tap, "Продолжить"/"Отправить" on the
    // paused-message dialog). If the job is still sitting in `deferred` (i.e. the
    // upload never actually started - it was paused during the pre-send delay),
    // and a delay is configured, re-arm the delay timer instead of firing the
    // upload immediately: pausing and resuming should behave like a fresh send,
    // giving the user another window to press the X again. If delay is off, or
    // the job already left `deferred` (a "late" pause, upload/network already in
    // flight), fall through to the normal immediate-resume path.
    private static boolean requeueDeferredIfQueued(long id) {
        boolean present;
        synchronized (deferred) {
            present = deferred.containsKey(id);
        }
        if (!present) return false;
        if (delayPref <= 0) {
            fireDeferred(id);
            return true;
        }
        log("delay: re-armed on resume id=" + id + " sec=" + delayPref);
        scheduleFire(id);
        return true;
    }

    private static void fireDeferred(long id) {
        // If the user paused this message (via the dialog shown during the delay
        // phase, or otherwise) while it was still sitting in `deferred`, do not
        // consume/fire the job now - leave it in the map so an explicit resume
        // (cell tap or a dialog button) can fire/requeue it later via
        // fireDeferredIfQueued/requeueDeferredIfQueued.
        synchronized (pausedIds) {
            if (pausedIds.contains(id)) {
                log("delay: fire skipped (paused) id=" + id);
                return;
            }
        }
        final DelayJob job;
        synchronized (deferred) {
            job = deferred.remove(id);
        }
        if (job == null) {
            // Not in `deferred` at all (already fired, or never queued) - nothing to do.
            log("delay: skipped id=" + id + " (no job in deferred)");
            return;
        }
        try {
            // Bug fix: this used to additionally require the message to already be
            // present in SendMessagesHelper.delayedMessages for the job's `location`
            // before proceeding. But while a job sits in `deferred`, the *original*
            // uploadFile call that would have populated delayedMessages was itself
            // intercepted and blocked (see hookUploadStart: param.setResult(null)
            // before Telegram's own uploadFile body ran) - so delayedMessages never
            // got an entry for it in the first place. That made this check almost
            // always false, so pressing "Отправить"/"Продолжить" during the delay
            // phase silently dropped the message instead of sending it. The presence
            // of `job` in `deferred` (removed via deferred.remove(id) above) is
            // already sufficient proof the job is live and hasn't been consumed by
            // another caller, so just proceed.
            String location = job.args.length > 0 && job.args[0] instanceof String ? (String) job.args[0] : null;
            log("delay: proceed upload id=" + id);
            if (location != null) {
                synchronized (recentFire) {
                    recentFire.put(location, Long.valueOf(System.currentTimeMillis()));
                    if (recentFire.size() > 16) recentFire.clear();
                }
            }
            Method m = findMethodByArity(job.self.getClass(), "uploadFile", job.args.length);
            if (m == null) {
                log("delay: method re-find failed id=" + id);
                return;
            }
            m.invoke(job.self, job.args);
        } catch (Throwable t) {
            log("delay: fire err " + t);
        }
    }

    private static Method findMethodByArity(Class<?> cls, String name, int arity) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals(name) && m.getParameterTypes().length == arity) {
                        try { m.setAccessible(true); } catch (Throwable t) {}
                        return m;
                    }
                }
            } catch (Throwable t) {}
        }
        return null;
    }

    private static void saveToDownloads(Object mo) {
        if (!savePref) return;
        long id = msgId(mo);
        synchronized (savedIds) {
            if (savedIds.contains(id)) {
                log("save: skip (already saved) id=" + id);
                return;
            }
            savedIds.add(id);
        }
        Object owner = gf(mo, "messageOwner");
        Object pathObj = owner == null ? null : gf(owner, "attachPath");
        if (!(pathObj instanceof String)) { log("save: no attachPath id=" + id); return; }
        final String path = (String) pathObj;
        if (path.isEmpty()) { log("save: empty attachPath id=" + id); return; }
        final File src = new File(path);
        if (!src.isFile()) { log("save: file missing " + path); return; }
        // Determine proper extension
        boolean isVoice = boolOf(mo, "isVoice");
        boolean isRound = boolOf(mo, "isRoundVideo");
        boolean isVideo = boolOf(mo, "isVideo");
        String ext;
        if (isVoice) ext = ".ogg";
        else if (isRound || isVideo) ext = ".mp4";
        else ext = ".ogg";
        final String name = "SendPause_" + System.currentTimeMillis() + ext;
        log("save: copying id=" + id + " -> Downloads/" + name);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String mime = mimeFor(name);
                    Context ctx = appContext();
                    if (ctx == null) { log("save: no context"); return; }
                    if (Build.VERSION.SDK_INT >= 29) {
                        ContentValues cv = new ContentValues();
                        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                        cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                        Uri u = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                        if (u == null) { log("save: mediaStore insert null"); return; }
                        OutputStream os = ctx.getContentResolver().openOutputStream(u);
                        if (os == null) { log("save: no output stream"); return; }
                        copyFile(src, os);
                        log("save: OK " + name);
                        showToast(ctx, "Сохранено в Загрузки: " + name);
                    } else {
                        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SendPause");
                        if (!dir.exists() && !dir.mkdirs()) { log("save: cannot create dir"); return; }
                        File dst = new File(dir, name);
                        OutputStream os = new FileOutputStream(dst);
                        copyFile(src, os);
                        log("save: OK " + dst.getAbsolutePath());
                        showToast(ctx, "Сохранено в Загрузки: " + name);
                    }
                } catch (Throwable t) {
                    log("save: err " + t);
                }
            }
        }, "SendPause-save");
        t.start();
    }

    private static void showToast(final Context ctx, final String msg) {
        if (mainHandler == null) mainHandler = new Handler(android.os.Looper.getMainLooper());
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show();
                } catch (Throwable t) {}
            }
        });
    }

    private static void copyFile(File src, OutputStream os) throws Exception {
        InputStream is = new FileInputStream(src);
        byte[] buf = new byte[65536];
        try {
            int r;
            while ((r = is.read(buf)) > 0) {
                os.write(buf, 0, r);
            }
            os.flush();
        } finally {
            try { os.close(); } catch (Throwable t) {}
            try { is.close(); } catch (Throwable t) {}
        }
    }

    private static String mimeFor(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".ogg") || n.endsWith(".oga")) return "audio/ogg";
        if (n.endsWith(".mp4") || n.endsWith(".m4v")) return "video/mp4";
        if (n.endsWith(".m4a") || n.endsWith(".aac")) return "audio/mp4";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        return "application/octet-stream";
    }

    private static Context appContext() {
        try {
            Class<?> al = Class.forName("org.telegram.messenger.ApplicationLoader");
            Field f = al.getField("applicationContext");
            Object c = f.get(null);
            if (c instanceof Context) return (Context) c;
        } catch (Throwable t) {}
        return null;
    }

    private static String summarize(Object o) {
        if (o == null) return "null";
        if (o instanceof Number) return String.valueOf(o);
        if (o instanceof String) {
            String s = (String) o;
            return s.length() > 60 ? s.substring(0, 60) : s;
        }
        if (moClass != null && moClass.isInstance(o)) return "MO#" + msgId(o);
        if (o instanceof Object[]) {
            Object[] arr = (Object[]) o;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.length && i < 4; i++) {
                if (i > 0) sb.append(",");
                sb.append(summarize(arr[i]));
            }
            if (arr.length > 4) sb.append(",..").append(arr.length);
            return sb.append("]").toString();
        }
        if (o instanceof List) {
            List<?> l = (List<?>) o;
            StringBuilder sb = new StringBuilder("list[");
            for (int i = 0; i < l.size() && i < 6; i++) {
                if (i > 0) sb.append(",");
                sb.append(summarize(l.get(i)));
            }
            if (l.size() > 6) sb.append(",..").append(l.size());
            return sb.append("]").toString();
        }
        return o.getClass().getSimpleName();
    }

    // ============================================================
    // pause / resume
    // ============================================================

    private static boolean pause(long id, Object mo) {
        try {
            Object smh = getSmh(accountOf(mo));
            if (smh == null) { log("pause: no smh"); return false; }
            Field f = findField(smh.getClass(), "delayedMessages");
            if (f == null) { log("pause: no delayedMessages field"); return false; }
            Object map = f.get(smh);
            if (!(map instanceof Map)) { log("pause: map type=" + (map == null ? "null" : map.getClass().getName())); return false; }
            Map<?, ?> m = (Map<?, ?>) map;

            Object dm = null;
            String loc = null;
            Object listRef = null;
            Object keyRef = null;
            int idx = -1;
            for (Object e0 : m.entrySet()) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) e0;
                Object v = e.getValue();
                if (!(v instanceof ArrayList)) continue;
                ArrayList<?> list = (ArrayList<?>) v;
                for (int i = 0; i < list.size(); i++) {
                    Object cand = list.get(i);
                    Object obj = gf(cand, "obj");
                    if (obj == null) continue;
                    if (gf(cand, "messageObjects") != null) continue;
                    if (msgId(obj) != id) continue;
                    dm = cand;
                    loc = (e.getKey() instanceof String) ? (String) e.getKey() : null;
                    listRef = list;
                    keyRef = e.getKey();
                    idx = i;
                    break;
                }
                if (dm != null) break;
            }
            if (dm == null) { log("pause: dm not found id=" + id + " mapSize=" + m.size() + " acc=" + accountOf(mo)); return false; }
            if (loc != null && loc.startsWith("Delayed")) { log("pause: skipped encoded key=" + loc + " id=" + id); return false; }

            ArrayList<?> list = (ArrayList<?>) listRef;
            list.remove(idx);
            if (list.isEmpty() && keyRef != null) {
                try { m.remove(keyRef); } catch (Throwable t) {}
            }

            Object uploads = gf(dm, "uploads");
            if (uploads instanceof Map) {
                for (Object op : ((Map<?, ?>) uploads).values()) {
                    try {
                        Method cm = findMethod(op.getClass(), "cancel", new Class<?>[0]);
                        if (cm != null) cm.invoke(op);
                    } catch (Throwable t) {}
                }
            }
            if (loc != null) {
                Object fl = callGetInstance("org.telegram.messenger.FileLoader", accountOf(dm));
                if (fl == null) { log("pause: no FileLoader"); }
                else {
                    Method cm = findMethod(fl.getClass(), "cancelFileUpload", new Class<?>[]{String.class, boolean.class});
                    if (cm == null) { log("pause: no cancelFileUpload"); }
                    else cm.invoke(fl, loc, Boolean.FALSE);
                }
            }
            synchronized (pausedIds) { pausedIds.add(id); }
            persistPaused();
            log("pause: OK id=" + id + " key=" + loc);
            postUpdate(mo);
            return true;
        } catch (Throwable t) {
            log("pause: " + t);
            return false;
        }
    }

    private static void resume(Object mo) {
        try {
            Object smh = getSmh(accountOf(mo));
            if (smh == null) { log("resume: no smh"); return; }
            if (!invokeSend(smh, mo)) {
                log("resume: no send method found");
            }
        } catch (Throwable t) {
            log("resume: " + t);
        }
    }

    private static boolean invokeSend(Object smh, Object mo) {
        List<Method> cands = new ArrayList<Method>();
        Object owner = gf(mo, "messageOwner");
        for (Class<?> c = smh.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Method[] ms = c.getDeclaredMethods();
                for (Method m : ms) {
                    String n = m.getName();
                    if (!"sendMessage".equals(n) && !"retrySendMessage".equals(n)) continue;
                    Class<?>[] pt = m.getParameterTypes();
                    if (pt.length < 1) continue;
                    Class<?> p0 = pt[0];
                    boolean ok0 = p0.isAssignableFrom(moClass)
                            || (owner != null && p0.isAssignableFrom(owner.getClass()));
                    if (!ok0) continue;
                    try { m.setAccessible(true); } catch (Throwable t) {}
                    cands.add(m);
                }
            } catch (Throwable t) {}
        }
        for (int score = 0; score <= 5; score++) {
            for (Method m : cands) {
                if (scoreOf(m) != score) continue;
                try {
                    m.invoke(smh, argsFor(m, mo, owner));
                    log("resume: sent via " + m.getName() + " arity=" + m.getParameterTypes().length
                            + " (" + paramTypeNames(m) + ") class=" + m.getDeclaringClass().getName());
                    return true;
                } catch (Throwable t) {
                    log("resume: " + m.getName() + " err " + t);
                }
            }
        }
        return false;
    }

    private static int scoreOf(Method m) {
        String n = m.getName();
        Class<?>[] pt = m.getParameterTypes();
        boolean moFirst = pt.length > 0 && pt[0].isAssignableFrom(moClass);
        boolean canonical = pt.length == 2 && moFirst && pt[1] == boolean.class;
        if ("retrySendMessage".equals(n) && canonical) return 0;
        if ("retrySendMessage".equals(n) && pt.length == 1 && moFirst) return 1;
        if ("sendMessage".equals(n) && pt.length == 1 && moFirst) return 2;
        if ("sendMessage".equals(n) && pt.length == 1) return 3;
        if ("retrySendMessage".equals(n)) return 4;
        return 5;
    }

    private static String paramTypeNames(Method m) {
        Class<?>[] pt = m.getParameterTypes();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pt.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(pt[i].getSimpleName());
        }
        return sb.toString();
    }

    private static Object[] argsFor(Method m, Object mo, Object owner) {
        Class<?>[] pt = m.getParameterTypes();
        Object[] args = new Object[pt.length];
        if (pt[0].isAssignableFrom(moClass)) args[0] = mo;
        else args[0] = owner;
        for (int i = 1; i < pt.length; i++) {
            Class<?> t = pt[i];
            if (t == boolean.class) args[i] = Boolean.FALSE;
            else if (t == int.class) args[i] = Integer.valueOf(0);
            else if (t == long.class) args[i] = Long.valueOf(0L);
            else args[i] = null;
        }
        return args;
    }

    private static void postUpdate(Object mo) {
        try {
            Object owner = gf(mo, "messageOwner");
            if (owner == null) return;
            Class<?> ncClass = Class.forName("org.telegram.messenger.NotificationCenter");
            Field mf;
            try { mf = ncClass.getField("updateMessageMedia"); } catch (Throwable t) { mf = null; }
            if (mf == null) return;
            int id = mf.getInt(null);
            Object nc = callGetInstance("org.telegram.messenger.NotificationCenter", accountOf(mo));
            if (nc == null) return;
            Method m = findMethod(nc.getClass(), "postNotificationName", new Class<?>[]{int.class, Object[].class});
            if (m == null) return;
            m.invoke(nc, Integer.valueOf(id), new Object[]{owner});
        } catch (Throwable t) {
            log("postUpdate: " + t);
        }
    }

    // ============================================================
    // overlay
    // ============================================================

    private static int themeColor(String key, int fallback) {
        try {
            Class<?> theme = Class.forName("org.telegram.ui.ActionBar.Theme");
            Method m = theme.getMethod("getColor", String.class);
            Object r = m.invoke(null, key);
            if (r instanceof Integer) return (Integer) r;
        } catch (Throwable t) {}
        return fallback;
    }

    private static float dp(float v) {
        try {
            Class<?> au = Class.forName("org.telegram.messenger.AndroidUtilities");
            Method m = au.getMethod("dp", float.class);
            Object r = m.invoke(null, v);
            if (r instanceof Number) return ((Number) r).floatValue();
        } catch (Throwable t) {}
        return v * 2f; // грубый fallback на случай отсутствия AndroidUtilities
    }

    private static void drawOverlay(Object cell, Canvas canvas) {
        if (canvas == null) return;
        Object rp = call0(cell, "getRadialProgress");
        if (rp == null) return;
        Object rect = call0(rp, "getProgressRect");
        if (!(rect instanceof RectF)) return;
        RectF r = (RectF) rect;
        if (r.width() <= 0f || r.height() <= 0f) return;
        float cx = r.centerX();
        float cy = r.centerY();
        // небольшой отступ, чтобы кружок не вылезал за штатный radial progress
        float inset = dp(1.5f);
        float rad = Math.max(0f, Math.min(r.width(), r.height()) / 2f - inset);
        if (rad <= 0f) return;

        // Цвет берём из темы приложения, чтобы кружок совпадал с акцентом
        // (send/voice button), а не был плоской чёрной заглушкой.
        int accent = themeColor("chat_outSendButton", 0xFF3E9BE0);
        int fillColor = (accent & 0x00FFFFFF) | 0xCC000000; // тот же тон, ~80% альфа

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(fillColor);
        canvas.drawCircle(cx, cy, rad, fill);

        // тонкая обводка поверх — как у штатного radial progress ринга
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.5f));
        stroke.setColor(0x33FFFFFF);
        canvas.drawCircle(cx, cy, rad - stroke.getStrokeWidth() / 2f, stroke);

        // белый play-треугольник (иконка "продолжить")
        float s = rad * 0.5f;
        Path path = new Path();
        path.moveTo(cx - s * 0.45f, cy - s);
        path.lineTo(cx + s * 0.85f, cy);
        path.lineTo(cx - s * 0.45f, cy + s);
        path.close();
        Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
        icon.setStyle(Paint.Style.FILL);
        icon.setColor(0xFFFFFFFF);
        canvas.drawPath(path, icon);
    }

    private static void invalidateCell(Object cell) {
        try {
            Method m = findMethod(cell.getClass(), "invalidate", new Class<?>[0]);
            if (m != null) m.invoke(cell);
        } catch (Throwable t) {}
    }

    // ============================================================
    // helpers
    // ============================================================

    private static long msgId(Object mo) {
        try {
            Object r = call0(mo, "getId");
            if (r == null) return 0L;
            return ((Number) r).longValue();
        } catch (Throwable t) {
            return 0L;
        }
    }

    private static boolean boolOf(Object mo, String name) {
        try {
            Object r = call0(mo, name);
            return r instanceof Boolean && ((Boolean) r).booleanValue();
        } catch (Throwable t) {
            return false;
        }
    }

    private static int intField(Object obj, String name) {
        Field f = findField(obj.getClass(), name);
        if (f == null) return -1;
        try {
            return f.getInt(obj);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static boolean boolField(Object obj, String name) {
        Field f = findField(obj.getClass(), name);
        if (f == null) return false;
        try {
            return f.getBoolean(obj);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isPausable(Object mo) {
        // Только голосовые (ГС) и видео-сообщения кружком (ВС) — не обычное видео/фото/файлы.
        if (!boolOf(mo, "isVoice") && !boolOf(mo, "isRoundVideo")) return false;
        if (boolOf(mo, "isSending")) return true;
        Object owner = gf(mo, "messageOwner");
        if (owner == null) return false;
        int st = intField(owner, "send_state");
        if (st == 2) {
            log("cancel: media with send_state=ERROR -> hold");
            return true;
        }
        return false;
    }

    private static Object getMessageObject(Object cell) {
        return call0(cell, "getMessageObject");
    }

    private static int accountOf(Object mo) {
        try {
            Object acc = gf(mo, "currentAccount");
            if (acc instanceof Number) return ((Number) acc).intValue();
        } catch (Throwable t) {}
        try {
            Class<?> uc = Class.forName("org.telegram.messenger.UserConfig");
            Field f = uc.getField("selectedAccount");
            return f.getInt(null);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static Object callGetInstance(String clsName, int acc) {
        try {
            Class<?> cls = Class.forName(clsName);
            Method m = null;
            try { m = cls.getMethod("getInstance", int.class); } catch (Throwable t) {
                m = findDeclared(cls, "getInstance", new Class<?>[]{int.class});
            }
            if (m != null) {
                Object r = m.invoke(null, Integer.valueOf(acc));
                if (r != null) return r;
            }
            try { m = cls.getMethod("getInstance"); } catch (Throwable t) {
                m = findDeclared(cls, "getInstance", new Class<?>[0]);
            }
            return m != null ? m.invoke(null) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object getSmh(int acc) {
        return callGetInstance("org.telegram.messenger.SendMessagesHelper", acc);
    }

    private static Object call0(Object obj, String name) {
        if (obj == null) return null;
        Method m = zeroArgCache.get(obj.getClass().getName() + "#" + name);
        if (m == null) {
            m = findMethod(obj.getClass(), name, new Class<?>[0]);
            if (m != null) zeroArgCache.put(obj.getClass().getName() + "#" + name, m);
        }
        if (m == null) return null;
        try {
            return m.invoke(obj);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object gf(Object obj, String name) {
        if (obj == null) return null;
        Field f = findField(obj.getClass(), name);
        if (f == null) return null;
        try {
            return f.get(obj);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method findDeclared(Class<?> cls, String name, Class<?>[] types) {
        try {
            Method m = cls.getDeclaredMethod(name, types);
            try { m.setAccessible(true); } catch (Throwable t) {}
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>[] types) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, types);
                try { m.setAccessible(true); } catch (Throwable t) {}
                return m;
            } catch (NoSuchMethodException e) {}
        }
        return null;
    }

    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                try { f.setAccessible(true); } catch (Throwable t) {}
                return f;
            } catch (NoSuchFieldException e) {}
        }
        return null;
    }

    private static void log(String s) {
        try {
            Log.d(TAG, s);
        } catch (Throwable t) {}
        diag(s);
    }
}