package com.github.uiautomator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ScreenBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent){
        if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())){
            Log.i("info", "Screen off action, will launch IdentifyActivity");
            context.startActivity(new Intent(context, IdentifyActivity.class));
        }
    }
}
