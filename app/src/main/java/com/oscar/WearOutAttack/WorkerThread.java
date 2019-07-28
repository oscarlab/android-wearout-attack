package com.oscar.WearOutAttack;

import android.util.Log;

import java.io.File;
import java.util.Random;

public class WorkerThread {
    private File _file;
    private Dynamo _dynamo;
    private long _count;
    private Thread _thread;
    private long _status;
    private byte[] _buf4k;
    final static long WORKER_STATUS_IDLE = 1;
    final static long WORKER_STATUS_START = 2;
    final static long WORKER_STATUS_RUNNING = 3;
    final static long WORKER_STATUS_ABORT = 4;

    public WorkerThread(File file, long filesize, Dynamo.IoEngine ioEngine, Dynamo.SyncType syncType, Dynamo.Direction direction) {
        _file = file;
        _dynamo = new Dynamo(file, filesize, ioEngine, syncType, direction);
        _dynamo.initialize();
        _count = 0;
        _status = WORKER_STATUS_IDLE;
        _buf4k = new byte[4096];
        Random rand = new Random();
        rand.nextBytes(_buf4k);
        file.deleteOnExit();
        Log.d("WorkerThread", "Creating working thread " + _file.getAbsolutePath());
    }

    public long getCount() {
        return _count;
    }

    public void abort() {
        // make sure abort thread during running, or simply return, wait on WORKER_STATUS_START
        Log.d("WorkerThread", "Abort working thread " + _file.getAbsolutePath());

        if (_status == WORKER_STATUS_IDLE || _status == WORKER_STATUS_ABORT)
            return;

        while (_status == WORKER_STATUS_START) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }

        _status = WORKER_STATUS_ABORT;
    }

    public void start() {
        Log.d("WorkerThread", "Start working thread " + _file.getAbsolutePath());
        if (_status != WORKER_STATUS_IDLE) {
            Log.w("WorkerThread", "Starting worker in incorrect status ("+ _status + ")");
            return;
        }
        _status = WORKER_STATUS_START;
        _thread = new Thread(new Runnable() {
            @Override
            public void run() {

                _status = WORKER_STATUS_RUNNING;
                while (true) {
                    long offset = 0;
                    if (MainActivity.CONF_IO_PATTERN == 1) {
                        offset = _dynamo.nextRand4KOffset();
                    } else if (MainActivity.CONF_IO_PATTERN == 2) {
                        offset = _dynamo.nextSeq4KOffset();
                    } else if (MainActivity.CONF_IO_PATTERN == 3) {
                        // Do nothing
                    }
                    if (MainActivity.CONF_FLAG_MIMIC) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            break;
                        }
                    } else {
                        // FIXME: update with real random data
                        _dynamo.doWrite(offset, _buf4k);
                    }

                    if (MainActivity.CONF_WRITE_DELAY > 0) {
                        try {
                            Thread.sleep(MainActivity.CONF_WRITE_DELAY);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    if (MainActivity.CONF_SYNC_COUNT > 0) {
                        // TODO: add sync count support
                    } else {
                        _dynamo.doSync();
                    }

                    _count += 1;

                    // Battery status check should belong to caller

                    if (_status == WORKER_STATUS_ABORT) {
                        break;
                    }
                }

                _status = WORKER_STATUS_IDLE;
            }
        });

        _thread.start();
    }
}
