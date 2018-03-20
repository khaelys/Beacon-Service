package com.unime.beacontest.objectinteraction;

import android.util.Log;

import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Longs;
import com.unime.beacontest.beacon.Settings;

import org.altbeacon.beacon.Beacon;

import java.security.SecureRandom;
import java.util.Arrays;

import static com.unime.beacontest.AES256.decrypt;
import static com.unime.beacontest.AES256.encrypt;

/* BeaconCommand format.
 *
 * High level view
 * | 8 Bytes Counter | 6 Bytes Command | 2 Bytes Reserved |
 * | 2 Bytes User ID | 2 Bytes Object ID |
 *
 * 6 Bytes Command:
 * | 1 Byte CMD Type | 1 Byte CMD Class | 1 Byte CMD OP Code | 2 Bytes Parameters | 1 Byte BITMAP |
 *
 * 2 Bytes Object ID:
 * | 1 Byte Category | 1 Byte ID |
 */

public class BeaconCommand {
    public static final String TAG = "BeaconCommand";

    private static final int COUNTER_SIZE = 8;
    private static final int COMMAND_SIZE = 6;
    private static final int RESERVED_SIZE = 2;
    private static final int USER_ID_SIZE = 2;
    private static final int OBJECT_ID_SIZE = 2;

    private static final int DATA_PAYLOAD_SIZE = 20;
    private static final int ENCRYPTED_DATA_PAYLOAD_SIZE = 16;

    private static final int COUNTER_INDEX = 0;
    private static final int COMMAND_INDEX = COUNTER_INDEX + COUNTER_SIZE;
    private static final int RESERVED_INDEX = COMMAND_INDEX + COMMAND_SIZE;
    private static final int USER_ID_INDEX = RESERVED_INDEX + RESERVED_SIZE;
    private static final int OBJECT_ID_INDEX = USER_ID_INDEX + USER_ID_SIZE;

    // Command components offsets
    private static final int COMMAND_TYPE_OFFSET = COMMAND_INDEX;
    private static final int COMMAND_CLASS_OFFSET = COMMAND_TYPE_OFFSET + 1;
    private static final int COMMAND_OP_CODE_OFFSET = COMMAND_CLASS_OFFSET + 1;
    private static final int PARAMETERS_OFFSET = COMMAND_OP_CODE_OFFSET + 1;
    private static final int BITMAP_OFFSET = PARAMETERS_OFFSET + 2;

    private byte[] counter; // long
    private byte[] command; // hex
    // 2 byte are free
    private byte[] reserved = new byte[RESERVED_SIZE]; // random?
    private byte[] userId; // hex
    private byte[] objectId; // hex

    private byte[] dataPayload = new byte[DATA_PAYLOAD_SIZE];
    private byte[] encryptedDataPayload = new byte[ENCRYPTED_DATA_PAYLOAD_SIZE];

    private Beacon beacon;

    // todo remove this initialization after test
    private byte[] key;
    private byte[] iv;


    public BeaconCommand() {
        zeroInit(getDataPayload());
    }

    public byte[] getDataPayload() {
        return dataPayload;
    }

    public void setCounter(Long counter) {
        byte[] counterBytes = Longs.toByteArray(counter);

        System.arraycopy(counterBytes, 0, dataPayload, COUNTER_INDEX, COUNTER_SIZE);
    }

    public void setCommandType(String hexCommandType) {
        dataPayload[COMMAND_TYPE_OFFSET] = BaseEncoding.base16().decode(hexCommandType)[0];
    }

    public void setCommandClass(String hexCommandClass) {
        dataPayload[COMMAND_CLASS_OFFSET] = BaseEncoding.base16().decode(hexCommandClass)[0];
    }

    public void setCommandOpCode(String hexCommandOpCode) {
        dataPayload[COMMAND_OP_CODE_OFFSET] = BaseEncoding.base16().decode(hexCommandOpCode)[0];
    }

