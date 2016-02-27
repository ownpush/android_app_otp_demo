<img src="https://ownpush.com/wp-content/uploads/2016/02/ownpush_128-logoSpelledout.png">

# OTP Demo App #

## Overview ##
The purpose of this app is to showcase how a developer would integrate <a href="https://ownpush.com" target="_blank">OwnPush</a> into their application for secure, end-to-end encrypted push messaging without Google Services, and as such without a negative drain to the device's battery. The OTP Demo App demonstrates a use case for secure messaging whereby the user is given a One-Time Password (OTP) securely. The OTP Demo App performs the following:

* Allows registration of the application with OwnPush and the OTP notification server
* Receive notification from the OwnPush service
* Notifies user of new OTP

## Registration With OwnPush ##

1. Register a new receiver to get OwnPush register intents
2. Generate a new key pair and store them
3. Call registration on OwnPushRegistrant
4. On register intent, store the keypair as they are used in message decryption

## Key Code Points ##

_Generating Key Pairs & Calling register_
```java
    OwnPushCrypto fp = new OwnPushCrypto(); // Create an OwnPush crypto object
    OwnPushCrypto.AppKeyPair keys = fp.generateInstallKey(); // Generate new keypair
    boolean ret = mReg.register(BuildConfig.APP_PUBLIC_KEY, keys.getPublicKey());
```

_Decrypt push message_
```java
    OwnPushCrypto fp = new OwnPushCrypto(); // Create a crypto object for decrypt
    // Get the app key pair from shared preferences (these have been confirmed by the register intent)
    OwnPushCrypto.AppKeyPair keys = fp.getKey(pref.getString(OwnPushClient.PREF_PUBLIC_KEY, ""), pref.getString(OwnPushClient.PREF_PRIVATE_KEY, ""));
    // Decrypt the message from the intent extra data
    String msg = fp.decryptFromIntent(intent.getExtras(), BuildConfig.APP_PUBLIC_KEY, keys);
```

_Adding correct intent filter to receiver_
```java
    // Create the intent filter to receive the register intents from OwnPush Service
    IntentFilter filter = new IntentFilter(OwnPushClient.INTENT_REGISTER);
    filter.addCategory(BuildConfig.APP_PUBLIC_KEY); // Use the app public key as the category
    registerReceiver(receiver, filter); // Register our RegisterReceiver object for this intent
```

_Defining permissions in manifest *(replace APP_ID with your application public key)*_
```xml
    <!-- Define and use our per app permission using the application public ID-->
    <permission android:name="com.fastbootmobile.ownpush.APP_ID.permission.PUSH"
        android:protectionLevel="signature" />
    <uses-permission android:name="com.fastbootmobile.ownpush.APP_ID.permission.PUSH"/>
```

_Defining a receiver for push messages within manifest *(replace APP_ID with your application public key)*_  
```xml
        <!-- Register our receiver for push messages -->
        <receiver android:name=".PushReceiver">
            <intent-filter>
                <!-- This is for the OwnPush receive action -->
                <action android:name="com.fastbootmobile.ownpush.intent.RECEIVE" />
                <!-- OwnPush also filters by category (using the app public id) -->
                <category android:name="APP_ID"/>
            </intent-filter>
        </receiver>
```