package com.dieam.reactnativepushnotification.modules;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.MessagingAnalytics;
import com.google.firebase.messaging.RemoteMessage;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;

import java.util.ArrayDeque;
import java.util.Queue;

import static com.dieam.reactnativepushnotification.modules.RNPushNotification.LOG_TAG;

public class RNPushNotificationListenerService extends FirebaseMessagingService {

    private RNReceivedMessageHandler mMessageReceivedHandler;
    private FirebaseMessagingService mFirebaseServiceDelegate;
    private static final Queue<String> recentlyReceivedMessageIds = new ArrayDeque(10);

    public RNPushNotificationListenerService() {
        super();
        this.mMessageReceivedHandler = new RNReceivedMessageHandler(this);
    }

    public RNPushNotificationListenerService(FirebaseMessagingService delegate) {
        super();
        this.mFirebaseServiceDelegate = delegate;
        this.mMessageReceivedHandler = new RNReceivedMessageHandler(delegate);
    }

    @Override
    public void onNewToken(String token) {
        final String deviceToken = token;
        final FirebaseMessagingService serviceRef = (this.mFirebaseServiceDelegate == null) ? this : this.mFirebaseServiceDelegate;
        Log.d(LOG_TAG, "Refreshed token: " + deviceToken);

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                // Construct and load our normal React JS code bundle
                final ReactInstanceManager mReactInstanceManager = ((ReactApplication)serviceRef.getApplication()).getReactNativeHost().getReactInstanceManager();
                ReactContext context = mReactInstanceManager.getCurrentReactContext();
                // If it's constructed, send a notification
                if (context != null) {
                    handleNewToken((ReactApplicationContext) context, deviceToken);
                } else {
                    // Otherwise wait for construction, then send the notification
                    mReactInstanceManager.addReactInstanceEventListener(new ReactInstanceManager.ReactInstanceEventListener() {
                        public void onReactContextInitialized(ReactContext context) {
                            handleNewToken((ReactApplicationContext) context, deviceToken);
                            mReactInstanceManager.removeReactInstanceEventListener(this);
                        }
                    });
                    if (!mReactInstanceManager.hasStartedCreatingInitialContext()) {
                        // Construct it in the background
                        mReactInstanceManager.createReactContextInBackground();
                    }
                }
            }
        });
    }

    private void handleNewToken(ReactApplicationContext context, String token) {
        RNPushNotificationJsDelivery jsDelivery = new RNPushNotificationJsDelivery(context);

        WritableMap params = Arguments.createMap();
        params.putString("deviceToken", token);
        jsDelivery.sendEvent("remoteNotificationsRegistered", params);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        // messages received to handleIntent method on this class
    }

    @Override
    public void handleIntent(Intent intent) {
        String action = intent.getAction();

        if ("com.google.android.c2dm.intent.RECEIVE".equals(action) || "com.google.firebase.messaging.RECEIVE_DIRECT_BOOT".equals(action)) {
            this.handleMessageIntent(intent);

            Log.i(LOG_TAG, "message received to handleIntent method");
        }
    }

    private void handleMessageIntent(Intent intent) {
        String messageId = intent.getStringExtra("google.message_id");

        if (!this.alreadyReceivedMessage(messageId)) {
            this.passMessageIntentToSdk(intent);
        }
    }

    private void passMessageIntentToSdk(Intent intent) {
        String messageType = intent.getStringExtra("message_type");

        if (messageType == null) {
            messageType = "gcm";
        }

        if(messageType.hashCode() == 102161 && messageType.equals("gcm")) {
          Bundle data = intent.getExtras();
          MessagingAnalytics.logNotificationReceived(intent);

          if(data != null) {
            data.remove("androidx.content.wakelockid");
            mMessageReceivedHandler.handleReceivedMessage(new RemoteMessage(data));
          }
        }
    }

    private boolean alreadyReceivedMessage(String messageId) {
        if (TextUtils.isEmpty(messageId)) {
            return false;
        } else if (recentlyReceivedMessageIds.contains(messageId)) {
            return true;
        } else {
            if (recentlyReceivedMessageIds.size() >= 10) {
                recentlyReceivedMessageIds.remove();
            }

            recentlyReceivedMessageIds.add(messageId);
            return false;
        }
    }
}
