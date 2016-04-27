/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package applet;

import javacard.framework.*;

/**
 *
 * @author User Lubo
 */
public class Applet7Zip extends javacard.framework.Applet {
    
    private final static byte CLA_7ZIPAPPLET = (byte) 0x13;
    
    private final static byte INS_SAY_SOMETHING = (byte) 0x01;
    
    private final static byte[] SOMETHING = new byte[] {
        0x48, 0x69, 0x2c, 0x20, 0x49, 0x20, 0x61, 0x6d, 0x20, 0x37, 0x2d, 0x5a, 
        0x69, 0x70, 0x20, 0x61, 0x70, 0x70, 0x6c, 0x65, 0x74, 0x2c, 0x20, 0x66, 
        0x65, 0x65, 0x6c, 0x20, 0x66, 0x72, 0x65, 0x65, 0x20, 0x74, 0x6f, 0x20, 
        0x74, 0x65, 0x73, 0x74, 0x20, 0x79, 0x6f, 0x75, 0x72, 0x20, 0x73, 0x6b, 
        0x69, 0x6c, 0x6c, 0x73, 0x20, 0x6f, 0x6e, 0x20, 0x6d, 0x65, 0x2e
    };
    
    protected Applet7Zip(byte[] bArray, short bOffset, byte bLength) {
        if(bLength > 0) {
            byte iLen = bArray[bOffset]; // aid length
            bOffset = (short) (bOffset + iLen + 1);
            byte cLen = bArray[bOffset]; // info length
            bOffset = (short) (bOffset + 3);
            byte aLen = bArray[bOffset]; // applet data length
        }
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
                case INS_SAY_SOMETHING:
                    saySomething(apdu);
                    break;
                default:
                    ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
            }
        } else {
            ISOException.throwIt( ISO7816.SW_CLA_NOT_SUPPORTED);
        }
    }
    
    private void saySomething(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short bufferLen = apdu.setIncomingAndReceive();
        
        Util.arrayCopyNonAtomic(SOMETHING , (short) 0 , 
                buffer , ISO7816.OFFSET_CDATA , (short) SOMETHING.length);
        
        apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, (short) SOMETHING.length);
    }
}
