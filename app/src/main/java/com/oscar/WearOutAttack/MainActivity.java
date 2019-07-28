package com.oscar.WearOutAttack;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private TextView gui_tv_con;
    private TextView gui_tv_state;
    private Spinner gui_sp_loc;
    private EditText gui_tx_loop;
    private EditText gui_tx_delay;

    private CheckBox gui_cb_ignoreCharge;

    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    private final long UI_UPDATE_INTERVAL = 1024;

    public static boolean flagIgnoreCharge = false;
    public static boolean flagDisableBackground = false;

    private enum TestState {
        IDLE,
        RUNNING,
        ABORT_PENDING,
    }
    TestState state;
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    private final String SecondaryExternal = "/mnt/media_rw/sdcard1";
    private final String TestFilename = "oscar.testfile";

    // CONF_FLAG_MIMIC suppress the actual I/O wear-out operations, use sleep instead, for debug purpose
    public static final boolean CONF_FLAG_MIMIC = false;

    // CONF_FILE_SIZE sets the size of the file used for wear-out
    public static final long CONF_FILE_SIZE = 10 * 1024 * 1024;

    public static final long CONF_NATIVE_WRITE_SIZE = 128; // 4K or 128K

    public static final long CONF_WRITE_DELAY = 0;
    public static final long CONF_SYNC_COUNT = 0;

    public static final int CONF_MAX_THREAD = 4;

    public static final long CONF_TIME_GRAN = 1000;

    // Configure I/O pattern (1: Random 4K, 2: Seq 4K, 3: no op)
    public static final long CONF_IO_PATTERN = 3;

    public enum StorageLocation {
        PRIVATE(0),
        EXTERNAL(1),
        SECONDARY(2);

        private int value;

        StorageLocation(int v) { value = v; }
        public int getValue() { return value; }
        public void setValue(int v) { value = v; }
    }

    public void conprintln(final String str) {
        gui_tv_con.post(new Runnable() {
            @Override
            public void run() {
                gui_tv_con.append(str + "\n");
            }
        });
    }

    private void dprintln(String str) { Log.d("main", str); }

    private void println(String str) {
        gui_tv_con.append(str + "\n");
    }

    private void runIOTest(StorageLocation location, long loopCount, long delay) {
        WorkerThread[] threads = new WorkerThread[CONF_MAX_THREAD];
        File[] files = new File[CONF_MAX_THREAD];

        for (int i = 0; i < CONF_MAX_THREAD; i++) {
            if (location == MainActivity.StorageLocation.PRIVATE) {
                files[i] = new File(getApplicationContext().getFilesDir(), TestFilename + "." + i);
            } else if (location == MainActivity.StorageLocation.EXTERNAL) {
                files[i] = new File(Environment.getExternalStorageDirectory(), TestFilename + "." + i);
            } else if (location == MainActivity.StorageLocation.SECONDARY) {
                files[i] = new File(SecondaryExternal, TestFilename + "." + i);
            } else {
                dprintln("Unsupported storage location: " + location);
                return;
            }

            threads[i] = new WorkerThread(files[i], CONF_FILE_SIZE, Dynamo.IoEngine.NATIVESYNC, Dynamo.SyncType.RWS, Dynamo.Direction.OUTPUT);
        }

        wakeLock.acquire();
        long startTime = System.nanoTime();
        long intervalStartTime = startTime;
        long[] counts = new long[CONF_MAX_THREAD];
        long[] intervalCounts = new long[CONF_MAX_THREAD];
        for (int i = 0; i < CONF_MAX_THREAD; i++) {
            threads[i].start();
        }

        for (int i = 0; i < loopCount; i++) {
            try {
                Thread.sleep(CONF_TIME_GRAN);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            for (int j = 0; j < CONF_MAX_THREAD; j++) {
                long tempCount = threads[j].getCount();
                intervalCounts[j] = tempCount - counts[j];
                counts[j] = tempCount;
            }
            long intervalTime = System.nanoTime() - intervalStartTime;
            intervalStartTime += intervalTime;
            long totalCount = 0;
            for (int j = 0; j < CONF_MAX_THREAD; j++) {
                Log.i("runIOTest", "Thread " + j + " bw " + CONF_NATIVE_WRITE_SIZE * 1000000000.0 * intervalCounts[j] / intervalTime);
                totalCount += intervalCounts[j];
            }
            Log.i("runIOTest", "Total interval bw " + CONF_NATIVE_WRITE_SIZE * 1000000000.0 * totalCount / intervalTime);

            if (state == TestState.ABORT_PENDING) {
                Log.i("runIOTest", "Aborted test");
                break;
            }
        }

        for (int i = 0; i < CONF_MAX_THREAD; i++) {
            threads[i].abort();
        }

        state = TestState.IDLE;

        wakeLock.release();

    }

    public void onRequestPermissionResult(int requestCode, String permissions[], int[] grantResults) {
        return;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        String [] PERMISSIONS_STORAGE = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    PERMISSIONS_STORAGE, 1);
        }

        powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "oscar:IO_TESTER");

        gui_tv_con = (TextView) findViewById(R.id.tv_con);
        gui_tv_con.setMovementMethod(new ScrollingMovementMethod());

        gui_tv_state = (TextView) findViewById(R.id.tv_state);
        gui_tv_state.setText("Idle, " + (BackgroundService._running ? "Service running": "Service not running"));

        gui_sp_loc = (Spinner) findViewById(R.id.sp_location);
        ArrayAdapter<CharSequence> sp_loc_adapter = ArrayAdapter.createFromResource(this,
                R.array.storage_locations, android.R.layout.simple_spinner_item);
        sp_loc_adapter.setDropDownViewResource(android.R.layout.simple_spinner_item);
        gui_sp_loc.setAdapter(sp_loc_adapter);

        gui_tx_loop = (EditText) findViewById(R.id.tx_loop);
        gui_tx_delay = (EditText) findViewById(R.id.tx_delay);

        gui_cb_ignoreCharge = (CheckBox) findViewById(R.id.cb_ignore_charge);

        Button gui_bt_start = (Button) findViewById(R.id.bt_start);
        Button gui_bt_abort = (Button) findViewById(R.id.bt_abort);
        Button gui_bt_start_service = (Button) findViewById(R.id.bt_start_service);

        PowerConnectionReceiver.main = this;
        BootCompleteReceiver.main = this;

        state = TestState.IDLE;

        gui_bt_start.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (state == TestState.IDLE) {
                    state = TestState.RUNNING;
                    println("Running write test on storage " + gui_sp_loc.getSelectedItemPosition() + " for " + gui_tx_loop.getText() + " cycles");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            StorageLocation location = StorageLocation.PRIVATE;
                            switch (gui_sp_loc.getSelectedItemPosition()) {
                                case 0:
                                    location = StorageLocation.PRIVATE;
                                    break;
                                case 1:
                                    location = StorageLocation.EXTERNAL;
                                    break;
                                case 2:
                                    location = StorageLocation.SECONDARY;
                                    break;
                                default:
                                    dprintln("Unknown location option: " + gui_sp_loc.getSelectedItemPosition());
                                    break;
                            }
                            runIOTest(location, Long.parseLong(gui_tx_loop.getText().toString()), Long.parseLong(gui_tx_delay.getText().toString()));
                        }
                    }).start();
                } else {
                    println("Test process not in idle state.");
                }
            }
        });

        gui_bt_abort.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (state != TestState.IDLE)
                    state = TestState.ABORT_PENDING;
                println("abort clicked");
            }
        });

        gui_bt_start_service.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent intent = new Intent(getBaseContext(), BackgroundService.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startService(intent);
            }
        });

        gui_cb_ignoreCharge.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                flagIgnoreCharge = isChecked;
            }
        });

        println("I/O tester");

        String extState = Environment.getExternalStorageState();
        String extPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        String rootPath = Environment.getRootDirectory().getAbsolutePath();

        println("External Storage State: " + extState + ", " + extPath + ", " + rootPath);

        println("Data path: " + this.getFilesDir().getAbsolutePath());

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart() {
        super.onStart();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
//        client.connect();
//        Action viewAction = Action.newAction(
//                Action.TYPE_VIEW, // TODO: choose an action type.
//                "Main Page", // TODO: Define a title for the content shown.
//                // TODO: If you have web page content that matches this app activity's content,
//                // make sure this auto-generated web page URL is correct.
//                // Otherwise, set the URL to null.
//                Uri.parse("http://host/path"),
//                // TODO: Make sure this auto-generated app deep link URI is correct.
//                Uri.parse("android-app://com.oscar.WearOutAttack/http/host/path")
//        );
//        AppIndex.AppIndexApi.start(client, viewAction);
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.oscar.WearOutAttack/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }

    @Override
    public void onStop() {
        super.onStop();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.oscar.WearOutAttack/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
//        Action viewAction = Action.newAction(
//                Action.TYPE_VIEW, // TODO: choose an action type.
//                "Main Page", // TODO: Define a title for the content shown.
//                // TODO: If you have web page content that matches this app activity's content,
//                // make sure this auto-generated web page URL is correct.
//                // Otherwise, set the URL to null.
//                Uri.parse("http://host/path"),
//                // TODO: Make sure this auto-generated app deep link URI is correct.
//                Uri.parse("android-app://com.oscar.WearOutAttack/http/host/path")
//        );
//        AppIndex.AppIndexApi.end(client, viewAction);
//        client.disconnect();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.disconnect();
    }
}
