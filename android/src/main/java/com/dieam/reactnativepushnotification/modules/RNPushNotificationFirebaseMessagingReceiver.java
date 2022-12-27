package com.dieam.reactnativepushnotification.modules;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.facebook.react.HeadlessJsTaskService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Queue;
import android.util.Log;

import io.invertase.firebase.app.ReactNativeFirebaseApp;
import io.invertase.firebase.common.ReactNativeFirebaseEventEmitter;
import io.invertase.firebase.common.SharedUtils;
import io.invertase.firebase.messaging.ReactNativeFirebaseMessagingHeadlessService;
import io.invertase.firebase.messaging.ReactNativeFirebaseMessagingSerializer;
import io.invertase.firebase.messaging.ReactNativeFirebaseMessagingStoreHelper;

import static com.dieam.reactnativepushnotification.modules.RNPushNotification.LOG_TAG;

public class RNPushNotificationFirebaseMessagingReceiver extends BroadcastReceiver {

  private static final String RECEIVE = "com.google.android.c2dm.intent.RECEIVE";
  private static final Queue<String> recentlyReceivedMessageIds = new ArrayDeque(20);

  static HashMap<String, RemoteMessage> notifications = new HashMap<>();

  private RNReceivedMessageHandler mMessageReceivedHandler;

  public RNPushNotificationFirebaseMessagingReceiver() {
    this.mMessageReceivedHandler = new RNReceivedMessageHandler();
  }

  private boolean alreadyReceivedMessage(String messageId) {
    if (TextUtils.isEmpty(messageId)) {
      return false;
    } else if (recentlyReceivedMessageIds.contains(messageId)) {
      Log.d(LOG_TAG, "Received duplicate message:".concat(messageId));
      
      return true;
    } else {
      if (recentlyReceivedMessageIds.size() >= 20) {
        recentlyReceivedMessageIds.remove();
      }

      recentlyReceivedMessageIds.add(messageId);
      return false;
    }
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(LOG_TAG, "remote message received");
    if (ReactNativeFirebaseApp.getApplicationContext() == null) {
      ReactNativeFirebaseApp.setApplicationContext(context.getApplicationContext());
    }

    RemoteMessage remoteMessage = new RemoteMessage(intent.getExtras());
    RemoteMessage.Notification remoteNotification = remoteMessage.getNotification();

    if (intent.getAction().equals(RECEIVE) && remoteNotification != null) {
      abortBroadcast();

      String messageId = intent.getStringExtra("google.message_id");

      if (!this.alreadyReceivedMessage(messageId)) {
        mMessageReceivedHandler.handleReceivedMessage(remoteMessage, context);
      }

    //---------------------------------------
      //@react-native-firebase-messaging code start

      ReactNativeFirebaseEventEmitter emitter = ReactNativeFirebaseEventEmitter.getSharedInstance();
      // Add a RemoteMessage if the message contains a notification payload
      if (remoteNotification != null) {
        notifications.put(remoteMessage.getMessageId(), remoteMessage);
        ReactNativeFirebaseMessagingStoreHelper.getInstance().getMessagingStore().storeFirebaseMessage(remoteMessage);
      }

      //  |-> ---------------------
      //      App in Foreground
      //   ------------------------
      if (SharedUtils.isAppInForeground(context)) {
        emitter.sendEvent(ReactNativeFirebaseMessagingSerializer.remoteMessageToEvent(remoteMessage, false));
        return;
      }


      //  |-> ---------------------
      //    App in Background/Quit
      //   ------------------------

      try {
        Intent backgroundIntent = new Intent(context, ReactNativeFirebaseMessagingHeadlessService.class);
        backgroundIntent.putExtra("message", remoteMessage);
        ComponentName name = context.startService(backgroundIntent);
        if (name != null) {
          HeadlessJsTaskService.acquireWakeLockNow(context);
        }
      } catch (IllegalStateException ex) {
        // By default, data only messages are "default" priority and cannot trigger Headless tasks
        Log.e(
                LOG_TAG,
                "Background messages only work if the message priority is set to 'high'",
                ex
        );
      }
      //@react-native-firebase-messaging code end
      //----------------------------------------
    }
  }
}
