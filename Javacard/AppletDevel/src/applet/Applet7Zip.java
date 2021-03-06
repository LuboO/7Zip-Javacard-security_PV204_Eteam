/*
 * PACKAGEID: 37 5A 69 70 41
 * APPLETID: 37 5A 69 70 41 70 70 6C 65 74
 */
package applet;

import javacard.framework.*;
import javacard.security.*;

/**
 * Applet designed for distributing passwords for 7Zip archives.
 * Modified 7Zip application will contact smartcard, and smartcard
 * will derive password and pass it to 7Zip.
 * @author LuboO
 */
public class Applet7Zip extends javacard.framework.Applet {
    /* Constant fields are public so that they can be accessed from Unit Tests
       during development. Moreover they are not exactly secret. */
    
    // Main instruction class
    public final static byte CLA_7ZIPAPPLET = (byte) 0x13;
    
    // Possible instructions. Wrong instruction will yield ISO7816.SW_INS_NOT_SUPPORTED
    /* Checks user PIN, if correct PIN was provided another actions can be executed */
    public final static byte INS_LOGIN_USER           = (byte) 0x70;
    /* Sets user PIN to new value. Possible only for logged in user or admin */
    public final static byte INS_SET_PIN_USER         = (byte) 0x71;
    /* Checks admin PIN, if correct PIN was provided another actions can be executed */
    public final static byte INS_LOGIN_ADMIN          = (byte) 0x72;
    /* Sets admin PIN to new value. Possible only for logged in admin */
    public final static byte INS_SET_PIN_ADMIN        = (byte) 0x73;
    /* Forgets master key for key derivation and generates new one. 
       Use with care, user won't be able to decrypt files 
       encrypted using old master key. Only after user login. */
    public final static byte INS_GENERATE_MASTER_KEY  = (byte) 0x74;
    /* Retrieves current counter with which last key was derived. 
       Only after user login */
    public final static byte INS_RETRIEVE_CURRENT_CTR = (byte) 0x75;
    /* Increments internal counter and derives key using it and master key. 
       Key is retrieved. Only after user login. */
    public final static byte INS_DERIVE_NEW_KEY       = (byte) 0x76;
    /* Derives key from master key based on counter sent in apdu. 
       Only after user login. */
    public final static byte INS_DERIVE_CTR_KEY       = (byte) 0x77;
    
    /* Return error responses */
    public final static short SW_VERIFICATION_FAILED       = 0x6300;
    public final static short SW_PIN_VERIFICATION_REQUIRED = 0x6301; 
    
    public final static short SIZE_MASTER_KEY_BYTE        = (short) 32;
    public final static short SIZE_COUNTER_BYTE           = (short) 8; 
    public final static short SIZE_RAM_ARRAY_BYTE         = (short) 256;
    public final static short SIZE_HMAC_SHA256_BLOCK_BYTE = (short) 64;
    
    public final static byte PIN_USER_MAX_TRIES  = (byte) 0x03;
    public final static byte PIN_USER_MIN_LENGTH = (byte) 0x04;
    public final static byte PIN_USER_MAX_LENGTH = (byte) 0x10;
    
    public final static byte PIN_ADMIN_MAX_TRIES = (byte) 0x7F;
    public final static byte PIN_ADMIN_LENGTH    = (byte) 0x10;
    
    private final static byte OPAD_BYTE = (byte) 0x5c;
    private final static byte IPAD_BYTE = (byte) 0x36;
    
    private OwnerPIN      mUserPin    = null;
    private OwnerPIN      mAdminPin   = null;
    private RandomData    mRandGen    = null;
    private MessageDigest mHashEngine = null;
    private AESKey        mMasterKey  = null;
    
