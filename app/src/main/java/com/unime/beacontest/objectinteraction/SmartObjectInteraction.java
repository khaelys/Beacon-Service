package com.unime.beacontest.objectinteraction;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.google.common.io.BaseEncoding;
import com.unime.beacontest.Settings;
import com.unime.beacontest.beacon.utils.BeaconModel;
import com.unime.beacontest.beacon.utils.BeaconService;
import com.unime.beacontest.beacon.utils.Filter;
import com.unime.beacontest.beacon.utils.ScanFilterUtils;

import static com.unime.beacontest.beacon.ActionsBeaconBroadcastReceiver.ACTION_SCAN_ACK;

/**
 *
 */

public class SmartObjectInteraction {
    private static SmartObjectInteraction instance;

    private static final String SMART_OBJECT_INTERACTION_TAG = "SmartObjectInteraction";
    private static final int SENDING_DURATION_MILLIS = 300;
    // private static final int SENDING_DELAY = 0;
    private static final int SCANNING_DURATION_MILLIS = SENDING_DURATION_MILLIS;
    private static final int SCANNING_DELAY_MILLIS = 100;
    public static final int MAX_ACK_RETRY = 2;
    private static final String ACK_VALUE = "ffffff";

    private BeaconCommand beaconCommand;
    private BeaconService beaconService;
    private int retryCounter = 0;

    private SmartObjectInteraction(Context context) {
        this.beaconService = new BeaconService(context);
    }

    public static SmartObjectInteraction getInstance(Context context) {
        if(instance == null) {
            instance = new SmartObjectInteraction(context);
        }

        return instance;
    }

    public void incRetryCounter() {
        retryCounter++;
    } // TODO counter overflow

    public void resetCounter() {
        retryCounter = 0;
    }

    public int getRetryCounter() {
        return retryCounter;
    }

    public void setBeaconCommand(BeaconCommand beaconCommand) {
        this.beaconCommand = beaconCommand;
    }

    private Filter ackFilter = (data) -> {
        //String hexData = BaseEncoding.base16().encode(data);
        int manufacturerId = ScanFilterUtils.getManufacturerId(data);

        if(BeaconModel.isAltBeacon(data) && (manufacturerId == Settings.MANUFACTURER_ID) &&
                BeaconModel.findMinor(data).equals(Settings.OBJECT_ID)) {
            Log.d(SMART_OBJECT_INTERACTION_TAG, "ackFilter: " + BaseEncoding.base16().lowerCase().encode(data));
            return true;
        }
        return false;
    };

    public void interact() {
        beaconService.sending(beaconCommand.build(), SENDING_DURATION_MILLIS);

        HandlerThread handlerThread = new HandlerThread("BeaconScanHandlerThread");
        handlerThread.start();
        Looper looper = handlerThread.getLooper();

        Handler delayScan = new Handler(looper);
        delayScan.postDelayed(
                () -> {
                    beaconService.scanning(
                            ackFilter,
                            Settings.SIGNAL_THRESHOLD,
                            SCANNING_DURATION_MILLIS,
                            ACTION_SCAN_ACK,
                            handlerThread
                    );
                }, SCANNING_DELAY_MILLIS
        );
    }
}
