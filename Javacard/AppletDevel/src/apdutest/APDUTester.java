/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package apdutest;

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
            
        } catch (Exception ex) {
            System.out.println(ex.toString());
        }
    }
    
}
