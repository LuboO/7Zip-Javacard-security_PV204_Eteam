/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package apdutest;

import applet.Applet7Zip;

/**
 *
 * @author User Lubo
 */
public class APDUTester {
    private final static byte INSTALL_DATA[] = {};
    
    private final static byte APPLET_AID[] = {
        (byte) 0x37, (byte) 0x5a, (byte) 0x69, (byte) 0x70, (byte) 0x41,
        (byte) 0x70, (byte) 0x70, (byte) 0x6c, (byte) 0x65, (byte) 0x74
    };
    
    static SimulatorManager manager = new SimulatorManager();
    
    public static void main(String[] args) {
        try {
            manager.startSimulator(APPLET_AID, INSTALL_DATA, Applet7Zip.class);
            
            byte[] test = {0x13 , 0x01 , 0x00 , 0x00 , 0x00};
            
            manager.transmitAPDU(test);
            
        } catch (Exception ex) {
            System.out.println(ex);
        }
    }
    
}
