package com.qwasi.sdk;

//import org.gradle.api.*;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.Context;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;

import android.util.Base64;
import android.util.Log;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ccoulton on 6/11/15.
 */
public class Qwasi{// implements Plugin{
    private Context context;
    private Application sharedApplication;
    private SharedPreferences preferences;
    private boolean mregistered;
    private QwasiLocation mlastLocation = null;
    private QwasiAppManager qwasiAppManager = null;
    private QwasiNotificationManager qwasiNotificationManager= null;
    private String mapplicationName = null;
    private String mdeviceToken = null;
    private QwasiClient mclient = null;
    public NetworkInfo networkInfo;
    private HashMap<Object, Object> mmessageCache;
    public QwasiLocationManager mlocationManager;
    public QwasiConfig mconfig;
    public String muserToken;
    public Boolean mpushEnabled;
    public Boolean mlocationEnabled;
    public Boolean meventsEnabled;
    public Boolean museLocalNotifications; //apple only?

    public Qwasi(Context context){
        super();
        this.context = context;
        sharedApplication = (Application) this.context.getApplicationContext();
        this.qwasiAppManager = new QwasiAppManager(this);
        this.mclient = new QwasiClient();
        this.networkInfo = ((ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        qwasiNotificationManager = new QwasiNotificationManager(context);
    }

    public Qwasi qwasiWithConfig(QwasiConfig config) {
        return(this.initWithConfig(config));
    }

    public String getMdeviceToken(){
        return mdeviceToken;
    }

    private String getVerboseVersionName(){
        switch(Build.VERSION.SDK_INT){
            case Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1: //15 min version
                return "Android Ice_Cream Sandwich";
            case Build.VERSION_CODES.JELLY_BEAN: //16 4.1  14.7%
            case Build.VERSION_CODES.JELLY_BEAN_MR1: //17  17.5%
            case Build.VERSION_CODES.JELLY_BEAN_MR2: //18  5.2%
                return "Android Jellybean";
            case Build.VERSION_CODES.KITKAT: //19 4.4      39.2
            case Build.VERSION_CODES.KITKAT_WATCH: //20
                return "Android KitKat";
            default:
                return "Android Unknown";   //21&22  12.4%
        }
    }

    private Qwasi initWithConfig(QwasiConfig config){
        mregistered = false;

        mconfig = config;
        this.setConfig(config);
        /* todo fix location updates and events
        locationUpdatefilter
        locationEventFilter
        locationSyncFilter
        */
        preferences = context.getSharedPreferences("qwasi_sdk", Context.MODE_PRIVATE);
        if (preferences.contains("localNote")){
            museLocalNotifications = preferences.getBoolean("localNote", false);
        }
        if (preferences.contains("gcm_token")){

        }
        qwasiNotificationManager.registerForRemoteNotification();
        mmessageCache = new HashMap<Object, Object>();


        muserToken = "";
        this.sharedApplication.registerActivityLifecycleCallbacks(qwasiAppManager);

        return this;
    }

    public void setConfig(QwasiConfig config){
        this.mconfig = config;
        mclient = mclient.clientWithConfig(config, this);
        mregistered = false;
    }

    public QwasiErrorCode setPushEnabled(){
        if (mpushEnabled){
            return this.registerForNotifications();
        }
        else{
            return this.unregisterForNotifications();
        }
    }

    private QwasiErrorCode mregisterDevice(String deviceToken, String name, String userToken, HashMap<String, Object> userInfo, boolean success, boolean failure){
        if (deviceToken == null){  //if devicetoken is null set it empty so the server will gen one
            deviceToken = mdeviceToken;
        }

        if (name == null){  //if name is null get it from the phone, or user can give us one
            name = Build.PRODUCT;
        }

        if (userToken == null){  //if we didn't get a usertoken set it to be the phone number
            userToken = "DROIDTOKEN";
        }
        Map<String, Object> info = new HashMap<String, Object>();

        if (userInfo != null) {  //make sure that it's init so we don't crash
            if (!userInfo.isEmpty()) { //make sure it's not empty
                info.put("info", userInfo); //put all the recieved info into info
            }
        }

        Map<String, Object> deviceInfo = new HashMap<String, Object>();
            if (BuildConfig.DEBUG == true) { //sets our debug value
                deviceInfo.put("debug", Boolean.valueOf(true));
            }
            else {
                deviceInfo.put("debug", Boolean.valueOf(false));
            }
            deviceInfo.put("version", String.valueOf(Build.VERSION.RELEASE));  //this is the numerical version
            deviceInfo.put("system", getVerboseVersionName()); //kitkat or w/e the codename is
            deviceInfo.put("model", Build.MANUFACTURER + " " + Build.MODEL);  //Samsung Then the actual device name
            deviceInfo.put("sdkVersion", String.valueOf("2.1.0"));  //set because that's what the sdk is currently
            //since JSONRPC2 on java reads from a Hashmap<string, object> easier to put it here
            //and since the JSONRPC2 doesn't format it how the server expects have to do some playing.
        info.put("info", deviceInfo);
        info.put("name", name);
        info.put("options", null);
        info.put("user_token", userToken); //phonenumber
        info.put("id", deviceToken);
        JSONRPC2Response response;
        try {
            SharedPreferences.Editor editor = preferences.edit();
            response = mclient.invokeMethod("device.register", info);
            if (response.indicatesSuccess()) {
                mregistered = true; //we've now registered

                Map<String, Object> result = new HashMap<String, Object>();  //holder object
                result.put("result", response.getResult());  //unpack response object
                result = (Map<String, Object>) result.get("result"); //further unpacking
                //response.hashCode();
                mdeviceToken = result.get("id").toString();  //set our device token from the server
                editor.putString("qwasi_device_token",mdeviceToken);

                //grab the next key and unpack it.
                info = (Map<String, Object>) result.get("application");
                mapplicationName = info.get("name").toString();

                //get the settings out
                info = (Map<String, Object>) info.get("settings");
                mpushEnabled = (Boolean) info.get("push_enabled");
                mlocationEnabled = (Boolean) info.get("location_enabled");
                meventsEnabled = (Boolean) info.get("events_enabled");
                Log.d("QwasiDebug", "Device Successfully Registered");
                return QwasiErrorCode.QwasiErrorNone;
            }
            else {
                Log.e("QwasiError", "Device Failed to Register");
                return QwasiErrorCode.QwasiErrorDeviceNotRegistered;
            }
        }
        catch (Throwable e){

            Log.d("Debug", e.getMessage());
            return QwasiErrorCode.QwasiErrorDeviceNotRegistered; //todo handle 404/401?
        }
    }

    public QwasiErrorCode setUserToken(String userToken){
        muserToken = userToken;
        if (mregistered){
            Map<String, Object> parms = new HashMap<String, Object>();

            parms.put("id", mdeviceToken);
            parms.put("user_token", muserToken);
            try {
                if (mclient.invokeMethod("device.set_user_token", parms).indicatesSuccess()) {
                    return QwasiErrorCode.QwasiErrorNone;
                } else {
                    Log.e("QwasiError", "Set UserToken Failed");
                    return QwasiErrorCode.QwasiErrorSetUserTokenFailed;
                }
            }
            catch (Throwable e){
                Log.d("Debug", e.getMessage());
                return null; //Todo handle 401/404 error
            }
        }
        Log.e("QwasiError", "Device Not Registered");
        return QwasiErrorCode.QwasiErrorDeviceNotRegistered;
    }

    public QwasiErrorCode registerDevice(String deviceToken, String name, String userToken, HashMap<String, Object> userInfo, boolean success, boolean failure) {
        return this.mregisterDevice(deviceToken, name, userToken, userInfo, false, false);
    }

    public QwasiErrorCode registerDevice(String deviceToken, String name, String userToken, HashMap<String, Object> userInfo, boolean success){
        return this.registerDevice(deviceToken, name, userToken, userInfo, success, false);
    }

    public QwasiErrorCode registerDevice(String deviceToken, String name, String userToken){
        return this.registerDevice(deviceToken, name, userToken, null, false, false);
    }

    public QwasiErrorCode registerDevice(String deviceToken, String userToken){
        return this.registerDevice(deviceToken, null, userToken, null, false, false);
    }

    public QwasiErrorCode registerDevice(String deviceToken, String userToken, HashMap<String, Object> userInfo ){ //hashmap for nsDictionary
        return this.registerDevice(deviceToken, null, userToken, userInfo, false, false);
    }

    public QwasiErrorCode unregisterDevice(String deviceToken){
        if(mregistered){
            HashMap<String, Object> parm = new HashMap<String, Object>();
            if (deviceToken == null){
                parm.put("id", mdeviceToken);
            }
            else {
                parm.put("id", deviceToken);
            }
            try {
                JSONRPC2Response response = mclient.invokeMethod("device.unregister", parm);
                mdeviceToken = "";
                mregistered = false;
                mpushEnabled = false;
                mlocationEnabled = false;
                Log.d("QwasiDebug", "UnregisterDevice Success");
                return QwasiErrorCode.QwasiErrorNone;
            }
            catch (Throwable e){
                Log.d("Debug", e.getMessage());
                return QwasiErrorCode.QwasiErrorDeviceRegistrationFailed;
            }
        }
        else{
            Log.e("QwasiError", "Device Not Registred");
            return QwasiErrorCode.QwasiErrorDeviceNotRegistered;
        }
    }

    private QwasiErrorCode registerForNotifications() {
        if(mregistered){
            while (qwasiNotificationManager.isRegistering()){}
            String pushGCM = qwasiNotificationManager.getPushToken();
            HashMap<String, Object> parms = new HashMap<String, Object>();
            parms.put("id", mdeviceToken);
            parms.put("proto", "push.gcm");
            parms.put("token", pushGCM);
            try {
                if (mclient.invokeMethod("device.set_push_token", parms).indicatesSuccess()) {
                    Log.d("QwasiDebug", "Set Push Token success");
                    return QwasiErrorCode.QwasiErrorNone;
                } else {
                    Log.e("QwasiError", "Set Push Token failed");
                    return QwasiErrorCode.QwasiErrorPushRegistrationFailed;
                }
            }
            catch (Throwable e){
                Log.d("Debug", e.getMessage());
                return null; //todo handle 401/404
            }
        }
        else {
            Log.e("QwasiError", "DeviceNotRegistered");
            return QwasiErrorCode.QwasiErrorDeviceNotRegistered;
        }
    }

    private QwasiErrorCode unregisterForNotifications(){
        if(mregistered){
            HashMap<String, Object> parms = new HashMap<String, Object>();
            parms.put("id", mdeviceToken);
            parms.put("proto", "push.poll");
            parms.put("token", "");
            try {
                if (mclient.invokeMethod("device.set_push_token", parms).indicatesSuccess()) {
                    Log.d("QwasiDebug", "UnSet Push Token success");
                    mpushEnabled = false;
                    return QwasiErrorCode.QwasiErrorNone;
                } else {
                    Log.e("QwasiError", "Unregister for Note failed");
                    return QwasiErrorCode.QwasiErrorPushUnregisterFailed;
                }
            }
            catch (Throwable e){
                Log.d("Debug", e.getMessage()); //todo handle 401/404
                return QwasiErrorCode.QwasiErrorPushUnregisterFailed;
            }

        }
        else{
            Log.e("QwasiError", "DeviceNotRegistered");
            return QwasiErrorCode.QwasiErrorDeviceNotRegistered;
        }
    }

    public QwasiErrorCode fetchMessageForNotification(HashMap<String, Object> userInfo, boolean success, boolean failure){
        if(mregistered){
            HashMap<String, Object> flags = new HashMap<String, Object>();
            //TODO Get the values from message fetch
            flags.put("opened", Boolean.valueOf(qwasiAppManager.isApplicationInForeground()));
            HashMap<String, Object> qwasi = new HashMap<String, Object>();
            qwasi.put("qwasi", userInfo.get("qwasi"));
            qwasi.putAll(userInfo);
            String msgId = String.valueOf(qwasi.get("m"));
            String appId = String.valueOf(qwasi.get("a"));
            if (!(msgId.isEmpty()) && !(appId.isEmpty())){
                if (appId.equals(mconfig.mapplication)){
                    //TODO: get cached messages
                    if (mmessageCache != null){
                        HashMap<String, Object> parms = new HashMap<String, Object>();
                        parms.put("device", mdeviceToken);
                        parms.put("id", msgId);
                        parms.put("flags", flags);
                        try {
                            if (mclient.invokeMethod("message.fetch", parms).indicatesSuccess()) {
                                return QwasiErrorCode.QwasiErrorNone;
                            } else {
                                return QwasiErrorCode.QwasiErrorMessageFetchFailed;
                            }
                        }
                        catch (Throwable e){
                            Log.d("Debug", e.getMessage());
                            return null; //fixme handle 401/404
                        }
                    }
                    else{ //todo
                        return QwasiErrorCode.QwasiErrorMessageNotFound;
                        //no messages to fetch
                        //QwasiMessage message = nskeyedUnarchiver unarchiver with cachedmessage
                    }
                }
                else{//TODO
                    return QwasiErrorCode.QwasiErrorNone;
                    //wrong appid
                    //if succesful, and there is a message
                }
            }
            else { //todo MsgId is empty or Appid is empty
                return QwasiErrorCode.QwasiErrorNone;
            }
        }
        else {
            return QwasiErrorCode.QwasiErrorDeviceNotRegistered;
        }
    }

    public QwasiErrorCode fetchUnreadMessage(/*func calls*/){
        if(mregistered){
            HashMap<String, Object> parms = new HashMap<String, Object>();
            HashMap<String, Object> options = new HashMap<String, Object>();
            options.put("fetch", String.valueOf(true));
            parms.put("device", mdeviceToken);
            parms.put("options", options);

            try {
                JSONRPC2Response response = mclient.invokeMethod("message.poll", parms);
                if (response.indicatesSuccess()) {
                    //todo get messages out and push them to screen/notifications
                    return QwasiErrorCode.QwasiErrorNone;
                }
                else{
                        Log.d("QwasiDebug", "Message Fetch Failed");
                        return QwasiErrorCode.QwasiErrorMessageFetchFailed;
                    }
                }

            catch (Throwable e) {
                Exception cause = new Exception(e.getMessage(), e);
                if (cause == new FileNotFoundException()) {
                    Log.d("Debug", "Nomessages to fetch");
                    return QwasiErrorCode.QwasiErrorMessageNotFound;
                } //todo Handle 401/404 error
                else
                    return null;
            }
        }
        else{
            Log.e("QwasiError", "Device Not Registered");
            return QwasiErrorCode.QwasiErrorDeviceNotRegistered;
        }

    }

    public QwasiErrorCode postEvent(String type, HashMap<String, Object> data, boolean success, boolean failure){
        if(mregistered){
            HashMap<String, Object> parms = new HashMap<String, Object>();
            parms.put("device", mdeviceToken);
            parms.put("type", type);
            parms.put("data", data);
            try {
                mclient.invokeMethod("event.post", parms);
                return QwasiErrorCode.QwasiErrorNone;
            }
            catch (Throwable e){  //todo handle 401, and 404's app not found device not found
                Exception cause = new Exception(e);
                Log.d("Debug", cause.getMessage());
                return QwasiErrorCode.QwasiErrorPostEventFailed;
            }
        }
        else{
            Log.e("QwasiError", "Device NotRegistered");
            return QwasiErrorCode.QwasiErrorDeviceNotRegistered;
        }
    }

    public QwasiErrorCode postEvent(String type, HashMap<String, Object> data){
        return this.postEvent(type, data, false, false);
    }

    public QwasiErrorCode fetchLocationsNear(Location place, boolean success, boolean failure) {
        if(mregistered){
            HashMap<String, Object> parms = new HashMap<String, Object>();
            HashMap<String, Object> near = new HashMap<String, Object>();
            ArrayList<String> options = new ArrayList<String>();
            near.put("lng", mlastLocation.getLongitude());
            near.put("lat", mlastLocation.getLatitude());
            //near.put("radius",Double.valueOf(LocationSyncFilter*10));
            options.add("schema");
            options.add("2.0");
            parms.put("near", near);
            parms.put("options", options);
            try {
                JSONRPC2Response response = mclient.invokeMethod("location.fetch", parms);
                return QwasiErrorCode.QwasiErrorNone;
            }
            catch (Throwable e){
                Log.d("Debug", e.getMessage());
                return QwasiErrorCode.QwasiErrorLocationFetchFailed; //fixme 401/404
            }
        }
        else{
            Log.e("QwasiError", "Device Not Registered");
            return QwasiErrorCode.QwasiErrorDeviceNotRegistered;
        }
    }

    public QwasiErrorCode subscribeToChannel(String channel){
        return this.subscribeToChannel(channel, false, false);
    }

    public QwasiErrorCode subscribeToChannel(String channel, boolean success, boolean failure){
        if(mregistered){
            HashMap<String, Object> parms = new HashMap<String, Object>();
            parms.put("device", mdeviceToken);
            parms.put("channel", channel);
            try {
                if (mclient.invokeMethod("channel.subscribe", parms).indicatesSuccess()) {
                    Log.d("QwasiDebug", "subscribe to channel success");
                    return QwasiErrorCode.QwasiErrorNone;
                } else {
                    Log.e("QwasiError", "Subscribe to channel Failed");
                    return QwasiErrorCode.QwasiErrorChannelSubscribeFailed;
                }
            }
            catch (Throwable e){
                Log.d("Debug", e.getMessage());
                return null; //fixme  401/404
            }
        }
        else{
            Log.e("QwasiError", "Device NotRegistered");
            return QwasiErrorCode.QwasiErrorDeviceNotRegistered;
        }
    }

    public QwasiErrorCode unsubscribeFromChannel(String channel){
        return this.unsubscribeFromChannel(channel, false, false);
    }

    public QwasiErrorCode unsubscribeFromChannel(String channel, boolean success, boolean failure){
        if(mregistered){
            HashMap<String, Object> parms = new HashMap<String, Object>();
            parms.put("device", mdeviceToken);
            parms.put("channel", channel);
            try {
                if (mclient.invokeMethod("channel.unsubscribe", parms).indicatesSuccess()) {
                    Log.d("QwasiDebug", "Unsubcribe from channel Success");
                    return QwasiErrorCode.QwasiErrorNone;
                } else {
                    Log.e("QwasiError", "Unsubscribe from channel Failed");
                    return QwasiErrorCode.QwasiErrorChannelUnsubscribeFailed;
                }
            }
            catch (Throwable e){
                Log.d("Debug", e.getMessage());
                return null; //fixme 401/404
            }
        }
        else{
            Log.e("QwasiError", "Device NotRegistered");
            return QwasiErrorCode.QwasiErrorDeviceNotRegistered;
        }
    }

    public QwasiErrorCode setDeviceValue(Object value, String key, boolean success, boolean failure){
        if(mregistered){
            HashMap<String, Object> parms = new HashMap<String, Object>();
            parms.put("id", mdeviceToken);
            parms.put("key", key);
            parms.put("value", value);
            try {
                if (mclient.invokeMethod("device.set_data", parms).indicatesSuccess()) {
                    Log.d("QwasiDebug", "Set data Success");
                    return QwasiErrorCode.QwasiErrorNone;
                } else {
                    Log.e("QwasiError", "Set data Failed");
                    return QwasiErrorCode.QwasiErrorSetDeviceDataFailed;
                }
            }
            catch (Throwable e){
                Log.d("Debug", e.getMessage());
                return null; //fixme 401/400
            }
        }
        else{
            Log.e("QwasiError", "Device Not Registered");
            return QwasiErrorCode.QwasiErrorDeviceNotRegistered;
        }
    }

    public QwasiErrorCode setDeviceValue(Object value, String key){
        return this.setDeviceValue(value, key, false, false);
    }

    public QwasiErrorCode deviceValueForKey(String key) {
        return this.deviceValueForKey(key, false, false);
    }

    public QwasiErrorCode deviceValueForKey(String key, boolean success, boolean failure){
        if (mregistered){
            Map<String, Object> parms = new HashMap<String, Object>();
            parms.put("id", mdeviceToken);
            parms.put("key", key);
            try {
                JSONRPC2Response response = mclient.invokeMethod("device.get_data", parms);
                if (response.indicatesSuccess()) {
                    parms.put("result", response.getResult());
                    //todo push data to screen?
                    Log.d("Debug", (String) parms.get("result"));
                    Log.d("QwasiDebug", "Get data Success");
                    return QwasiErrorCode.QwasiErrorNone;
                } else {
                    Log.e("QwasiError", "Get data Failed");
                    return QwasiErrorCode.QwasiErrorGetDeviceDataFailed;
                }
            }
            catch (Throwable e){
                Log.d("Debug", e.getMessage());
                return null; //fixme 400/401
            }
        }
        else{
            Log.e("QwasiError", "Device Not Registered");
            return QwasiErrorCode.QwasiErrorDeviceNotRegistered;
        }
    }

    public QwasiErrorCode sendMessage(QwasiMessage message, String userToken, boolean success, boolean failure){
        if(mregistered) {
            Object payload = message.mpayload;
            if (payload != null){
            //todo: check if is a valid json object
                //if (){}
            //throw an error, get the data if the data is null, or the error isn't print error
            //set payload to the JSONData
            }
            else if (payload instanceof String){
                HashMap<String, Object> encrypted = new HashMap<String, Object>();
                encrypted.put("payload", Base64.encode(((String) payload).getBytes(), Base64.DEFAULT));
                //convert it to a jsonObject?
            }
            else{
                payload = null;
            }
            Map<String, Object> parms = new HashMap<String, Object>();
            HashMap<String, Object> audi = new HashMap<String, Object>();
            audi.put("user_tokens", userToken);
            parms.put("audience", audi); //can be devices, usertokens, channels w/e
            parms.put("payload_type", payload.getClass().toString());
            //parms.put("notification", new HashMap<String, Object>().put("text", message.alert));
            //parms.put("payload", payload);
            //parms.put("tags", message.tags);
            //parms.put("options", (new HashMap<String, Object>().put("encodedPayload", )))
            try {
                if (mclient.invokeMethod("message.send", parms).indicatesSuccess()) {
                    Log.d("QwasiDebug", "Message Sent Successfully");
                    return QwasiErrorCode.QwasiErrorNone;
                } else {
                    Log.e("QwasiError", "Message Send Failed");
                    return QwasiErrorCode.QwasiErrorSendMessageFailed;
                }
            }
            catch (Throwable e){
                Log.d("Debug", e.getMessage());
                return null; //fixme  400/401
            }
        }
        else{//todo handle sendmessge error
            Log.e("QwasiError", "Device not Registered");
            return QwasiErrorCode.QwasiErrorDeviceNotRegistered;
        }
    }

    public QwasiErrorCode sendMessage(QwasiMessage message, String userToken){
        return this.sendMessage(message, userToken, false, false);
    }
}