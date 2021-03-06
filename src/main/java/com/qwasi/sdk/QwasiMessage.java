package com.qwasi.sdk;

import android.text.format.DateFormat;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

/**
 * Created by ccoulton on 6/11/15.
 * For Qwasi Inc. for their Open source Android SDK example
 * Released under the MIT Licence
 */
public class QwasiMessage extends Object{
    public String malert;
    public Date mtimestamp;
    public String messageId;
    public String application;
    public String mpayloadType;
    public Object mpayload;
    public ArrayList<Object> mtags;
    public Boolean selected;
    public Boolean fetched;
    private Object mencodedPayload;
    String TAG = "QwasiMessage";

    public QwasiMessage(){
        super();
        mtags = new ArrayList<>();
    }

    private QwasiMessage initWithData(HashMap<String, Object> data){
        messageId = data.get("id").toString();
        application = ((HashMap<String, Object>)data.get("application")).get("id").toString();
        malert = data.get("text").toString();
        if (Qwasi.getInstance().qwasiAppManager.isApplicationInForeground()){
            selected = true;
        }
        //dateformater = date
        //DateFormat dateFormatter = new DateFormat();

        mtimestamp = new Date();

        mpayloadType = data.get("payload_type").toString();
        if (((HashMap<String, Object>) data.get("context")).containsKey("tags")) {
            mtags.add(((HashMap<String, Object>) data.get("context")).get("tags"));
        }
        if (((HashMap<String, Object>) data.get("flags")).containsKey("fetched")) {
            fetched = Boolean.getBoolean(((HashMap<String, Object>) data.get("flags")).get("fetched").toString());
        }
        mencodedPayload = data.get("payload");
        byte [] temp = Base64.decode(mencodedPayload.toString(), Base64.DEFAULT);
        try{
            if (mpayloadType.equalsIgnoreCase("application/json")){
                //error?
                mpayload = new JSONObject(new String(temp, "UTF-8"));
            }

            else if (mpayloadType.contains("text")){
                mpayload = new String(temp, "UTF-8");
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
        Log.d(TAG, mpayload.toString());
        return this;
    }

    public QwasiMessage messageWithData(HashMap<String, Object> data){
        return this.initWithData(data);
    }

    private Object initWithCoder(/*nsCoder*/){
        return this;
    }

    public Object initWithAlert(String alert, JSONObject payload, String payloadtype, ArrayList<Object> tags){
        malert = alert;
        mpayload = payload;
        mpayloadType = payloadtype;
        mtags = tags;
        mtimestamp = new Date();
        if (mpayloadType == null) {
            if (mpayload instanceof JSONObject){
                mpayloadType = "application/json";
            }
            else if(mpayload instanceof String){
                mpayloadType = "text/plain";
            }
        }
        return this;
    }

    public Boolean silent(){
        return (malert == null)||(malert.isEmpty());
    }

    public String description(){
        return mpayload.toString();
    }
}
