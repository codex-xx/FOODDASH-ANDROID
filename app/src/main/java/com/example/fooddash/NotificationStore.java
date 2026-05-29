package com.example.fooddash;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NotificationStore {

    private static final String PREFS_NAME = "fooddash_notification_prefs";
    private static final String LEGACY_PREFS_NAME = "fooddash_prefs";
    private static final String LEGACY_HISTORY_KEY = "notification_history_json";
    private static final String LEGACY_DISMISSED_KEY = "dismissed_notification_keys_json";
    private static final String KEY_PREFIX_HISTORY = "notification_history_json_user_";
    private static final String KEY_PREFIX_DISMISSED = "dismissed_notification_keys_json_user_";

    private static final String TYPE_ORDER = "order";
    private static final String TYPE_PROMOTION = "promotion";
    private static final String TYPE_MESSAGE = "message";

    private NotificationStore() {
    }

    public enum NotificationFilter {
        ALL,
        UNREAD,
        ORDERS,
        PROMOTIONS
    }

    public static final class NotificationGroup {
        public final String groupKey;
        public final int orderId;
        public final String type;
        public final String title;
        public final String subtitle;
        public final String latestStatus;
        public final String latestMessage;
        public final long latestTimestamp;
        public final boolean unread;
        public final List<JSONObject> events;

        NotificationGroup(String groupKey,
                          int orderId,
                          String type,
                          String title,
                          String subtitle,
                          String latestStatus,
                          String latestMessage,
                          long latestTimestamp,
                          boolean unread,
                          List<JSONObject> events) {
            this.groupKey = groupKey;
            this.orderId = orderId;
            this.type = type;
            this.title = title;
            this.subtitle = subtitle;
            this.latestStatus = latestStatus;
            this.latestMessage = latestMessage;
            this.latestTimestamp = latestTimestamp;
            this.unread = unread;
            this.events = events;
        }

        public JSONObject getLatestEvent() {
            if (events.isEmpty()) return new JSONObject();
            return events.get(events.size() - 1);
        }
    }

    public static JSONArray mergeFetchedOrders(Context context, List<JSONObject> orders) {
        int userId = getCurrentUserId(context);
        if (userId <= 0) {
            return loadHistory(context);
        }

        JSONArray currentHistory = loadHistory(context);
        Set<String> dismissedKeys = loadDismissedKeys(context);
        Map<String, JSONObject> mergedEvents = new LinkedHashMap<>();

        for (int i = 0; i < currentHistory.length(); i++) {
            JSONObject event = currentHistory.optJSONObject(i);
            if (event == null) continue;
            String eventKey = getEventKey(event, i);
            if (TextUtils.isEmpty(eventKey) || dismissedKeys.contains(eventKey)) continue;
            mergedEvents.put(eventKey, normalizeEvent(event, true));
        }

        for (JSONObject order : orders) {
            if (order == null) continue;
            int orderId = order.optInt("id", order.optInt("order_id", -1));
            String status = ActiveOrderActivity.normalizeStatus(order.optString("status", "pending"));
            String restaurant = firstNonEmpty(order.optString("restaurant_name"), order.optString("restaurant"), "Restaurant");

            if (!isNotifiableStatus(status)) {
                continue;
            }

            for (String stage : getStagesForStatus(status)) {
                JSONObject event = createNotificationEvent(userId, orderId, stage, restaurant, order, getStageRank(status));
                String eventKey = event.optString("event_key", "");
                if (TextUtils.isEmpty(eventKey) || dismissedKeys.contains(eventKey)) {
                    continue;
                }
                JSONObject existing = mergedEvents.get(eventKey);
                mergedEvents.put(eventKey, existing == null ? event : mergeEvent(existing, event));
            }
        }

        JSONArray updated = toSortedArray(mergedEvents.values());
        saveHistory(context, updated);
        return updated;
    }

    public static JSONArray loadHistory(Context context) {
        int userId = getCurrentUserId(context);
        if (userId <= 0) {
            return new JSONArray();
        }

        ensureMigrated(context, userId);
        SharedPreferences prefs = getPrefs(context);
        String raw = prefs.getString(historyKey(userId), "[]");
        JSONArray history = parseArray(raw);
        return normalizeHistory(history, loadDismissedKeys(context), false);
    }

    public static void saveHistory(Context context, JSONArray history) {
        int userId = getCurrentUserId(context);
        if (userId <= 0) {
            return;
        }
        SharedPreferences prefs = getPrefs(context);
        prefs.edit().putString(historyKey(userId), normalizeHistory(history, loadDismissedKeys(context), false).toString()).apply();
    }

    public static Set<String> loadDismissedKeys(Context context) {
        int userId = getCurrentUserId(context);
        if (userId <= 0) {
            return new LinkedHashSet<>();
        }

        ensureMigrated(context, userId);
        SharedPreferences prefs = getPrefs(context);
        String raw = prefs.getString(dismissedKey(userId), "[]");
        return parseStringSet(raw);
    }

    public static void saveDismissedKeys(Context context, Set<String> keys) {
        int userId = getCurrentUserId(context);
        if (userId <= 0) {
            return;
        }
        JSONArray array = new JSONArray();
        for (String key : keys) {
            if (!TextUtils.isEmpty(key)) {
                array.put(key);
            }
        }
        getPrefs(context).edit().putString(dismissedKey(userId), array.toString()).apply();
    }

    public static void dismissGroups(Context context, List<NotificationGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            return;
        }

        Set<String> groupKeys = new LinkedHashSet<>();
        for (NotificationGroup group : groups) {
            if (group != null) {
                groupKeys.add(group.groupKey);
            }
        }
        dismissGroupKeys(context, groupKeys);
    }

    public static void dismissGroupKeys(Context context, Set<String> groupKeys) {
        if (groupKeys == null || groupKeys.isEmpty()) {
            return;
        }

        JSONArray history = loadHistory(context);
        JSONArray updated = new JSONArray();
        Set<String> dismissed = loadDismissedKeys(context);

        for (int i = 0; i < history.length(); i++) {
            JSONObject event = history.optJSONObject(i);
            if (event == null) continue;
            String groupKey = getGroupKey(event, i);
            String eventKey = getEventKey(event, i);
            if (groupKeys.contains(groupKey)) {
                if (!TextUtils.isEmpty(eventKey)) {
                    dismissed.add(eventKey);
                }
                continue;
            }
            updated.put(normalizeEvent(event, false));
        }

        saveDismissedKeys(context, dismissed);
        saveHistory(context, updated);
    }

    public static void markAllRead(Context context) {
        JSONArray history = loadHistory(context);
        JSONArray updated = new JSONArray();
        for (int i = 0; i < history.length(); i++) {
            JSONObject event = history.optJSONObject(i);
            if (event == null) continue;
            JSONObject copy = copyJson(event);
            try {
                copy.put("read", true);
            } catch (Exception ignored) {
            }
            updated.put(normalizeEvent(copy, true));
        }
        saveHistory(context, updated);
    }

    public static void markGroupRead(Context context, String groupKey) {
        if (TextUtils.isEmpty(groupKey)) {
            return;
        }

        JSONArray history = loadHistory(context);
        JSONArray updated = new JSONArray();
        for (int i = 0; i < history.length(); i++) {
            JSONObject event = history.optJSONObject(i);
            if (event == null) continue;
            JSONObject copy = copyJson(event);
            if (groupKey.equals(getGroupKey(copy, i))) {
                try {
                    copy.put("read", true);
                } catch (Exception ignored) {
                }
            }
            updated.put(normalizeEvent(copy, copy.optBoolean("read", false)));
        }
        saveHistory(context, updated);
    }

    public static int getUnreadGroupCount(Context context) {
        int count = 0;
        List<NotificationGroup> groups = buildGroups(loadHistory(context), NotificationFilter.ALL);
        for (NotificationGroup group : groups) {
            if (group.unread) {
                count++;
            }
        }
        return count;
    }

    public static List<NotificationGroup> buildGroups(JSONArray history, NotificationFilter filter) {
        List<NotificationGroup> groups = new ArrayList<>();
        if (history == null) {
            return groups;
        }

        Map<String, GroupBuilder> builders = new LinkedHashMap<>();
        for (int i = 0; i < history.length(); i++) {
            JSONObject event = history.optJSONObject(i);
            if (event == null) continue;
            JSONObject normalized = normalizeEvent(event, false);
            String groupKey = getGroupKey(normalized, i);
            GroupBuilder builder = builders.get(groupKey);
            if (builder == null) {
                builder = new GroupBuilder(groupKey, normalized);
                builders.put(groupKey, builder);
            }
            builder.add(normalized);
        }

        for (GroupBuilder builder : builders.values()) {
            NotificationGroup group = builder.build();
            if (!matchesFilter(group, filter)) {
                continue;
            }
            groups.add(group);
        }

        Collections.sort(groups, new Comparator<NotificationGroup>() {
            @Override
            public int compare(NotificationGroup left, NotificationGroup right) {
                return Long.compare(right.latestTimestamp, left.latestTimestamp);
            }
        });
        return groups;
    }

    public static String formatTimestamp(Context context, long timestampMillis) {
        if (timestampMillis <= 0) {
            return "";
        }

        long now = System.currentTimeMillis();
        if (DateUtils.isToday(timestampMillis)) {
            return "Today " + DateFormat.getTimeFormat(context).format(timestampMillis);
        }

        long diff = now - timestampMillis;
        if (diff < DateUtils.HOUR_IN_MILLIS) {
            return DateUtils.getRelativeTimeSpanString(timestampMillis, now, DateUtils.MINUTE_IN_MILLIS).toString();
        }

        if (DateUtils.isToday(timestampMillis + DateUtils.DAY_IN_MILLIS)) {
            return "Yesterday " + DateFormat.getTimeFormat(context).format(timestampMillis);
        }

        return DateFormat.getMediumDateFormat(context).format(timestampMillis) + " " + DateFormat.getTimeFormat(context).format(timestampMillis);
    }

    public static String getStageLabel(String status) {
        if (Constants.STATUS_ACCEPTED.equals(status)) return "Accepted";
        if (Constants.STATUS_PREPARING.equals(status)) return "Preparing";
        if (Constants.STATUS_PICKED_UP.equals(status)) return "Rider Accepted";
        if (Constants.STATUS_OUT_FOR_DELIVERY.equals(status)) return "Out for Delivery";
        if (Constants.STATUS_DELIVERED.equals(status)) return "Delivered";
        return status;
    }

    public static int getStageRank(String status) {
        if (Constants.STATUS_ACCEPTED.equals(status)) return 1;
        if (Constants.STATUS_PREPARING.equals(status)) return 2;
        if (Constants.STATUS_PICKED_UP.equals(status)) return 3;
        if (Constants.STATUS_OUT_FOR_DELIVERY.equals(status)) return 4;
        if (Constants.STATUS_DELIVERED.equals(status)) return 5;
        return 0;
    }

    public static List<String> getStagesForStatus(String status) {
        List<String> stages = new ArrayList<>();
        int rank = getStageRank(status);
        if (rank >= 1) stages.add(Constants.STATUS_ACCEPTED);
        if (rank >= 2) stages.add(Constants.STATUS_PREPARING);
        if (rank >= 3) stages.add(Constants.STATUS_PICKED_UP);
        if (rank >= 4) stages.add(Constants.STATUS_OUT_FOR_DELIVERY);
        if (rank >= 5) stages.add(Constants.STATUS_DELIVERED);
        return stages;
    }

    public static boolean isNotifiableStatus(String status) {
        return Constants.STATUS_ACCEPTED.equals(status)
                || Constants.STATUS_PREPARING.equals(status)
                || Constants.STATUS_PICKED_UP.equals(status)
                || Constants.STATUS_OUT_FOR_DELIVERY.equals(status)
                || Constants.STATUS_DELIVERED.equals(status);
    }

    public static int getCurrentUserId(Context context) {
        SharedPreferences legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
        return legacyPrefs.getInt("user_id", -1);
    }

    private static boolean matchesFilter(NotificationGroup group, NotificationFilter filter) {
        if (filter == null || filter == NotificationFilter.ALL) {
            return true;
        }
        if (filter == NotificationFilter.UNREAD) {
            return group.unread;
        }
        if (filter == NotificationFilter.ORDERS) {
            return group.orderId > 0;
        }
        if (filter == NotificationFilter.PROMOTIONS) {
            return TYPE_PROMOTION.equalsIgnoreCase(group.type);
        }
        return true;
    }

    private static JSONObject createNotificationEvent(int userId, int orderId, String status, String restaurant, JSONObject order, int currentStatusRank) {
        JSONObject event = new JSONObject();
        try {
            event.put("event_key", orderId + "_" + status);
            event.put("user_id", userId);
            event.put("order_id", orderId);
            event.put("status", status);
            event.put("title", getStageLabel(status));
            // message should use the cleaned restaurant name, not raw JSON
            String fallbackRestaurant = firstNonEmpty(order.optString("restaurant_name"), order.optString("restaurant"), "Restaurant");
            String messageRestaurant = fallbackRestaurant;
            long baseTime = extractOrderTimestamp(order);
            int stageRank = getStageRank(status);
            // If we are creating events for earlier stages, space them slightly before the base time so timeline orders correctly
            long createdAt = baseTime - (long) (Math.max(0, currentStatusRank - stageRank) * 2000L);
            event.put("created_at", createdAt > 0 ? createdAt : System.currentTimeMillis());
            event.put("read", false);
            event.put("type", "order");
            // Prefer explicit restaurant name/address fields; avoid embedding raw JSON
            String restaurantName = firstNonEmpty(order.optString("restaurant_name"), order.optString("restaurant"));
            String restaurantAddress = firstNonEmpty(order.optString("restaurant_address"), order.optString("restaurant_address"));
            JSONObject restaurantObj = order.optJSONObject("restaurant");
            if (restaurantObj != null) {
                restaurantName = firstNonEmpty(restaurantObj.optString("name"), restaurantName);
                restaurantAddress = firstNonEmpty(restaurantObj.optString("address"), restaurantAddress);
            }
            // If the extracted restaurantName looks like JSON, ignore it
            if (!TextUtils.isEmpty(restaurantName) && (restaurantName.trim().startsWith("{") || restaurantName.trim().startsWith("["))) {
                restaurantName = "Restaurant";
            }
            event.put("restaurant_name", restaurantName);
            if (!TextUtils.isEmpty(restaurantAddress)) {
                event.put("restaurant_address", restaurantAddress);
            }
            String driverName = "";
            String driverPhone = "";

            JSONObject driverObj = order.optJSONObject("driver");
            if (driverObj != null) {
                driverName = firstNonEmpty(driverObj.optString("name"), driverObj.optString("driver_name"), driverObj.optString("full_name"));
                driverPhone = firstNonEmpty(driverObj.optString("phone"), driverObj.optString("contact"), driverObj.optString("mobile"), driverObj.optString("phone_number"));
                event.put("driver_name", driverName);
                event.put("driver_phone", driverPhone);
                event.put("driver_avatar", firstNonEmpty(driverObj.optString("avatar"), driverObj.optString("image"), driverObj.optString("photo")));
            } else {
                driverName = firstNonEmpty(order.optString("driver_name"), order.optString("rider_name"), order.optString("driver_fullname"));
                driverPhone = firstNonEmpty(order.optString("driver_phone"), order.optString("driver_contact"), order.optString("driver_phone_number"), order.optString("rider_phone"));
                event.put("driver_name", driverName);
                event.put("driver_phone", driverPhone);
                event.put("driver_avatar", firstNonEmpty(order.optString("driver_avatar"), order.optString("driver_image")));
            }

            // Use the sanitized restaurant/driver fields for a readable timeline message.
            event.put("message", getStatusMessage(status, restaurantName, driverName, driverPhone));
        } catch (Exception ignored) {
        }
        return event;
    }

    // Try to extract a reasonable epoch millis for the order's last update or creation time.
    private static long extractOrderTimestamp(JSONObject order) {
        if (order == null) return System.currentTimeMillis();
        // If server provides numeric timestamps
        long v = order.optLong("status_updated_at", 0L);
        if (v > 0) return normalizeTimestamp(v);
        v = order.optLong("updated_at", 0L);
        if (v > 0) return normalizeTimestamp(v);
        v = order.optLong("created_at", 0L);
        if (v > 0) return normalizeTimestamp(v);

        // Try string timestamps like "2026-05-18 21:54:46"
        String s = firstNonEmpty(order.optString("status_updated_at"), order.optString("updated_at"), order.optString("created_at"));
        if (!TextUtils.isEmpty(s)) {
            long parsed = parseTimestampString(s);
            if (parsed > 0) return parsed;
        }

        return System.currentTimeMillis();
    }

    private static long normalizeTimestamp(long ts) {
        // Heuristic: if timestamp looks like seconds (10 digits), convert to millis
        if (ts > 0 && ts < 10000000000L) {
            return ts * 1000L;
        }
        return ts;
    }

    private static long parseTimestampString(String s) {
        if (TextUtils.isEmpty(s)) return 0L;
        String normalized = s.trim();
        if (normalized.endsWith("Z")) {
            normalized = normalized.substring(0, normalized.length() - 1) + "+0000";
        } else {
            normalized = normalized.replaceAll("([+-]\\d\\d):(\\d\\d)$", "$1$2");
        }

        String[] patterns = new String[]{
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        };
        for (String p : patterns) {
            try {
                SimpleDateFormat fmt = new SimpleDateFormat(p, Locale.US);
                fmt.setLenient(true);
                if (p.endsWith("Z")) {
                    fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                return fmt.parse(normalized).getTime();
            } catch (Exception ignored) {
            }
        }
        // If it's numeric string
        try {
            long v = Long.parseLong(s);
            return normalizeTimestamp(v);
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private static String getStatusMessage(String status, String restaurant, String driverName, String driverPhone) {
        switch (status) {
            case Constants.STATUS_ACCEPTED:
                if (!TextUtils.isEmpty(driverName) || !TextUtils.isEmpty(driverPhone)) {
                    String who = TextUtils.isEmpty(driverName) ? "Driver" : driverName;
                    return TextUtils.isEmpty(driverPhone)
                            ? who + " accepted your order from " + restaurant + "."
                            : who + " accepted your order from " + restaurant + ". Contact: " + driverPhone;
                }
                return "Restaurant accepted your order from " + restaurant + ".";
            case Constants.STATUS_PREPARING:
                return "Your order from " + restaurant + " is being prepared.";
            case Constants.STATUS_PICKED_UP:
                return "Driver accepted and picked up your order from " + restaurant + ".";
            case Constants.STATUS_OUT_FOR_DELIVERY:
                return "Driver is on the way with your order from " + restaurant + ".";
            case Constants.STATUS_DELIVERED:
                return "Your order from " + restaurant + " has been delivered. Enjoy your meal!";
            default:
                return "";
        }
    }

    private static JSONArray toSortedArray(Iterable<JSONObject> events) {
        List<JSONObject> list = new ArrayList<>();
        for (JSONObject event : events) {
            if (event != null) {
                list.add(event);
            }
        }
        Collections.sort(list, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject left, JSONObject right) {
                long leftCreatedAt = left.optLong("created_at", 0L);
                long rightCreatedAt = right.optLong("created_at", 0L);
                int createdAtCompare = Long.compare(leftCreatedAt, rightCreatedAt);
                if (createdAtCompare != 0) {
                    return createdAtCompare;
                }
                return left.optString("event_key", "").compareTo(right.optString("event_key", ""));
            }
        });

        JSONArray array = new JSONArray();
        for (JSONObject event : list) {
            array.put(event);
        }
        return array;
    }

    private static JSONArray normalizeHistory(JSONArray history, Set<String> dismissedKeys, boolean defaultRead) {
        Map<String, JSONObject> deduped = new LinkedHashMap<>();
        if (history == null) {
            return new JSONArray();
        }

        for (int i = 0; i < history.length(); i++) {
            JSONObject event = history.optJSONObject(i);
            if (event == null) continue;
            JSONObject normalized = normalizeEvent(event, defaultRead);
            String eventKey = getEventKey(normalized, i);
            if (TextUtils.isEmpty(eventKey) || dismissedKeys.contains(eventKey)) {
                continue;
            }
            deduped.put(eventKey, normalized);
        }

        return toSortedArray(deduped.values());
    }

    private static JSONObject normalizeEvent(JSONObject event, boolean defaultRead) {
        JSONObject copy = copyJson(event);
        String eventKey = getEventKey(copy, 0);
        if (!TextUtils.isEmpty(eventKey)) {
            try {
                copy.put("event_key", eventKey);
            } catch (Exception ignored) {
            }
        }
        if (!copy.has("read")) {
            try {
                copy.put("read", defaultRead);
            } catch (Exception ignored) {
            }
        }
        if (!copy.has("type")) {
            try {
                copy.put("type", copy.optInt("order_id", -1) > 0 ? TYPE_ORDER : TYPE_MESSAGE);
            } catch (Exception ignored) {
            }
        }
        return copy;
    }

    private static JSONObject mergeEvent(JSONObject existing, JSONObject incoming) {
        JSONObject merged = copyJson(existing);
        try {
            JSONArray keys = incoming.names();
            for (int i = 0; keys != null && i < keys.length(); i++) {
                String key = keys.optString(i, "");
                if (TextUtils.isEmpty(key)) {
                    continue;
                }
                if ("read".equals(key)) {
                    continue;
                }
                merged.put(key, incoming.opt(key));
            }
            merged.put("read", existing.optBoolean("read", false) || incoming.optBoolean("read", false));
        } catch (Exception ignored) {
        }
        return merged;
    }

    private static void ensureMigrated(Context context, int userId) {
        SharedPreferences prefs = getPrefs(context);
        String historyKey = historyKey(userId);
        String dismissedKey = dismissedKey(userId);
        if (prefs.contains(historyKey) && prefs.contains(dismissedKey)) {
            return;
        }

        SharedPreferences legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;

        if (!prefs.contains(historyKey)) {
            String legacyHistory = legacyPrefs.getString(LEGACY_HISTORY_KEY, null);
            if (!TextUtils.isEmpty(legacyHistory)) {
                JSONArray history = parseArray(legacyHistory);
                editor.putString(historyKey, normalizeHistory(history, new LinkedHashSet<>(), true).toString());
                changed = true;
            }
        }

        if (!prefs.contains(dismissedKey)) {
            String legacyDismissed = legacyPrefs.getString(LEGACY_DISMISSED_KEY, null);
            if (!TextUtils.isEmpty(legacyDismissed)) {
                editor.putString(dismissedKey, legacyDismissed);
                changed = true;
            }
        }

        if (changed) {
            editor.apply();
        }
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String historyKey(int userId) {
        return KEY_PREFIX_HISTORY + userId;
    }

    private static String dismissedKey(int userId) {
        return KEY_PREFIX_DISMISSED + userId;
    }

    private static JSONArray parseArray(String raw) {
        try {
            return new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static Set<String> parseStringSet(String raw) {
        Set<String> keys = new LinkedHashSet<>();
        JSONArray array = parseArray(raw);
        for (int i = 0; i < array.length(); i++) {
            String key = array.optString(i, "");
            if (!TextUtils.isEmpty(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static JSONObject copyJson(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static String getEventKey(JSONObject event, int fallbackIndex) {
        if (event == null) return "";
        String key = event.optString("event_key", "");
        if (!TextUtils.isEmpty(key)) return key;
        int orderId = event.optInt("order_id", -1);
        String status = event.optString("status", "");
        long createdAt = event.optLong("created_at", -1L);
        if (orderId > 0 && !TextUtils.isEmpty(status)) {
            return createdAt > 0 ? orderId + "_" + status + "_" + createdAt : orderId + "_" + status;
        }
        return "legacy_" + fallbackIndex + "_" + event.optString("title", "") + "_" + event.optString("message", "");
    }

    private static String getGroupKey(JSONObject event, int fallbackIndex) {
        int orderId = event.optInt("order_id", -1);
        if (orderId > 0) {
            return "order_" + orderId;
        }
        String type = event.optString("type", "");
        if (TYPE_PROMOTION.equalsIgnoreCase(type)) {
            return "promotion_" + getEventKey(event, fallbackIndex);
        }
        return "message_" + getEventKey(event, fallbackIndex);
    }

    private static boolean isPromotion(JSONObject event) {
        return event != null && TYPE_PROMOTION.equalsIgnoreCase(event.optString("type", ""));
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (!TextUtils.isEmpty(value) && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "";
    }

    private static final class GroupBuilder {
        private final String groupKey;
        private final List<JSONObject> events = new ArrayList<>();
        private final String type;
        private final int orderId;
        private final String restaurantName;
        private final String restaurantAddress;

        GroupBuilder(String groupKey, JSONObject seed) {
            this.groupKey = groupKey;
            this.orderId = seed.optInt("order_id", -1);
            String explicitType = firstNonEmpty(seed.optString("type"));
            if (!TextUtils.isEmpty(explicitType)) {
                this.type = explicitType;
            } else if (this.orderId > 0) {
                this.type = TYPE_ORDER;
            } else {
                this.type = TYPE_MESSAGE;
            }
            String name = firstNonEmpty(seed.optString("restaurant_name"), seed.optString("restaurant"));
            String address = firstNonEmpty(seed.optString("restaurant_address"), seed.optString("restaurant_address"));
            JSONObject restObj = seed.optJSONObject("restaurant");
            if (restObj != null) {
                name = firstNonEmpty(restObj.optString("name"), name);
                address = firstNonEmpty(restObj.optString("address"), address);
            }
            if (TextUtils.isEmpty(name) || name.trim().startsWith("{") || name.trim().startsWith("[")) {
                name = "Restaurant";
            }
            this.restaurantName = name;
            this.restaurantAddress = address;
        }

        void add(JSONObject event) {
            events.add(event);
        }

        NotificationGroup build() {
            Collections.sort(events, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject left, JSONObject right) {
                    return Long.compare(left.optLong("created_at", 0L), right.optLong("created_at", 0L));
                }
            });

            JSONObject latest = events.get(events.size() - 1);
            boolean unread = false;
            long latestTimestamp = latest.optLong("created_at", 0L);
            for (JSONObject event : events) {
                unread = unread || !event.optBoolean("read", false);
            }

            String title = orderId > 0 ? "Order #" + orderId : firstNonEmpty(latest.optString("title"), "Promotion");
            String latestStatus = latest.optString("status", "");
            // For orders show restaurant address (or name if address missing), not raw JSON
            String subtitle = orderId > 0 ? (TextUtils.isEmpty(restaurantAddress) ? restaurantName : restaurantAddress) : firstNonEmpty(latest.optString("title"), "Promotion");
            String latestMessage = latest.optString("message", "");
            // If message is missing or looks like raw JSON, prefer a friendly status message instead
            if (TextUtils.isEmpty(latestMessage) || latestMessage.trim().startsWith("{") || latestMessage.trim().startsWith("[")) {
                latestMessage = getStatusMessage(
                    latestStatus,
                    restaurantName,
                    firstNonEmpty(latest.optString("driver_name"), latest.optString("rider_name"), latest.optString("driver_fullname")),
                    firstNonEmpty(latest.optString("driver_phone"), latest.optString("driver_contact"), latest.optString("driver_phone_number"), latest.optString("rider_phone"))
                );
            }

            return new NotificationGroup(groupKey, orderId, type, title, subtitle, latestStatus, latestMessage, latestTimestamp, unread, new ArrayList<>(events));
        }
    }
}