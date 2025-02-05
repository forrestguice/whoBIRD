/**
    Copyright (C) 2024 Forrest Guice
    This file is part of whoBIRD.

    whoBIRD is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    whoBIRD is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with whoBIRD.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.tensorflow.lite.examples.soundclassifier;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

public class BirdService extends Service
{
    public static String LOGTAG = "BirdService";

    public static String ACTION_START = "start";
    public static String ACTION_STOP = "stop";
    public static String ACTION_EXIT = "exit";

    public static final int FOREGROUND_SERVICE_TYPE_MICROPHONE;
    static {
        if (Build.VERSION.SDK_INT >= 30) {
            FOREGROUND_SERVICE_TYPE_MICROPHONE = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
        } else FOREGROUND_SERVICE_TYPE_MICROPHONE = 128;
    }

    private SoundClassifier soundClassifier = null;
    synchronized private void initSoundClassifier(@Nullable Runnable after)
    {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() ->
        {
            if (soundClassifier == null) {
                soundClassifier = new SoundClassifier(this, ui, new SoundClassifier.Options());    // this might take a moment...
            }
            if (after != null) {
                handler.post(after);
            }
        });
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        ContextCompat.registerReceiver(BirdService.this, receiver, getIntentFilter(), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onDestroy()
    {
        unregisterReceiver(receiver);
        super.onDestroy();
    }

    /**
     * onStart
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        NotificationCompat.Builder notification = createMainNotification(this, getNotificationMessage(this, isRecording()), isRecording());
        ServiceCompat.startForeground(this, NOTIFICATION_MAIN, notification.build(), FOREGROUND_SERVICE_TYPE_MICROPHONE);   // we are obligated to startForeground within 5s
        handleAction(((intent != null) ? intent.getAction() : null));
        return START_NOT_STICKY;
    }

    protected void handleAction(String action)
    {
        if (action != null)
        {
            if (action.equals(ACTION_START))
            {
                Log.d(LOGTAG, "onStartCommand: " + action);
                startRecording();

            } else if (action.equals(ACTION_STOP)) {
                Log.d(LOGTAG, "onStartCommand: " + action);
                stopRecording();

            } else if (action.equals(ACTION_EXIT)) {
                Log.d(LOGTAG, "onStartCommand: " + action);
                exitService();

            } else {
                Log.w(LOGTAG, "onStartCommand: unrecognized action: " + action);
            }
        } else {
            Log.w(LOGTAG, "onStartCommand: null action");
            initSoundClassifier(null);
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(LOGTAG, "onReceive: " + intent);
            onStartCommand(intent, 0, -1);
        }
    };
    protected static IntentFilter getIntentFilter()
    {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_START);
        filter.addAction(ACTION_STOP);
        filter.addAction(ACTION_EXIT);
        return filter;
    }

    /**
     * onBind
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private final BirdServiceBinder binder = new BirdServiceBinder();
    public class BirdServiceBinder extends Binder {
        public BirdService getService() {
            return BirdService.this;
        }
    }

    /**
     * ServiceListener
     */
    public interface BirdServiceListener
    {
        SoundClassifierUI getUI();
        void onStateChanged(boolean isActive);
        void onExit();
    }

    private final ArrayList<BirdServiceListener> serviceListeners = new ArrayList<>();

    public void addServiceListener(BirdServiceListener listener) {
        serviceListeners.add(listener);
    }
    public void removeServiceListener(BirdServiceListener listener) {
        serviceListeners.remove(listener);
    }
    protected void signalStateChanged(boolean value) {
        for (BirdServiceListener listener : serviceListeners) {
            listener.onStateChanged(value);
        }
    }
    protected void signalOnExit() {
        for (BirdServiceListener listener : serviceListeners) {
            listener.onExit();
        }
    }

    public boolean isRecording() {
        return isRecording;
    }
    protected boolean isRecording = false;

    public void startRecording()
    {
        isRecording = true;
        NotificationCompat.Builder notification = createMainNotification_Running(this, getString(R.string.notification_message_listening));
        ServiceCompat.startForeground(this, NOTIFICATION_MAIN, notification.build(), FOREGROUND_SERVICE_TYPE_MICROPHONE);
        signalStateChanged(isRecording);

        initSoundClassifier(() -> {
            soundClassifier.start();
        });
    }

    public void stopRecording()
    {
        isRecording = false;
        if (soundClassifier != null) {
            soundClassifier.stop();
        }
        NotificationCompat.Builder notification = createMainNotification_Stopped(this, getString(R.string.notification_message_ready));
        ServiceCompat.startForeground(this, NOTIFICATION_MAIN, notification.build(), FOREGROUND_SERVICE_TYPE_MICROPHONE);
        signalStateChanged(isRecording);
    }

    public void exitService()
    {
        stopRecording();
        signalOnExit();
        stopForeground(true);
        stopSelf();
    }

    private final SoundClassifierUI ui = new SoundClassifierUI()
    {
        @Override
        public boolean isShowingProgress() {
            return isRecording();
        }

        @Override
        public boolean ignoreMeta() {
            for (BirdServiceListener listener : serviceListeners) {
                return listener.getUI().ignoreMeta();
            }
            return false;
        }

        @Override
        public void setLocationText(float lat, float lon) {
            for (BirdServiceListener listener : serviceListeners) {
                listener.getUI().setLocationText(lat, lon);
            }
        }

        @Override
        public void setPrimaryText(@NonNull String text)
        {
            String notificationText = text;
            if (notificationText.isEmpty()) {
                notificationText = getNotificationMessage(BirdService.this, isRecording());
            }
            updateNotification(NOTIFICATION_MAIN, createMainNotification(BirdService.this, notificationText, isRecording()).build());
            for (BirdServiceListener listener : serviceListeners) {
                listener.getUI().setPrimaryText(text);
            }
        }

        @Override
        public void setPrimaryText(float value, @NonNull String label)
        {
            String message = (label + "  " + Math.round(value * 100.0) + "%");   // TODO: i18n
            updateNotification(NOTIFICATION_MAIN, createMainNotification(BirdService.this, message, isRecording()).build());
            for (BirdServiceListener listener : serviceListeners) {
                listener.getUI().setPrimaryText(value, label);
            }
        }

        @Override
        public void setSecondaryText(@NonNull String text) {
            for (BirdServiceListener listener : serviceListeners) {
                listener.getUI().setSecondaryText(text);
            }
        }

        @Override
        public void setSecondaryText(float value, @NonNull String label) {
            for (BirdServiceListener listener : serviceListeners) {
                listener.getUI().setSecondaryText(value, label);
            }
        }

        @Override
        public boolean showImages() {
            for (BirdServiceListener listener : serviceListeners) {
                return listener.getUI().showImages();
            }
            return false;
        }

        @Nullable
        @Override
        public String getImageURL() {
            for (BirdServiceListener listener : serviceListeners) {
                return listener.getUI().getImageURL();
            }
            return null;
        }

        @Override
        public void showImage(@NonNull String label, @Nullable String url) {
            for (BirdServiceListener listener : serviceListeners) {
                listener.getUI().showImage(label, url);
            }
        }

        @Override
        public void hideImage() {
            for (BirdServiceListener listener : serviceListeners) {
                listener.getUI().hideImage();
            }
        }
    };

    /**
     * Notifications
     */
    public static final int NOTIFICATION_MAIN = -10;

    protected void updateNotification(int id, Notification notification)
    {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(id, notification);
    }

    private static NotificationCompat.Builder createMainNotification(Context context, String message)
    {
        NotificationCompat.Builder notification = createNotificationBuilder(context);
        notification.setContentTitle(context.getString(R.string.app_name))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSilent(true)
                .setContentIntent(getControlActivityPendingIntent(context))
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_mic_24dp)
                .setOngoing(true);

        return notification;
    }

    protected final NotificationCompat.Builder createMainNotification(Context context, String message, boolean isRunning) {
        return isRunning ? createMainNotification_Running(context, message)
                : createMainNotification_Stopped(context, message);
    }
    private static NotificationCompat.Builder createMainNotification_Running(Context context, String message)
    {
        NotificationCompat.Builder notification = createMainNotification(context, message);
        notification.addAction(R.drawable.ic_pause_24dp, context.getString(R.string.notification_action_stop), getServicePendingIntent(context, ACTION_STOP));
        notification.addAction(R.drawable.ic_view_24dp, context.getString(R.string.notification_action_list), getListActivityPendingIntent(context));
        notification.addAction(R.drawable.ic_close_24dp, context.getString(R.string.notification_action_exit), getServicePendingIntent(context, ACTION_EXIT));
        notification.setProgress(0, 0, true);
        return notification;
    }
    private static NotificationCompat.Builder createMainNotification_Stopped(Context context, String message)
    {
        NotificationCompat.Builder notification = createMainNotification(context, message);
        notification.addAction(R.drawable.ic_play_24dp, context.getString(R.string.notification_action_start), getServicePendingIntent(context, ACTION_START));
        notification.addAction(R.drawable.ic_view_24dp, context.getString(R.string.notification_action_list), getListActivityPendingIntent(context));
        notification.addAction(R.drawable.ic_close_24dp, context.getString(R.string.notification_action_exit), getServicePendingIntent(context, ACTION_EXIT));
        return notification;
    }

    protected static String getNotificationMessage(Context context, boolean isRecording) {
        return context.getString(isRecording ? R.string.notification_message_listening
                : R.string.notification_message_ready);
    }

    public static Intent getServiceIntent(Context context, String action)
    {
        Intent intent = new Intent(action);
        intent.setPackage(context.getPackageName());
        return intent;
    }

    private static PendingIntent getServicePendingIntent(Context context, String action)
    {
        Intent intent = getServiceIntent(context, action);
        return PendingIntent.getBroadcast(context, 0, intent,  PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent getControlActivityPendingIntent(Context context)
    {
        Intent intent = new Intent(context, BirdServiceActivity.class);
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent getListActivityPendingIntent(Context context)
    {
        Intent intent = new Intent(context, ViewActivity.class);
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Notification Channels
     */
    public static final String CHANNEL_ID_MAIN = "whobird.notification.channel";

    @TargetApi(26)
    protected static String createNotificationChannel(Context context)
    {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null)
        {
            String channelID = CHANNEL_ID_MAIN;
            String title = context.getString(R.string.notificationChannel_main_title);
            String desc = context.getString(R.string.notificationChannel_main_desc);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel channel = new NotificationChannel(channelID, title, importance);
            channel.setDescription(desc);
            channel.setSound(null, null);
            notificationManager.createNotificationChannel(channel);
            return channelID;
        }
        return "";
    }

    public static NotificationCompat.Builder createNotificationBuilder(Context context)
    {
        NotificationCompat.Builder builder;
        if (Build.VERSION.SDK_INT >= 26)
        {
            builder = new NotificationCompat.Builder(context, createNotificationChannel(context));
            builder.setOnlyAlertOnce(true);
        } else {
            builder = new NotificationCompat.Builder(context);
        }
        return builder;
    }

}