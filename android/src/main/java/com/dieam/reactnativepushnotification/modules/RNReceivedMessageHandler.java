package com.dieam.reactnativepushnotification.modules;

import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableNativeMap;
import com.google.firebase.messaging.RemoteMessage;

import android.app.Application;
import android.os.Bundle;
import android.content.Context;
import android.util.Log;
import android.net.Uri;
import androidx.core.app.NotificationCompat;

import java.util.Map;
import java.security.SecureRandom;

import static com.dieam.reactnativepushnotification.modules.RNPushNotification.LOG_TAG;

public class RNReceivedMessageHandler {

    public void handleReceivedMessage(RemoteMessage message, Context context) {
        String from = message.getFrom();
        RemoteMessage.Notification remoteNotification = message.getNotification();
        final Bundle bundle = new Bundle();
        // Putting it from remoteNotification first so it can be overriden if message
        // data has it
        if (remoteNotification != null) {
            // ^ It's null when message is from GCM
            RNPushNotificationConfig config = new RNPushNotificationConfig(context);

            String title = getLocalizedString(remoteNotification.getTitle(), remoteNotification.getTitleLocalizationKey(), remoteNotification.getTitleLocalizationArgs(), context);
            String body = getLocalizedString(remoteNotification.getBody(), remoteNotification.getBodyLocalizationKey(), remoteNotification.getBodyLocalizationArgs(), context);

            bundle.putString("title", title);
            bundle.putString("message", body);
            bundle.putString("sound", remoteNotification.getSound());
            bundle.putString("color", remoteNotification.getColor());
            bundle.putString("tag", remoteNotification.getTag());

            if(remoteNotification.getIcon() != null) {
              bundle.putString("smallIcon", remoteNotification.getIcon());
            } else {
              bundle.putString("smallIcon", "ic_notification");
            }

            if(remoteNotification.getChannelId() != null) {
              bundle.putString("channelId", remoteNotification.getChannelId());
            }
            else {
              bundle.putString("channelId", config.getNotificationDefaultChannelId(context));
            }

            Integer visibilty = remoteNotification.getVisibility();
            String visibilityString = "private";

            if (visibilty != null) {
                switch (visibilty) {
                    case NotificationCompat.VISIBILITY_PUBLIC:
                        visibilityString = "public";
                        break;
                    case NotificationCompat.VISIBILITY_SECRET:
                        visibilityString = "secret";
                        break;
                }
            }

            bundle.putString("visibility", visibilityString);

            Integer priority = remoteNotification.getNotificationPriority();
            String priorityString = "high";

            if (priority != null) {
              switch (priority) {
                  case NotificationCompat.PRIORITY_MAX:
                      priorityString = "max";
                      break;
                  case NotificationCompat.PRIORITY_LOW:
                      priorityString = "low";
                      break;
                  case NotificationCompat.PRIORITY_MIN:
                      priorityString = "min";
                      break;
                  case NotificationCompat.PRIORITY_DEFAULT:
                      priorityString = "default";
                      break;
              }
            }

            bundle.putString("priority", priorityString);

            Uri uri = remoteNotification.getImageUrl();

            if(uri != null) {
                String imageUrl = uri.toString();

                bundle.putString("bigPictureUrl", imageUrl);
                bundle.putString("largeIconUrl", imageUrl);
            }
        }

        Bundle dataBundle = new Bundle();
        Map<String, String> notificationData = message.getData();

        for(Map.Entry<String, String> entry : notificationData.entrySet()) {
            dataBundle.putString(entry.getKey(), entry.getValue());
        }

        bundle.putParcelable("data", dataBundle);

        Log.v(LOG_TAG, "onMessageReceived: " + bundle);

        handleRemotePushNotification(context, bundle);
    }

    private void handleRemotePushNotification(Context context, Bundle bundle) {

        // If notification ID is not provided by the user for push notification, generate one at random
        if (bundle.getString("id") == null) {
            SecureRandom randomNumberGenerator = new SecureRandom();
            bundle.putString("id", String.valueOf(randomNumberGenerator.nextInt()));
        }

        Application applicationContext = (Application) context.getApplicationContext();

        RNPushNotificationConfig config = new RNPushNotificationConfig(context);
        RNPushNotificationHelper pushNotificationHelper = new RNPushNotificationHelper(applicationContext);

        pushNotificationHelper.createDefaultChannel(context, bundle.getString("channelId"));

        boolean isForeground = pushNotificationHelper.isApplicationInForeground();

        bundle.putBoolean("foreground", isForeground);
        bundle.putBoolean("userInteraction", false);

        if (config.getNotificationForeground() || !isForeground) {
            Log.v(LOG_TAG, "sendNotification: " + bundle);

            pushNotificationHelper.sendToNotificationCentre(bundle);
        }
    }

    private String getLocalizedString(String text, String locKey, String[] locArgs, Context context) {
        if(text != null) {
          return text;
        }

        String packageName = context.getPackageName();

        String result = null;

        if (locKey != null) {
            int id = context.getResources().getIdentifier(locKey, "string", packageName);
            if (id != 0) {
                if (locArgs != null) {
                    result = context.getResources().getString(id, (Object[]) locArgs);
                } else {
                    result = context.getResources().getString(id);
                }
            }
        }

        return result;
    }
}
