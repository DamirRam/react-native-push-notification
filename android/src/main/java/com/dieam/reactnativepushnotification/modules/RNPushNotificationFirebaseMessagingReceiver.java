package com.dieam.reactnativepushnotification.modules;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.facebook.react.HeadlessJsTaskService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;

import io.invertase.firebase.app.ReactNativeFirebaseApp;
import io.invertase.firebase.common.ReactNativeFirebaseEventEmitter;
import io.invertase.firebase.common.SharedUtils;
import io.invertase.firebase.messaging.ReactNativeFirebaseMessagingHeadlessService;
import io.invertase.firebase.messaging.ReactNativeFirebaseMessagingSerializer;
import io.invertase.firebase.messaging.ReactNativeFirebaseMessagingStoreHelper;

import static com.dieam.reactnativepushnotification.modules.RNPushNotification.LOG_TAG;

public class RNPushNotificationFirebaseMessagingReceiver extends BroadcastReceiver {

  private static final String RECEIVE = "com.google.android.c2dm.intent.RECEIVE";
  static HashMap<String, RemoteMessage> notifications = new HashMap<>();

  private RNReceivedMessageHandler mMessageReceivedHandler;

  public RNPushNotificationFirebaseMessagingReceiver() {
    this.mMessageReceivedHandler = new RNReceivedMessageHandler();
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(LOG_TAG, "remote message received");
    if (ReactNativeFirebaseApp.getApplicationContext() == null) {
      ReactNativeFirebaseApp.setApplicationContext(context.getApplicationContext());
    }

    RemoteMessage remoteMessage = new RemoteMessage(intent.getExtras());
    RemoteMessage.Notification remoteNotification = remoteMessage.getNotification();

    mMessageReceivedHandler.handleReceivedMessage(remoteMessage, context);

    if (intent.getAction().equals(RECEIVE) && remoteNotification != null) {
      abortBroadcast();

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
