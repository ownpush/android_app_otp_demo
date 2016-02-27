/*
 * The MIT License (MIT)
 * Copyright (c) 2016 Fastboot Mobile LLC.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package com.fastbootmobile.twofactorauthdemo;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.fastbootmobile.ownpushclient.OwnPushClient;
import com.fastbootmobile.ownpushclient.OwnPushCrypto;

public class PushReceiver extends BroadcastReceiver {

    private static String TAG = "PushReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "ACTION : " + intent.getAction());
        SharedPreferences pref = context.getApplicationContext().getSharedPreferences(OwnPushClient.PREF_PUSH, Context.MODE_PRIVATE);

        if (intent.getAction().equals(OwnPushClient.INTENT_RECEIVE)) {
            Log.d(TAG, "Decrypt : " + intent.getExtras().getString(OwnPushClient.EXTRA_DATA));

            OwnPushCrypto fp = new OwnPushCrypto();
            OwnPushCrypto.AppKeyPair keys = fp.getKey(pref.getString(OwnPushClient.PREF_PUBLIC_KEY, ""), pref.getString(OwnPushClient.PREF_PRIVATE_KEY, ""));
            String msg = fp.decryptFromIntent(intent.getExtras(), BuildConfig.APP_PUBLIC_KEY, keys);

            if (msg != null) {
                Log.e(TAG, "OTP : " + msg);
                Intent i = new Intent(context, MainActivity.class);
                i.putExtra("otp",msg);

                PendingIntent pIntent = PendingIntent.getActivity(context, (int) System.currentTimeMillis(), i, 0);

                Notification n  = new Notification.Builder(context.getApplicationContext())
                        .setContentTitle("OwnPush 2FA")
                        .setContentText("2FA Code Received")
                        .setContentIntent(pIntent)
                        .setSmallIcon(R.drawable.ic_done)
                        .setAutoCancel(true)
                        .build();

                n.defaults |= Notification.DEFAULT_SOUND;

                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(0, n);

            }
        }
    }
}
