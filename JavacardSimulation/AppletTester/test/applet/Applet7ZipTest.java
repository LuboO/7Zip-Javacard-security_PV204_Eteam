/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package applet;

import apdutest.SimulatorManager;
import javacard.framework.ISO7816;
import org.bouncycastle.util.Arrays;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author User Lubo
 */
public class Applet7ZipTest {
    
    public Applet7ZipTest() {
    }
    
    SimulatorManager manager;
    
    private final static byte INSTALL_DATA[] = {};
    
    private final static byte APPLET_AID[] = {
        (byte) 0x37, (byte) 0x5a, (byte) 0x69, (byte) 0x70, (byte) 0x41,
        (byte) 0x70, (byte) 0x70, (byte) 0x6c, (byte) 0x65, (byte) 0x74
    };
    
    @Before
    public void setUp() {
        manager = new SimulatorManager();
        manager.startSimulator(APPLET_AID, INSTALL_DATA, Applet7Zip.class);
    }
    
    @After
    public void tearDown() {
        manager = null;
    }

    @Test
    public void badInsByte() throws Exception {
        byte[] buffer = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , 0x69 , 0x00 , 0x00 , 
            0x00
        };
        byte[] recv = manager.transmitAPDU(buffer);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_INS_NOT_SUPPORTED));
    }
    
    @Test
    public void badClaByte() throws Exception {
        byte[] buffer = 
        {
            Applet7Zip.CLA_7ZIPAPPLET - 1 , 0x00 , 0x00 , 0x00,
            0x00
        };
        byte[] recv = manager.transmitAPDU(buffer);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_CLA_NOT_SUPPORTED));
    }
    
    @Test
    public void loginUserBadPin() throws Exception  {
        byte[] buffer = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_USER , 0x00 , 0x00 ,
            0x04 , 0x00 , 0x01 , 0x02 , 0x03
        };
        byte[] recv = manager.transmitAPDU(buffer);
        assertTrue(cmpResponseCode(recv, Applet7Zip.SW_VERIFICATION_FAILED));
    }
    
    @Test
    public void loginUserGoodPin() throws Exception  {
        byte[] buffer = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_USER , 0x00 , 0x00 ,
            0x04 , 0x00 , 0x00 , 0x00 , 0x00
        };
        byte[] recv = manager.transmitAPDU(buffer);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
    }
    
    @Test
    public void loginUserPinTriesBlock() throws Exception  {
        byte[] badPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_USER , 0x00 , 0x00 ,
            0x04 , 0x01 , 0x01 , 0x01 , 0x01
        };
        byte[] goodPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_USER , 0x00 , 0x00 ,
            0x04 , 0x00 , 0x00 , 0x00 , 0x00
        };
        for(int i = 0 ; i < Applet7Zip.PIN_USER_MAX_TRIES ;  ++i) {
            manager.transmitAPDU(badPin);            
        }
        byte[] recv = manager.transmitAPDU(goodPin);
        assertTrue(cmpResponseCode(recv, Applet7Zip.SW_VERIFICATION_FAILED));
    }
    
    @Test
    public void loginUserPinTriesNoBlock() throws Exception  {
        byte[] badPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_USER , 0x00 , 0x00 ,
            0x04 , 0x01 , 0x01 , 0x01 , 0x01
        };
        byte[] goodPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_USER , 0x00 , 0x00 ,
            0x04 , 0x00 , 0x00 , 0x00 , 0x00
        };
        for(int i = 0 ; i < Applet7Zip.PIN_USER_MAX_TRIES - 1 ;  ++i) {
            manager.transmitAPDU(badPin);            
        }
        byte[] recv = manager.transmitAPDU(goodPin);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
    }
    
    @Test
    public void setPinUserNoLogin() throws Exception  {
        byte[] buffer = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_SET_PIN_USER , 0x00 , 0x00 , 
            0x04 , 0x01 , 0x01 , 0x01 , 0x01
        };
        byte[] recv = manager.transmitAPDU(buffer);
        assertTrue(cmpResponseCode(recv, Applet7Zip.SW_PIN_VERIFICATION_REQUIRED));
    }
    
    @Test
    public void setPinUserUserLogin() throws Exception  {
        byte[] oldPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_USER , 0x00 , 0x00 ,
            0x04 , 0x00 , 0x00 , 0x00 , 0x00
        };
        byte[] newPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_SET_PIN_USER , 0x00 , 0x00 ,
            0x05 , 0x01 , 0x01 , 0x01 , 0x01 , 0x01
        };
        byte[] authorizedIns = 
        {
            Applet7Zip.CLA_7ZIPAPPLET, Applet7Zip.INS_RETRIEVE_CURRENT_CTR , 0x00 , 0x00,
            0x00
        };
        byte[] recv;
        /* Login */
        recv = manager.transmitAPDU(oldPin);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
        /* Change pin */
        recv = manager.transmitAPDU(newPin);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
        /* I should be logged out after pin change */
        recv = manager.transmitAPDU(authorizedIns);
        assertTrue(cmpResponseCode(recv, Applet7Zip.SW_PIN_VERIFICATION_REQUIRED));
        /* Login with new pin should be success */
        newPin[ISO7816.OFFSET_INS] = Applet7Zip.INS_LOGIN_USER;
        recv = manager.transmitAPDU(newPin);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
    }
    
    @Test
    public void setPinUserAdminLogin() throws Exception  {
        byte[] adminPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_ADMIN , 0x00 , 0x00 ,
            0x10 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00
        };
        byte[] newPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_SET_PIN_USER , 0x00 , 0x00 , 
            0x04 , 0x00 , 0x00 , 0x01 , 0x01
        };
        byte[] recv;
        recv = manager.transmitAPDU(adminPin);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
        recv = manager.transmitAPDU(newPin);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
        newPin[ISO7816.OFFSET_INS] = Applet7Zip.INS_LOGIN_USER;
        recv = manager.transmitAPDU(newPin);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
    }
    
    @Test
    public void setUserPinBadLength() throws Exception  {
        byte[] validPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_USER , 0x00 , 0x00 ,
            0x04 , 0x00 , 0x00 , 0x00 , 0x00
        };
        byte[] shortPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_SET_PIN_USER , 0x00 , 0x00 ,
            0x03 , 0x00 , 0x00 , 0x00
        };
        byte[] longPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_SET_PIN_USER , 0x00 , 0x00 ,
            0x11 , 
            0x00 , 0x00 , 0x00 , 0x00 ,
            0x00 , 0x00 , 0x00 , 0x00 ,
            0x00 , 0x00 , 0x00 , 0x00 ,
            0x00 , 0x00 , 0x00 , 0x00 ,
            0x00
        };
        byte[] recv;
        recv = manager.transmitAPDU(validPin);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
        recv = manager.transmitAPDU(shortPin);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_DATA_INVALID));
        recv = manager.transmitAPDU(longPin);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_DATA_INVALID));
    }
    
    @Test
    public void loginAdminBadPin() throws Exception  {
        byte[] badPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_ADMIN , 0x00 , 0x00 ,
            0x10 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x01
        };
        byte[] recv = manager.transmitAPDU(badPin);
        assertTrue(cmpResponseCode(recv, Applet7Zip.SW_VERIFICATION_FAILED));
    }
    
    @Test
    public void loginAdminGoodPin() throws Exception  {
        byte[] goodPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_ADMIN , 0x00 , 0x00 ,
            0x10 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00
        };
        byte[] recv = manager.transmitAPDU(goodPin);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
    }
    
    @Test
    public void loginAdminPinTriesBlock() throws Exception  {
        byte[] badPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_ADMIN , 0x00 , 0x00 ,
            0x10 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x01
        };
        byte[] goodPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_ADMIN , 0x00 , 0x00 ,
            0x10 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00
        };
        for(int i = 0 ; i < Applet7Zip.PIN_ADMIN_MAX_TRIES ; ++i)
            manager.transmitAPDU(badPin);
            
        byte[] recv = manager.transmitAPDU(goodPin);
        assertTrue(cmpResponseCode(recv, Applet7Zip.SW_VERIFICATION_FAILED));
    }
    
    @Test
    public void loginAdminPinTriesNoBlock() throws Exception  {
        byte[] badPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_ADMIN , 0x00 , 0x00 ,
            0x10 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x01
        };
        byte[] goodPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_ADMIN , 0x00 , 0x00 ,
            0x10 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00
        };
        for(int i = 0 ; i < Applet7Zip.PIN_ADMIN_MAX_TRIES - 1 ; ++i)
            manager.transmitAPDU(badPin);
            
        byte[] recv = manager.transmitAPDU(goodPin);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
    }
    
    @Test
    public void setAdminPinNoLogin() throws Exception  {
        byte[] newPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_SET_PIN_ADMIN , 0x00 , 0x00 ,
            0x10 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x01
        };
        byte[] recv = manager.transmitAPDU(newPin);
        assertTrue(cmpResponseCode(recv, Applet7Zip.SW_PIN_VERIFICATION_REQUIRED));
    }
    
    @Test
    public void setAdminPinLogin() throws Exception  {
        byte[] oldPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_ADMIN , 0x00 , 0x00 ,
            0x10 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00
        };
        byte[] newPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_SET_PIN_ADMIN , 0x00 , 0x00 ,
            0x10 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x01
        };
        byte[] recv;
        recv = manager.transmitAPDU(oldPin);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
        recv = manager.transmitAPDU(newPin);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
        newPin[ISO7816.OFFSET_INS] = Applet7Zip.INS_LOGIN_ADMIN;
        recv = manager.transmitAPDU(newPin);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
    }
    
    @Test
    public void setAdminPinBadLength() throws Exception  {
        byte[] oldPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_ADMIN , 0x00 , 0x00 ,
            0x10 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00
        };
        byte[] invalidPin = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_SET_PIN_ADMIN , 0x00 , 0x00 ,
            0x11 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00 , 0x00
        };
        byte[] recv;
        recv = manager.transmitAPDU(oldPin);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
        recv = manager.transmitAPDU(invalidPin);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_DATA_INVALID));
    }
    
    @Test
    public void generateMasterKeyNoLogin() throws Exception  {
        byte[] badIns = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_GENERATE_MASTER_KEY , 0x00 , 0x00 ,
            0x00
        };
        byte[] recv = manager.transmitAPDU(badIns);
        assertTrue(cmpResponseCode(recv, Applet7Zip.SW_PIN_VERIFICATION_REQUIRED));
    }
    
    @Test
    public void generateMasterKeyLogin() throws Exception  {
        byte[] userLogin = {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_USER , 0x00 , 0x00 ,
            0x04 , 0x00 , 0x00 , 0x00 , 0x00
        };
        byte[] ins = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_GENERATE_MASTER_KEY , 0x00 , 0x00 ,
            0x00
        };
        manager.transmitAPDU(userLogin);
        byte[] recv = manager.transmitAPDU(ins);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
    }
    
    @Test
    public void generateMasterKeyCtrZero() throws Exception {
        byte[] userLogin = {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_USER , 0x00 , 0x00 ,
            0x04 , 0x00 , 0x00 , 0x00 , 0x00
        };
        byte[] deriveNewKey = {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_DERIVE_NEW_KEY , 0x00 , 0x00 ,
            0x00
        };
        byte[] generateKey = {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_GENERATE_MASTER_KEY , 0x00 , 0x00 ,
            0x00
        };
        byte[] retrieveCtr = {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_RETRIEVE_CURRENT_CTR , 0x00 , 0x00 ,
            0x00
        };
        byte[] expectedCtr = {0x00 , 0x00 , 0x00 , 0x00 , 0x00 , 0x00 , 0x00 , 0x00 , (byte) 0x90 , 0x00};
        byte[] recv;
        recv = manager.transmitAPDU(userLogin);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
        recv = manager.transmitAPDU(deriveNewKey);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
        recv = manager.transmitAPDU(deriveNewKey);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
        recv = manager.transmitAPDU(deriveNewKey);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
        recv = manager.transmitAPDU(generateKey);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
        recv = manager.transmitAPDU(retrieveCtr);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
        assertTrue(Arrays.areEqual(recv, expectedCtr));
    }
    
    @Test
    public void retrieveCurrentCtrNoLogin() throws Exception  {
        byte[] badIns = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_RETRIEVE_CURRENT_CTR , 0x00 , 0x00 ,
            0x00
        };
        byte[] recv = manager.transmitAPDU(badIns);
        assertTrue(cmpResponseCode(recv, Applet7Zip.SW_PIN_VERIFICATION_REQUIRED));
    }
    
    @Test
    public void retrieveCurrentCtrLogin() throws Exception  {
        byte[] userLogin = {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_USER , 0x00 , 0x00 ,
            0x04 , 0x00 , 0x00 , 0x00 , 0x00
        };
        byte[] ins = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_RETRIEVE_CURRENT_CTR , 0x00 , 0x00 ,
            0x00
        };
        manager.transmitAPDU(userLogin);
        byte[] recv = manager.transmitAPDU(ins);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
        assertEquals(recv.length, (int) Applet7Zip.SIZE_COUNTER_BYTE + 2);
    }
    
    @Test
    public void deriveNewKeyNoLogin() throws Exception  {
        byte[] badIns = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_DERIVE_NEW_KEY , 0x00 , 0x00 ,
            0x00
        };
        byte[] recv = manager.transmitAPDU(badIns);
        assertTrue(cmpResponseCode(recv, Applet7Zip.SW_PIN_VERIFICATION_REQUIRED));
    }
    
    @Test
    public void deriveNewKeyLogin() throws Exception  {
        byte[] userLogin = {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_USER , 0x00 , 0x00 ,
            0x04 , 0x00 , 0x00 , 0x00 , 0x00
        };
        byte[] ins = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_DERIVE_NEW_KEY , 0x00 , 0x00 ,
            0x00
        };
        manager.transmitAPDU(userLogin);
        byte[] recv = manager.transmitAPDU(ins);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
        assertEquals(recv.length, (int) Applet7Zip.SIZE_MASTER_KEY_BYTE + 2);
    }
    
    @Test
    public void deriveCtrKeyNoLogin() throws Exception  {
        byte[] badIns = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_DERIVE_CTR_KEY , 0x00 , 0x00 ,
            0x00
        };
        byte[] recv = manager.transmitAPDU(badIns);
        assertTrue(cmpResponseCode(recv, Applet7Zip.SW_PIN_VERIFICATION_REQUIRED));
    }
    
    @Test
    public void deriveCtrKeyLogin() throws Exception  {
        byte[] userLogin = {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_USER , 0x00 , 0x00 ,
            0x04 , 0x00 , 0x00 , 0x00 , 0x00
        };
        byte[] ins = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_DERIVE_CTR_KEY , 0x00 , 0x00 ,
            Applet7Zip.SIZE_COUNTER_BYTE , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x00
        };
        manager.transmitAPDU(userLogin);
        byte[] recv = manager.transmitAPDU(ins);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_NO_ERROR));
        assertEquals(recv.length, (int) Applet7Zip.SIZE_MASTER_KEY_BYTE + 2);
    }
    
    @Test
    public void deriveCtrKeyBadCtr() throws Exception  {
        byte[] userLogin = {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_USER , 0x00 , 0x00 ,
            0x04 , 0x00 , 0x00 , 0x00 , 0x00
        };
        byte[] insBadLength = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_DERIVE_CTR_KEY , 0x00 , 0x00 ,
            0x07 , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00
        };
        byte[] insCtrTooBig = {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_DERIVE_CTR_KEY , 0x00 , 0x00 ,
            Applet7Zip.SIZE_COUNTER_BYTE , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x7F
        };
        manager.transmitAPDU(userLogin);
        byte[] recv = manager.transmitAPDU(insBadLength);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_DATA_INVALID));
        recv = manager.transmitAPDU(insCtrTooBig);
        assertTrue(cmpResponseCode(recv, ISO7816.SW_DATA_INVALID));
    }
    
    @Test
    public void deriveCtrKeyIsValid() throws Exception {
        byte[] userLogin = {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_USER , 0x00 , 0x00 ,
            0x04 , 0x00 , 0x00 , 0x00 , 0x00
        };
        byte[] deriveNew = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_DERIVE_NEW_KEY , 0x00 , 0x00 ,
            0x00
        };
        byte[] deriveCtr = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_DERIVE_CTR_KEY , 0x00 , 0x00 ,
            Applet7Zip.SIZE_COUNTER_BYTE , 
            0x00 , 0x00 , 0x00 , 0x00 , 
            0x00 , 0x00 , 0x00 , 0x01
        };
        manager.transmitAPDU(userLogin);
        byte[] rval1 = manager.transmitAPDU(deriveNew);
        byte[] rval2 = manager.transmitAPDU(deriveCtr);
        assertTrue(Arrays.areEqual(rval1, rval2));
    }
    
    /**
     * Derives two new keys and then tries to access them with extracted counters.
     * Repeat this many times.
     * @throws Exception 
     */
    @Test
    public void derivedKeysTest() throws Exception {
        byte[] userLogin = {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_LOGIN_USER , 0x00 , 0x00 ,
            0x04 , 0x00 , 0x00 , 0x00 , 0x00
        };
        
        byte[] deriveNewKey = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_DERIVE_NEW_KEY , 0x00 , 0x00 ,
            0x00
        };
        
        byte[] deriveCtrKey = new byte[13];
        deriveCtrKey[ISO7816.OFFSET_CLA] = Applet7Zip.CLA_7ZIPAPPLET;
        deriveCtrKey[ISO7816.OFFSET_INS] = Applet7Zip.INS_DERIVE_CTR_KEY;
        deriveCtrKey[ISO7816.OFFSET_P1] = 0x00;
        deriveCtrKey[ISO7816.OFFSET_P2] = 0x00;
        deriveCtrKey[ISO7816.OFFSET_LC] = Applet7Zip.SIZE_COUNTER_BYTE;
        
        byte[] getCounter = 
        {
            Applet7Zip.CLA_7ZIPAPPLET , Applet7Zip.INS_RETRIEVE_CURRENT_CTR , 0x00 , 0x00 , 
            0x00
        };
        
        /* User login */
        byte[] recv = manager.transmitAPDU(userLogin);
        assertTrue(cmpResponseCode(recv , ISO7816.SW_NO_ERROR));
        
        byte[] firstKey;
        byte[] secondKey;
        byte[] counterKey;
        byte[] counter;
        
        for(int i = 0 ; i < 1000 ; ++i) {
            /* Get first key */
            firstKey = manager.transmitAPDU(deriveNewKey);
            /* Get counter of first key */
            counter = manager.transmitAPDU(getCounter);
            /* Get second key */
            secondKey = manager.transmitAPDU(deriveNewKey);
            /* Append counter of first key to deriveCrtKey */
            System.arraycopy(counter , 0 , deriveCtrKey , 
                             ISO7816.OFFSET_CDATA , Applet7Zip.SIZE_COUNTER_BYTE);
            /* Derive key from counter */
            counterKey = manager.transmitAPDU(deriveCtrKey);
            /* Compare first key to counter key */
            assertTrue(Arrays.areEqual(firstKey, counterKey));
            /* Check that first derivation succeeded */
            assertTrue(cmpResponseCode(firstKey, ISO7816.SW_NO_ERROR));
            /* Get counter of second key */
            counter = manager.transmitAPDU(getCounter);
            /* Append second counter to deriveCtrKey */
            System.arraycopy(counter , 0 , deriveCtrKey , 
                             ISO7816.OFFSET_CDATA , Applet7Zip.SIZE_COUNTER_BYTE);
            /* Derive key from counter */
            counterKey = manager.transmitAPDU(deriveCtrKey);
            /* Compare second key to counter key */
            assertTrue(Arrays.areEqual(secondKey, counterKey));
            /* Check that second derivation succeeded */
            assertTrue(cmpResponseCode(secondKey, ISO7816.SW_NO_ERROR));
        }
    }
    
    private boolean cmpResponseCode(byte[] buffer , int expectedResponse) {
        /* I said it once and I will say it again. I LOVE absence of unsigned byte in Java... */ 
        expectedResponse &= 0xFFFF;
        int response = (buffer[buffer.length - 2] & 0xFF);
        response <<= 8;
        response |= (buffer[buffer.length - 1] & 0xFF);
        
        return response == expectedResponse;
    }
}
