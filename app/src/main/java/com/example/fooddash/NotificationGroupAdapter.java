package com.example.fooddash;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class NotificationGroupAdapter extends ListAdapter<NotificationStore.NotificationGroup, NotificationGroupAdapter.NotificationViewHolder> {

    public interface Listener {
        void onGroupClicked(NotificationStore.NotificationGroup group);
    }

    private static final String[] TIMELINE_STAGES = new String[] {
            Constants.STATUS_ACCEPTED,
            Constants.STATUS_PREPARING,
            Constants.STATUS_PICKED_UP,
            Constants.STATUS_OUT_FOR_DELIVERY,
            Constants.STATUS_DELIVERED
    };

    private final Listener listener;

    public NotificationGroupAdapter(Listener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification_group, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        NotificationStore.NotificationGroup group = getItem(position);
        holder.bind(group, listener);
    }

    static final class NotificationViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final TextView titleView;
        private final TextView subtitleView;
        private final TextView messageView;
        private final TextView timestampView;
        private final TextView unreadBadgeView;
        private final LinearLayout timelineContainer;
        private final LinearLayout driverContainer;
        private final TextView stageSummaryView;

        NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.notificationGroupCard);
            titleView = itemView.findViewById(R.id.notificationGroupTitle);
            subtitleView = itemView.findViewById(R.id.notificationGroupSubtitle);
            messageView = itemView.findViewById(R.id.notificationGroupMessage);
            timestampView = itemView.findViewById(R.id.notificationGroupTimestamp);
            unreadBadgeView = itemView.findViewById(R.id.notificationGroupUnreadBadge);
            timelineContainer = itemView.findViewById(R.id.notificationTimelineContainer);
            driverContainer = itemView.findViewById(R.id.notificationDriverContainer);
            stageSummaryView = itemView.findViewById(R.id.notificationStageSummary);
        }

        void bind(NotificationStore.NotificationGroup group, Listener listener) {
            Context context = itemView.getContext();
            titleView.setText(group.title);
            subtitleView.setText(group.subtitle);
            messageView.setText(group.latestMessage);
            messageView.setVisibility(group.latestMessage == null || group.latestMessage.trim().isEmpty() ? View.GONE : View.VISIBLE);
            timestampView.setText(NotificationStore.formatTimestamp(context, group.latestTimestamp));
            unreadBadgeView.setVisibility(group.unread ? View.VISIBLE : View.GONE);
            cardView.setStrokeColor(ContextCompat.getColor(context, group.unread ? R.color.primary_blue : R.color.light_gray));
            cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white));
            // Show notifications in a concise "message" form instead of expanded timeline.
            // Hide timeline and stage summary to present a single message-style card.
            timelineContainer.removeAllViews();
            timelineContainer.setVisibility(View.GONE);
            stageSummaryView.setVisibility(View.GONE);

            // Show a dedicated driver details block when driver information is available.
            driverContainer.removeAllViews();
            bindDriverDetails(context, group);

            cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onGroupClicked(group);
                }
            });
        }

        private View createTimelineRow(Context context, NotificationStore.NotificationGroup group, String status, int latestStageRank) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 8, 0, 8);

            TextView marker = new TextView(context);
            int stageRank = NotificationStore.getStageRank(status);
            boolean completed = latestStageRank >= stageRank && stageRank > 0;
            marker.setText(completed ? "\u2713" : "\u2022");
            marker.setTextColor(Color.WHITE);
            marker.setTypeface(Typeface.DEFAULT_BOLD);
            marker.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24));
            markerParams.setMargins(0, dp(context, 2), dp(context, 12), 0);
            marker.setLayoutParams(markerParams);
            marker.setBackground(createCircleDrawable(context, completed ? R.color.primary_blue : R.color.light_gray));

            LinearLayout body = new LinearLayout(context);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView label = new TextView(context);
            label.setText(NotificationStore.getStageLabel(status));
            label.setTextColor(ContextCompat.getColor(context, R.color.black));
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setTextSize(14f);

            TextView time = new TextView(context);
            time.setTextColor(ContextCompat.getColor(context, R.color.text_gray));
            time.setTextSize(12f);

            long eventTimestamp = findStageTimestamp(group, status);
            time.setText(eventTimestamp > 0 ? NotificationStore.formatTimestamp(context, eventTimestamp) : "Pending");

            body.addView(label);
            body.addView(time);

            row.addView(marker);
            row.addView(body);
            return row;
        }

        private void bindDriverDetails(Context context, NotificationStore.NotificationGroup group) {
            int latestStageRank = NotificationStore.getStageRank(group.latestStatus);
            // Show rider/driver details starting from order acceptance onward.
            if (latestStageRank < NotificationStore.getStageRank(Constants.STATUS_ACCEPTED)) {
                driverContainer.setVisibility(View.GONE);
                return;
            }

            JSONObject latest = group.getLatestEvent();
            String driverName = firstNonEmpty(latest.optString("driver_name"), latest.optString("rider_name"));
            String driverPhone = firstNonEmpty(latest.optString("driver_phone"), latest.optString("driver_contact"));
            String driverAvatar = firstNonEmpty(latest.optString("driver_avatar"), latest.optString("driver_image"));

            if (driverName.isEmpty() && driverPhone.isEmpty() && driverAvatar.isEmpty()) {
                driverContainer.setVisibility(View.GONE);
                return;
            }

            driverContainer.setVisibility(View.VISIBLE);
            driverContainer.setOrientation(LinearLayout.HORIZONTAL);
            driverContainer.setPadding(0, dp(context, 10), 0, 0);

            ImageView avatar = new ImageView(context);
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(context, 40), dp(context, 40));
            avatarParams.setMargins(0, 0, dp(context, 10), 0);
            avatar.setLayoutParams(avatarParams);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (!driverAvatar.isEmpty()) {
                Glide.with(context).load(driverAvatar).placeholder(R.drawable.ic_launcher_foreground).into(avatar);
            } else {
                avatar.setImageResource(R.drawable.ic_launcher_foreground);
            }

            LinearLayout info = new LinearLayout(context);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView name = new TextView(context);
            name.setText(driverName.isEmpty() ? "Driver" : driverName);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setTextColor(ContextCompat.getColor(context, R.color.black));

            TextView phone = new TextView(context);
            phone.setText(driverPhone.isEmpty() ? "Driver assigned" : "Call: " + driverPhone);
            phone.setTextColor(ContextCompat.getColor(context, R.color.primary_blue));
            phone.setTextSize(12f);
            phone.setClickable(!driverPhone.isEmpty());
            if (!driverPhone.isEmpty()) {
                phone.setPaintFlags(phone.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                phone.setOnClickListener(v -> {
                    Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                    dialIntent.setData(Uri.parse("tel:" + driverPhone.trim()));
                    context.startActivity(dialIntent);
                });
            }

            info.addView(name);
            info.addView(phone);

            driverContainer.addView(avatar);
            driverContainer.addView(info);
        }

        private long findStageTimestamp(NotificationStore.NotificationGroup group, String status) {
            for (JSONObject event : group.events) {
                if (status.equals(event.optString("status", ""))) {
                    return event.optLong("created_at", 0L);
                }
            }
            return 0L;
        }

        private GradientDrawable createCircleDrawable(Context context, int colorRes) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(ContextCompat.getColor(context, colorRes));
            return drawable;
        }

        private int dp(Context context, int value) {
            float density = context.getResources().getDisplayMetrics().density;
            return Math.round(value * density);
        }

        private String firstNonEmpty(String... values) {
            for (String value : values) {
                if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) {
                    return value.trim();
                }
            }
            return "";
        }
    }

    private static final DiffUtil.ItemCallback<NotificationStore.NotificationGroup> DIFF_CALLBACK = new DiffUtil.ItemCallback<NotificationStore.NotificationGroup>() {
        @Override
        public boolean areItemsTheSame(@NonNull NotificationStore.NotificationGroup oldItem, @NonNull NotificationStore.NotificationGroup newItem) {
            return oldItem.groupKey.equals(newItem.groupKey);
        }

        @Override
        public boolean areContentsTheSame(@NonNull NotificationStore.NotificationGroup oldItem, @NonNull NotificationStore.NotificationGroup newItem) {
            return oldItem.latestTimestamp == newItem.latestTimestamp
                    && oldItem.unread == newItem.unread
                    && oldItem.events.size() == newItem.events.size()
                    && safeEquals(oldItem.title, newItem.title)
                    && safeEquals(oldItem.subtitle, newItem.subtitle)
                    && safeEquals(oldItem.latestStatus, newItem.latestStatus)
                    && safeEquals(oldItem.latestMessage, newItem.latestMessage);
        }

        private boolean safeEquals(String left, String right) {
            if (left == null) {
                return right == null;
            }
            return left.equals(right);
        }
    };
}