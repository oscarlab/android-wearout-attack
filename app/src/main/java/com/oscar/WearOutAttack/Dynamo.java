package com.oscar.WearOutAttack;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Random;


public class Dynamo {
    String _filename;
    File _file;
    FileOutputStream _fos;
    FileInputStream _fis;
    RandomAccessFile _randFile;
    long _last_offset;
    private long _filesize;
    private long _cur_write_offset;
    private long _cur_read_offset;

    Random _rand;

    int _fd;

    public static native int NativeSyncInit();
    public native int NativeSyncOpen(String filename);
    public native int NativeSyncWrite4K(int fd);
    public native int NativeSyncWrite128K(int fd);
    public native int NativeSyncFSync(int fd);

    private void dprintln(String str) {
        Log.i("Dynamo", str);
    }

    public enum SyncType {
        FLUSH,
        FORCE,
        FDSYNC,
        /* Following ones only apply to RANDOMACCESS */
        RWS,
        RWD,
        RW,
    }
    private SyncType _syncType;

    public enum IoEngine {
        STREAMIO,
        RANDOMACCESS,
        NATIVESYNC,
    }
    private IoEngine _ioEngine;

    public enum Direction {
        INPUT,
        OUTPUT
    }
    Direction _direction;

    public Dynamo(File file, long filesize, IoEngine ioEngine, SyncType syncType, Direction direction) {
        _file = file;
        _filesize = filesize;
        _direction = direction;
        _ioEngine = ioEngine;
        _syncType = syncType;
        _rand = new Random();
        _last_offset = 0;

        switch (_direction) {
            case INPUT:
                System.out.println("Read test not supported");
                System.exit(1);
                break;
            case OUTPUT:
                try {
                    if (ioEngine == IoEngine.STREAMIO) {
                        _fos = new FileOutputStream(_file);
                    } else if (ioEngine == IoEngine.RANDOMACCESS){
                        String mode = "";
                        if (syncType == SyncType.RWD)
                            mode = "rwd";
                        else if (syncType == SyncType.RWS)
                            mode = "rws";
                        else if (syncType == SyncType.RW)
                            mode = "rw";
                        else {
                            System.out.println("WARNING, possibly unsupported sync type for random access mode" + syncType);
                            mode = "rws";
//                            System.out.println("Unsupported synctype for random access mode " + syncType);
//                            System.exit(1);
                        }

                        _randFile = new RandomAccessFile(_file, mode);
                    } else if (ioEngine == IoEngine.NATIVESYNC) {
                        _fd = NativeSyncOpen(_file.getAbsolutePath());
                        System.out.println("Native file opened with fd " + _fd);
                    } else {
                        System.out.println("Unknonwn IoEngine specified " + ioEngine);
                    }
                } catch (IOException exception) {
                    System.out.println(exception);
                    System.exit(1);
                }
                break;
        }
    }

    public long nextRandOffset() {
        return 0;
    }

    public long nextRand4KOffset() {
        long offset;

        offset = Math.abs(_rand.nextLong()) % (_filesize - 4096);

        return offset;
    }

    public long nextSeq4KOffset() {
        long offset;

        offset = (_last_offset + 4096) % (_filesize - 4096);
        _last_offset = offset;

        return offset;
    }

    public void initialize() {
        if (_ioEngine == IoEngine.RANDOMACCESS) {
            try {
                _randFile.setLength(_filesize);
            } catch (IOException exception) {
                System.out.println(exception);
                System.exit(1);
            }
        }
    }

    public void cleanup() {
        if (_ioEngine == IoEngine.RANDOMACCESS) {
            try {
                _randFile.close();
                _randFile = null;
            } catch (IOException exception) {
                System.out.println(exception);
                System.exit(1);
            }
        }
    }

    public void doWrite(long offset, byte[] buf) {
        _cur_write_offset = offset;
        try {
            switch (_ioEngine) {
                case STREAMIO:
                    _fos.write(buf);
                    break;
                case RANDOMACCESS:
                    _randFile.seek(offset);
                    _randFile.write(buf);
                    break;
                case NATIVESYNC:
                    if (MainActivity.CONF_NATIVE_WRITE_SIZE == 4)
                        NativeSyncWrite4K(_fd);
                    else if (MainActivity.CONF_NATIVE_WRITE_SIZE == 128)
                        NativeSyncWrite128K(_fd);
                    else
                        System.out.println("Unknown write size " + MainActivity.CONF_NATIVE_WRITE_SIZE);
                    break;
                default:
                    System.out.println("Unhandled IOEngine " + _ioEngine);
            }
        } catch (IOException exception) {
            System.out.println(exception);
        }
    }

    public void doRead(long offset) {
        _cur_read_offset = offset;
    }

    public void doSync() {
        try {
            if (_ioEngine == IoEngine.NATIVESYNC) {
                switch (_syncType) {
                    case FLUSH:
                    case FDSYNC:
                        NativeSyncFSync(_fd);
                        break;
                    case RWD:
                    case RWS:
                        //Do nothing
                        break;
                }
                return;
            }
            if (_ioEngine == IoEngine.RANDOMACCESS) {
                switch (_syncType) {
                    case FLUSH:
                        //not supported
                        break;
                    case FDSYNC:
                        _randFile.getFD().sync();
                        break;
                    default:
                        break;
                }
                return;
            }
            switch (_syncType) {

                    case FLUSH:
                        _fos.flush();
                        break;
                    case FORCE:
                        _fos.getChannel().force(true);
                        break;
                    case FDSYNC:
                        _fos.getFD().sync();
                        break;
                    default:
                        /* Do nothing for RWD/RWS? */
                        break;
            }
        } catch (IOException exception) {
            System.out.println(exception);
            System.exit(1);
        }

    }

    static {
        System.loadLibrary("io-tester");
        NativeSyncInit();
    }
}
