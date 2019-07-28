package com.oscar.WearOutAttack;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;

public class BackgroundService extends Service {

    public MainActivity _main;
    public static boolean _running;
    public static long _checkInterval;
    private IntentFilter ifilter;
    private PowerManager powerManager;
    Thread burner;

    private final String TestFilename = "oscar.testfile";
    private long delay = 0;
//    private PowerManager.WakeLock wakeLock;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onCreate() {
        _running = false;
        _main = null;
        _checkInterval = 1000;
        ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
//        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IO_TESTER_BG");

        Log.d("Background service", "creating service");
    }

    public int onStartCommand(Intent intent, int flags, final int startId) {
        Log.d("Background service", "Starting service");

        if (_running) {
            Log.d("Background service", "Another service already running, exiting (" + startId + ")");
            stopSelf(startId);
            return Service.START_STICKY;
        }

        if (MainActivity.flagDisableBackground) {
            Log.d("Background service", "flagDisableBackground set, exiting service (" + startId + ")");
            stopSelf(startId);
            return Service.START_STICKY;
        }

        _running = true;
        burner = new Thread(new Runnable() {

            @Override
            public void run() {
                File[] files = new File[MainActivity.CONF_MAX_THREAD];
                WorkerThread[] threads = new WorkerThread[MainActivity.CONF_MAX_THREAD];

                for (int i = 0; i < MainActivity.CONF_MAX_THREAD; i++) {
                    files[i] = new File(getApplicationContext().getFilesDir(), TestFilename + "." + i);
                    threads[i] = new WorkerThread(files[i], MainActivity.CONF_FILE_SIZE, Dynamo.IoEngine.NATIVESYNC,
                            Dynamo.SyncType.RWS, Dynamo.Direction.OUTPUT);
                }

                PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "oscar:IO_TESTER_BG");

                wakeLock.acquire();

                long startTime = System.nanoTime();
                long intervalStartTime = startTime;
                long[] counts = new long[MainActivity.CONF_MAX_THREAD];
                long[] intervalCounts = new long[MainActivity.CONF_MAX_THREAD];

                for (int i = 0; i < MainActivity.CONF_MAX_THREAD; i++) {
                    threads[i].start();
                }

                while (true) {
                    try {
                        Thread.sleep(MainActivity.CONF_TIME_GRAN);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }

                    long totalIntervalCount = 0;
                    long totalCount = 0;
                    for (int j = 0; j < MainActivity.CONF_MAX_THREAD; j++) {
                        long tempCount = threads[j].getCount();
                        intervalCounts[j] = tempCount - counts[j];
                        counts[j] = tempCount;
                        totalCount += tempCount;
                        totalIntervalCount += intervalCounts[j];
                    }

                    long intervalTime = System.nanoTime() - intervalStartTime;
                    intervalStartTime += intervalTime;


                    Intent batteryStatus = registerReceiver(null, ifilter);
                    int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    Log.d("Background service", "["+ MainActivity.CONF_MAX_THREAD + "] Charging " + status + " running status " + _running
                            + ", count " + totalIntervalCount + " / " + totalCount
                            + ", throughput: " + 4.0 * totalIntervalCount * 1000000000 / (intervalTime));

                    if (MainActivity.flagIgnoreCharge)
                        continue;
                    if (status != BatteryManager.BATTERY_STATUS_CHARGING
                            && status != BatteryManager.BATTERY_STATUS_FULL) {
                        Log.i("Background service", "Phone stopped charging, exiting");
                        break;
                    }
                }

                _running=false;
                wakeLock.release();
                stopSelf(startId);
            }
        });

        burner.start();

        return Service.START_STICKY;
    }

    public void onDestroy() {
        Log.d("Background service", "destroying service");
    }
}
