/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package apdutest;

import com.licel.jcardsim.io.CAD;
import com.licel.jcardsim.io.JavaxSmartCardInterface;
import javacard.framework.AID;

/**
 *
 * @author User Lubo
 */
public class SimulatorManager {
    
    private static JavaxSmartCardInterface mSimulator = null;
    private static CAD mCad = null;
    
    public boolean startSimulator(byte[] AID , byte[] installData , Class appletClass) {
        System.setProperty("com.licel.jcardsim.terminal.type", "2");
        mCad = new CAD(System.getProperties());
        mSimulator = (JavaxSmartCardInterface) mCad.getCardInterface();
        AID appletAID = new AID(AID , (short) 0 , (byte) AID.length);
        mSimulator.installApplet(appletAID, appletClass, installData, (short) 0, (byte) installData.length);
        return mSimulator.selectApplet(appletAID);
    }
    
    public byte[] transmitAPDU(byte apdu[]) throws Exception {
        return transmitAPDU(apdu, true);
    }
    
    public byte[] transmitAPDU(byte apdu[], boolean toConsole) throws Exception {
        if(toConsole) {
            System.out.println(">>>>");
            System.out.println(bytesToHex(apdu));
        }
        byte[] responseBytes = mSimulator.transmitCommand(apdu);
        if(toConsole) {
            System.out.println(bytesToHex(responseBytes));
            System.out.println("<<<<");
        }
        return responseBytes;
    }
    
    private String bytesToHex(byte[] data) {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            buf.append(byteToHex(data[i]));
            buf.append(" ");
        }
        return (buf.toString());
    }
    
    private String byteToHex(byte data) {
        StringBuilder buf = new StringBuilder();
        buf.append(toHexChar((data >>> 4) & 0x0F));
        buf.append(toHexChar(data & 0x0F));
        return buf.toString();
    }
    
    private char toHexChar(int i) {
        if ((0 <= i) && (i <= 9)) {
            return (char) ('0' + i);
        } else {
            return (char) ('a' + (i - 10));
        }
    }
}