    /* Fast RAM memory for various operations */
    private byte mRamArray[] = null;
    /* Persistent memory for 8 byte counter */
    private byte mCounter[] = null;
    
    
    protected Applet7Zip(byte[] bArray, short bOffset, byte bLength) {
        if(bLength > 0) {
            /* Some info loading */
            byte iLen = bArray[bOffset]; // aid length
            bOffset = (short) (bOffset + iLen + 1);
            byte cLen = bArray[bOffset]; // info length
            bOffset = (short) (bOffset + 3);
            byte aLen = bArray[bOffset]; // applet data length
        }
        /* Create RAM array set to zero characters (for PIN setting) */
        mRamArray = JCSystem.makeTransientByteArray(SIZE_RAM_ARRAY_BYTE , JCSystem.CLEAR_ON_DESELECT);
        Util.arrayFillNonAtomic(mRamArray, (short) 0, SIZE_RAM_ARRAY_BYTE, (byte) 0x30);

        /* Create persistent counter set to zero */
        mCounter = new byte[SIZE_COUNTER_BYTE];
        Util.arrayFillNonAtomic(mCounter, (short) 0, SIZE_COUNTER_BYTE, (byte) 0x00);

        /* Initialize PINs to zeroes */
        mUserPin = new OwnerPIN(PIN_USER_MAX_TRIES , PIN_USER_MAX_LENGTH);
        mUserPin.update(mRamArray, (short) 0, PIN_USER_MIN_LENGTH);

        mAdminPin = new OwnerPIN(PIN_ADMIN_MAX_TRIES , PIN_ADMIN_LENGTH);
        mAdminPin.update(mRamArray, (short) 0, PIN_ADMIN_LENGTH);

        /* Fill ram array with binary zeroes */
        Util.arrayFillNonAtomic(mRamArray, (short) 0, SIZE_RAM_ARRAY_BYTE, (byte) 0x00);
        
        /* Initialize random generator */
        mRandGen = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
        /* Generate rnd data */
        mRandGen.generateData(mRamArray, (short) 0, SIZE_MASTER_KEY_BYTE);
        /* Initialize and set master key to random data */
        mMasterKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_256, false);
        mMasterKey.setKey(mRamArray , (short) 0);
        /* Zero out the array again */
        Util.arrayFillNonAtomic(mRamArray, (short) 0, SIZE_MASTER_KEY_BYTE, (byte) 0x00);
        
        /* Initialize hashing engine */
        mHashEngine = MessageDigest.getInitializedMessageDigestInstance(MessageDigest.ALG_SHA_256, false);
        
