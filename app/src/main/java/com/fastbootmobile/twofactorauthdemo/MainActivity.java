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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.fastbootmobile.ownpushclient.OwnPushClient;
import com.fastbootmobile.ownpushclient.OwnPushCrypto;
import com.fastbootmobile.ownpushclient.OwnPushRegistrant;
import com.joshdholtz.sentry.Sentry;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;


public class MainActivity extends AppCompatActivity {

    private OwnPushRegistrant mReg;
    private RegisterReceiver receiver;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.info_layout);

        Button regButton = (Button) findViewById(R.id.register);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            if (extras.getString("otp", null) != null) {
                TextView txt = (TextView) findViewById(R.id.txt);
                regButton.setVisibility(View.GONE);
                txt.setText("One Time Password :  " + extras.getString("otp", null));
                return;
            }
        }

        mReg = new OwnPushRegistrant(this);
        mHandler = new Handler();
        receiver = new RegisterReceiver(new Handler());

        IntentFilter filter = new IntentFilter(OwnPushClient.INTENT_REGISTER);
        filter.addCategory(BuildConfig.APP_PUBLIC_KEY);

        registerReceiver(receiver, filter);

        final SharedPreferences prefs = this.getApplicationContext().getSharedPreferences(
                OwnPushClient.PREF_PUSH,Context.MODE_PRIVATE);

        if (!prefs.getBoolean(OwnPushClient.PREF_REG_DONE,false)) {

            regButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    OwnPushCrypto fp = new OwnPushCrypto();
                    OwnPushCrypto.AppKeyPair keys = fp.generateInstallKey();

                    boolean ret = mReg.register(BuildConfig.APP_PUBLIC_KEY, keys.getPublicKey());

                    if (ret) {
                        //STORE THEM
                        prefs.edit().putString(OwnPushClient.PREF_PUBLIC_KEY, keys.getPublicKey()).commit();
                        prefs.edit().putString(OwnPushClient.PREF_PRIVATE_KEY, keys.getPrivateKey()).commit();
                    }
                }
            });
        } else {
            regButton.setVisibility(View.GONE);

            updateUI();

            if (prefs.getString("device_uid",null) == null) {
                registerWithBackend();
            }
        }


    }

    protected void updateUI(){
        TextView txt = (TextView) findViewById(R.id.txt);
        Button regButton = (Button) findViewById(R.id.register);

        final SharedPreferences prefs = this.getApplicationContext().getSharedPreferences(
                OwnPushClient.PREF_PUSH,Context.MODE_PRIVATE);

        if (prefs.getString("device_uid",null) != null){
            txt.setText("Device ID :  " + prefs.getString("device_uid", null));
        }

        if (prefs.getBoolean(OwnPushClient.PREF_REG_DONE,false)){
            regButton.setVisibility(View.GONE);
        }
    }


    protected void registerWithBackend(){

        final SharedPreferences pref = this.getSharedPreferences(OwnPushClient.PREF_PUSH, Context.MODE_PRIVATE);


        Thread httpThread = new Thread(new Runnable() {

            private String TAG = "httpThread";
            private String ENDPOINT = "https://otp.demo.ownpush.com/push/register";


            @Override
            public void run() {
                URL urlObj;

                try {
                    urlObj = new URL(ENDPOINT);

                    String  install_id = pref.getString(OwnPushClient.PREF_PUBLIC_KEY, null);

                    if (install_id == null){
                        return;
                    }

                    String mPostData = "push_id=" + install_id;
                    HttpsURLConnection con = (HttpsURLConnection) urlObj.openConnection();

                    con.setRequestProperty("User-Agent","Mozilla/5.0 ( compatible ) ");
                    con.setRequestProperty("Accept","*/*");
                    con.setDoInput(true);
                    con.setRequestMethod("POST");
                    con.getOutputStream().write(mPostData.getBytes());
                    con.connect();
                    int http_status = con.getResponseCode();

                    if (http_status != 200){
                        Log.e(TAG, "ERROR IN HTTP REPONSE : " + http_status);
                        return;
                    }

                    InputStream stream;
                    stream = con.getInputStream();

                    BufferedReader br = new BufferedReader(new InputStreamReader(stream));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line+"\n");
                    }
                    br.close();
                    String data = sb.toString();

                    if (data.contains("device_uid")){
                        JSONObject json = new JSONObject(data);
                        String device_id = json.getString("device_uid");
                        pref.edit().putString("device_uid",device_id).commit();
                        Log.d(TAG, "GOT DEVICE UID OF " + device_id);

                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                updateUI();
                            }
                        });

                    }


                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        });

        httpThread.start();
    }



    public class RegisterReceiver extends BroadcastReceiver{

        private final Handler handler; // Handler used to execute code on the UI thread
        private String TAG = "RegisterReceiver";

        public RegisterReceiver(Handler handler) {
            this.handler = handler;
        }

        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(OwnPushClient.INTENT_REGISTER)){
                boolean status = intent.getExtras().getBoolean(OwnPushClient.EXTRA_STATUS);
                SharedPreferences pref = context.getApplicationContext().getSharedPreferences(OwnPushClient.PREF_PUSH, Context.MODE_PRIVATE);


                if (status){
                    String install_id = intent.getExtras().getString(OwnPushClient.EXTRA_INSTALL_ID);
                    Log.d(TAG, "INSTALL REGISTERED WITH ID : " + install_id);

                    pref.edit().putBoolean(OwnPushClient.PREF_REG_DONE,true).commit();
                    registerWithBackend();
                    updateUI();

                } else {
                    Log.d(TAG, "REGISTRATION FAILED ... TRY AGAIN");
                    pref.edit().remove(OwnPushClient.PREF_REG_DONE).commit();
                    pref.edit().remove(OwnPushClient.PREF_PUBLIC_KEY).commit();
                    pref.edit().remove(OwnPushClient.PREF_PRIVATE_KEY).commit();
                }
            }

        }
    }
}