    public void setParameters(String hexParameter1, String hexParameter2) {
        dataPayload[PARAMETERS_OFFSET] = BaseEncoding.base16().decode(hexParameter1)[0];
        dataPayload[PARAMETERS_OFFSET + 1] = BaseEncoding.base16().decode(hexParameter2)[0];
    }

    // Bitmap input parameter: (byte)0b11111111
    public void setBitmap(byte bitmap) {
        dataPayload[BITMAP_OFFSET] = bitmap;
    }

    public void setReserved(String hexReserved) {
        byte[] reservedBytes = BaseEncoding.base16().decode(hexReserved);
        System.arraycopy(reservedBytes, 0, dataPayload, RESERVED_INDEX, RESERVED_SIZE);
    }

    public void randomizeReserved() {
        byte[] reservedBytes = new byte[RESERVED_SIZE];

        // randomize reserved bytes
        SecureRandom random = new SecureRandom();
        random.nextBytes(reservedBytes);

        System.arraycopy(reservedBytes, 0, dataPayload, RESERVED_INDEX, RESERVED_SIZE);
    }

    public void setUserId(String hexUserId) {
        byte[] userIdBytes = BaseEncoding.base16().decode(hexUserId);
        System.arraycopy(userIdBytes, 0, dataPayload, USER_ID_INDEX, USER_ID_SIZE);
    }

    public void setObjectId(String hexCategory, String hexObjectId) {
        dataPayload[OBJECT_ID_INDEX] = BaseEncoding.base16().decode(hexCategory)[0];
        dataPayload[OBJECT_ID_INDEX + 1] = BaseEncoding.base16().decode(hexObjectId)[0];
    }

    public void setKey(byte[] key) {
        this.key = key;
    }

    public void setIv(byte[] iv) {
        this.iv = iv;
    }

    public Beacon build() {
        // Encrypt Payload Data (first 16 Bytes)
        byte[] payloadToEncrypt = new byte[ENCRYPTED_DATA_PAYLOAD_SIZE];
        System.arraycopy(dataPayload, 0, payloadToEncrypt, 0, ENCRYPTED_DATA_PAYLOAD_SIZE);

        try {
            encryptedDataPayload = encrypt(payloadToEncrypt, key, iv);
            Log.d(TAG, "encrypted: " + BaseEncoding.base16().lowerCase().encode(encryptedDataPayload));

            String decryptedPayload = decrypt(encryptedDataPayload, key, iv);
            Log.d(TAG, "decrypted: counter: " + decryptedPayload.substring(0,16) +
                    " command: " + decryptedPayload.substring(16,28) + " reserved: " +
                    decryptedPayload.substring(28, 32));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new Beacon.Builder()
                .setId1(findUUID(encryptedDataPayload))
                .setId2(findMajor(dataPayload))
                .setId3(findMinor(dataPayload))
                .setManufacturer(Settings.MANUFACTURER_ID)
                .setTxPower(Settings.TX_POWER)
                .setRssi(Settings.RSSI)
                .setDataFields(Arrays.asList(new Long[]{0l})) // Remove this for beacon layouts without d: fields
                .build();

    }


    private String findUUID(final byte[] data){
        StringBuilder sb = new StringBuilder();
        for(int i = COUNTER_INDEX, offset = 0; i <= ENCRYPTED_DATA_PAYLOAD_SIZE-1; ++i, ++offset) {

            sb.append(String.format("%02x", (int)(data[i] & 0xff)));
            if (offset == 3 || offset == 5 || offset == 7 || offset == 9) {
                sb.append("-");
            }
        }
        Log.d(TAG, "hex: "+sb.toString());
        return sb.toString();
    }

    private String findMajor(final byte[] data){

        String major = String.format("%02x%02x", data[USER_ID_INDEX], data[USER_ID_INDEX + 1]);
        return major;
    }

    private String findMinor(final byte[] data){

        String minor = String.format("%02x%02x", data[OBJECT_ID_INDEX], data[OBJECT_ID_INDEX + 1]);
        return minor;
    }

    private void zeroInit(byte[] data) {
        for(int i=0; i < data.length; i++) {
            data[i] = 0;
        }
    }


}
