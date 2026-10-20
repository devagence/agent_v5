package ai.lailaba.mobile;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** User-enabled notification access bridge. Does not persist notification content. */
public class LailabaNotificationListener extends NotificationListenerService {
    private static volatile LailabaNotificationListener instance;

    @Override public void onListenerConnected() { super.onListenerConnected(); instance = this; }
    @Override public void onListenerDisconnected() { if (instance == this) instance = null; super.onListenerDisconnected(); }
    @Override public void onDestroy() { if (instance == this) instance = null; super.onDestroy(); }

    static boolean isEnabled() { return instance != null; }
    static StatusBarNotification[] getActiveNotificationsSafe() {
        LailabaNotificationListener service = instance;
        if (service == null) return null;
        try { return service.getActiveNotifications(); } catch (SecurityException | IllegalStateException e) { return null; }
    }
}