        register();
    }
    
    public static void install(byte[] bArray, short bOffset, byte bLength) throws ISOException {
        new Applet7Zip(bArray, bOffset,bLength);
    }
    
    public void process(APDU apdu) {
        if(selectingApplet())
            return;
        byte[] buffer = apdu.getBuffer();
        
        if(buffer[ISO7816.OFFSET_CLA] == CLA_7ZIPAPPLET) {
            switch(buffer[ISO7816.OFFSET_INS]) {
                case INS_LOGIN_USER:
                    loginUser(apdu); break;
                case INS_SET_PIN_USER:
                    setPinUser(apdu); break;
                case INS_LOGIN_ADMIN:
                    loginAdmin(apdu); break;
                case INS_SET_PIN_ADMIN:
                    setPinAdmin(apdu); break;
                case INS_GENERATE_MASTER_KEY:
                    generateMasterKey(apdu); break;
                case INS_RETRIEVE_CURRENT_CTR:
                    retrieveCurrentCtr(apdu); break;
                case INS_DERIVE_NEW_KEY:
                    deriveNewKey(apdu); break;
                case INS_DERIVE_CTR_KEY:
                    deriveCtrKey(apdu); break;
                default:
                    ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
            }
        } else {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }
    }
    
    public boolean select() {
        return true;
    }
    
    public void deselect() {
        Util.arrayFillNonAtomic(mRamArray, (short) 0, SIZE_RAM_ARRAY_BYTE, (byte) 0x00);
    }
    
    private void loginUser(APDU apdu) {
        byte[]  buffer = apdu.getBuffer();
        short   bufLen = apdu.setIncomingAndReceive();
        
        if(false == mUserPin.check(buffer, ISO7816.OFFSET_CDATA, (byte) bufLen))
            ISOException.throwIt(SW_VERIFICATION_FAILED);
    }
    
    private void setPinUser(APDU apdu) {
        byte[]  buffer = apdu.getBuffer();
        short   bufLen = apdu.setIncomingAndReceive();
        
        if((false == mUserPin.isValidated()) && (false == mAdminPin.isValidated()))
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
        
        /* Check length of PIN */
        if(bufLen < PIN_USER_MIN_LENGTH || bufLen > PIN_USER_MAX_LENGTH)
            ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        
        JCSystem.beginTransaction();
        mUserPin.update(buffer, ISO7816.OFFSET_CDATA, (byte) bufLen);
        JCSystem.commitTransaction();
    }
    
    private void loginAdmin(APDU apdu) {
        byte[]  buffer = apdu.getBuffer();
        short   bufLen = apdu.setIncomingAndReceive();
        
        if(false == mAdminPin.check(buffer, ISO7816.OFFSET_CDATA, (byte) bufLen))
            ISOException.throwIt(SW_VERIFICATION_FAILED);
    }
    
    private void setPinAdmin(APDU apdu) {
        byte[]  buffer = apdu.getBuffer();
        short   bufLen = apdu.setIncomingAndReceive();
        
        if(false == mAdminPin.isValidated())
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
        
        /* Check length of PIN */
        if(bufLen != PIN_ADMIN_LENGTH)
            ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        
        JCSystem.beginTransaction();
        mAdminPin.update(buffer, ISO7816.OFFSET_CDATA, PIN_ADMIN_LENGTH);
        JCSystem.commitTransaction();
    }
    
    private void generateMasterKey(APDU apdu) {
        /* Buffer isn't needed here, so just receive it */
        apdu.setIncomingAndReceive();
        
        if(false == mUserPin.isValidated())
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
        
        JCSystem.beginTransaction();
        /* Generate new data and set its value to key */
        mRandGen.generateData(mRamArray, (short) 0, SIZE_MASTER_KEY_BYTE);
        mMasterKey.setKey(mRamArray, (short) 0);
        /* Zero out array again */
        Util.arrayFillNonAtomic(mRamArray, (short) 0, SIZE_MASTER_KEY_BYTE, (byte) 0x00);
        /* Set counter to zero */
        Util.arrayFillNonAtomic(mCounter, (short) 0 , SIZE_COUNTER_BYTE, (byte) 0x00);
        JCSystem.commitTransaction();
    }
    
    private void retrieveCurrentCtr(APDU apdu) {
        apdu.setIncomingAndReceive();
        
        if(false == mUserPin.isValidated())
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
        
        /* Sends back current value of counter */
        apdu.setOutgoing();
        apdu.setOutgoingLength(SIZE_COUNTER_BYTE);
        apdu.sendBytesLong(mCounter , (short) 0 , SIZE_COUNTER_BYTE);
    }
    
    private void deriveNewKey(APDU apdu) {
        apdu.setIncomingAndReceive();
        
        if(false == mUserPin.isValidated())
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
        
        /* Increments counter, derives new key and sends it back */
        JCSystem.beginTransaction();
        incCounterNonAtomic();
        deriveKeyToRam(mCounter , (short) 0);
        JCSystem.commitTransaction();
        apdu.setOutgoing();
        apdu.setOutgoingLength(SIZE_MASTER_KEY_BYTE);
        apdu.sendBytesLong(mRamArray, (short) 0, SIZE_MASTER_KEY_BYTE);
    }
    
    private void deriveCtrKey(APDU apdu) {
        byte[]  buffer = apdu.getBuffer();
        short   bufLen = apdu.setIncomingAndReceive();
        
        if(false == mUserPin.isValidated())
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
        
        if(false == cmpCounter(buffer, ISO7816.OFFSET_CDATA , bufLen))
            ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        
        /* Derives key from counter and master key, sends it back */
        JCSystem.beginTransaction();
        deriveKeyToRam(buffer, (short) ISO7816.OFFSET_CDATA);
        JCSystem.commitTransaction();
        apdu.setOutgoing();
        apdu.setOutgoingLength(SIZE_MASTER_KEY_BYTE);
        apdu.sendBytesLong(mRamArray, (short) 0, SIZE_MASTER_KEY_BYTE);
    }
    
    private void incCounterNonAtomic() {
        for(short i = SIZE_COUNTER_BYTE - 1; i >= 0; --i) {
            ++mCounter[i];
            if(mCounter[i] != (short) 0)
                break;
        }
    }
    
    /**
     * Checks whether given counter in argument is lesser or equal to persistent
     * counter stored on card.
     * @param buffer Buffer with stored counter received in apdu
     * @param ctrOffset Offset at which starts counter
     * @param ctrLen Length of counter
     * @return True if counter is lesser or equal to mCounter and their sizes are equal, false
     * otherwise
     */
    private boolean cmpCounter(byte[] buffer , byte ctrOffset , short ctrLen) {
        if(buffer == null)
            return false;
        
        if(ctrLen != SIZE_COUNTER_BYTE)
            return false;
        
        short mCounterByte;
        short bufferByte;
        
        for(short i = (short) 0 ; i < SIZE_COUNTER_BYTE ; ++i) {
            mCounterByte = (short) (mCounter[i] & 0xFF);
            bufferByte = (short) (buffer[(short)(ctrOffset + i)] & 0xFF);
            
            if(bufferByte > mCounterByte) {
                return false;
            } else if(bufferByte < mCounterByte) {
                break;
            }
        }
        return true;
    }
    
    private void deriveKeyToRam(byte[] buffer, short ctrOff) {
        /* Zero out working array */
        Util.arrayFillNonAtomic(mRamArray, (short) 0, SIZE_RAM_ARRAY_BYTE, (byte) 0x00);
        /* Implements HMAC because cards alone can't handle it. */
        short keyOuterOff = 0;
        short keyInnerOff = (short) (keyOuterOff + SIZE_HMAC_SHA256_BLOCK_BYTE);
        short counterOff  = (short) (keyInnerOff + SIZE_HMAC_SHA256_BLOCK_BYTE);
        mMasterKey.getKey(mRamArray, keyInnerOff);
        mMasterKey.getKey(mRamArray, keyOuterOff);
        arrayXorByte(mRamArray , keyOuterOff , OPAD_BYTE , SIZE_HMAC_SHA256_BLOCK_BYTE);
        arrayXorByte(mRamArray , keyInnerOff , IPAD_BYTE , SIZE_HMAC_SHA256_BLOCK_BYTE);
        Util.arrayCopyNonAtomic(buffer , ctrOff , mRamArray , counterOff , SIZE_COUNTER_BYTE);
        mHashEngine.doFinal(mRamArray, keyInnerOff, (short)(SIZE_HMAC_SHA256_BLOCK_BYTE + SIZE_COUNTER_BYTE), 
                            mRamArray, keyInnerOff);
        mHashEngine.doFinal(mRamArray, keyOuterOff, (short)(SIZE_HMAC_SHA256_BLOCK_BYTE + SIZE_MASTER_KEY_BYTE), 
                            mRamArray, keyOuterOff);
        /* Derived key is now happily in ram on offset 0 */
    }
    
    private void arrayXorByte(byte[] arr , short off , byte toXor , short len) {
        short i = 0;
        while(i < len) {
            arr[(short)(off + i)] = (byte) (arr[(short)(off + i)] ^ toXor);
            ++i;
        }
    }
}
