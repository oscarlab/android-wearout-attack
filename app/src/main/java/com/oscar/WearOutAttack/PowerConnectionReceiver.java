package com.oscar.WearOutAttack;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

public class PowerConnectionReceiver extends BroadcastReceiver {

    static public MainActivity main;

    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;

        int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

//        main.conprintln(" " + BatteryManager.EXTRA_STATUS + " " + BatteryManager.EXTRA_PLUGGED + " " + BatteryManager.BATTERY_STATUS_CHARGING);
//        main.conprintln("Charging state changed " + status + " " + chargePlug);
//        main.conprintln(intent.toString());

        boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
        boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;

        Intent intent1 = new Intent(context, BackgroundService.class);
        intent1.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startService(intent1);
    }
}