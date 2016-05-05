/*
 * PACKAGEID: 4C 61 62 61 6B
 * APPLETID: 4C 61 62 61 6B 41 70 70 6C 65 74
 */
package applets;

/*
 * Imported packages
 */
// specific import for Javacard API access
import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.*;

public class SimpleApplet extends javacard.framework.Applet
{
    // MAIN INSTRUCTION CLASS
    final static byte CLA_SIMPLEAPPLET                = (byte) 0xB0;

    // INSTRUCTIONS
    public final static byte INS_GENERATEKEY                = (byte) 0x51;
    public final static byte INS_ENCRYPTION_DEF             = (byte) 0x52;
    public final static byte INS_ENCRYPTION_OPT             = (byte) 0x53;
    public final static byte INS_MEASURE_TIME               = (byte) 0x54;
    
    // AES additional info, send in P1 and P2
    public final static byte MODE_ENC                       = (byte) 0x01;
    public final static byte MODE_DEC                       = (byte) 0x02;
    public final static byte PART_FINAL                     = (byte) 0x01;
    public final static byte PART_UPDATE                    = (byte) 0x02;
    public final static byte TIME_CPY_EEPROM_RAM            = (byte) 0x03;
    public final static byte TIME_ENC_EEPROM                = (byte) 0x04;
    public final static byte TIME_ENC_EEPROM_RAM            = (byte) 0x05;
    public final static byte TIME_ENC_RAM                   = (byte) 0x06;
    public final static byte TIME_NO_ACTION                 = (byte) 0x07;
    public final static byte TIME_SEND_BACK_APDU            = (byte) 0x08;
    public final static byte TIME_SEND_BACK_RAM             = (byte) 0x09;
    public final static byte TIME_CPY_RAM_EEPROM            = (byte) 0x0A;
    
    public final static short MAX_AES_BUFFER_LENGTH          = (short) 0xF0; // Closest to 255B
    public final static byte  AES_BLOCK_LENGTH               = (short) 0x10; // 16B
    public final static short AES_KEY_LENGHT                 = (short) 0x20; // 256b
    final static short ARRAY_LENGTH_RAM                      = (short) 0xF0; // Used only for encrypting 

    public final static short SW_CIPHER_DATA_LENGTH_BAD     = (short) 0x6710;
    
    private   RandomData     m_secureRandom = null;
    private   AESKey         m_aesKey = null;
    private   Cipher         m_encryptCipher = null;
    private   Cipher         m_decryptCipher = null;

    // TEMPORARRY ARRAY IN RAM
    private   byte        m_ramArray[] = null;

    /**
     * LabakApplet default constructor
     * Only this class's install method should create the applet object.
     */
    protected SimpleApplet(byte[] buffer, short offset, byte length)
    {
        if(length > 9) {

            // CREATE AES KEY OBJECT
            m_aesKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_256, false);
            // CREATE OBJECTS FOR CBC CIPHERING
            m_encryptCipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false);
            m_decryptCipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false);

            // CREATE RANDOM DATA GENERATOR
             m_secureRandom = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);

            // TEMPORARY BUFFER USED FOR FAST OPERATION WITH MEMORY LOCATED IN RAM
            m_ramArray = JCSystem.makeTransientByteArray(ARRAY_LENGTH_RAM, JCSystem.CLEAR_ON_DESELECT);
            Util.arrayFillNonAtomic(m_ramArray, (short) 0, ARRAY_LENGTH_RAM, (byte) 0);
            // SET KEY VALUE
            m_aesKey.setKey(m_ramArray, (short) 0);

            // INIT CIPHERS WITH NEW KEY
            m_encryptCipher.init(m_aesKey, Cipher.MODE_ENCRYPT);
            m_decryptCipher.init(m_aesKey, Cipher.MODE_DECRYPT);
        } else {
           // <IF NECESSARY, USE COMMENTS TO CHECK LENGTH >
           // if(length != <PUT YOUR PARAMETERS LENGTH> )
           //     ISOException.throwIt((short)(ISO7816.SW_WRONG_LENGTH + length));
       }

        // <PUT YOUR CREATION ACTION HERE>

        // register this instance
          register();
    }

    /**
     * Method installing the applet.
     * @param bArray the array containing installation parameters
     * @param bOffset the starting offset in bArray
     * @param bLength the length in bytes of the data parameter in bArray
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) throws ISOException
    {
        // applet  instance creation 
        new SimpleApplet (bArray, bOffset, bLength);
    }

    /**
     * Select method returns true if applet selection is supported.
     * @return boolean status of selection.
     */
    public boolean select()
    {   
        return true;
    }

    /**
     * Deselect method called by the system in the deselection process.
     */
    public void deselect()
    {
    }

    /**
     * Method processing an incoming APDU.
     * @see APDU
     * @param apdu the incoming APDU
     * @exception ISOException with the response bytes defined by ISO 7816-4
     */
    public void process(APDU apdu) throws ISOException
    {
        // get the APDU buffer
        byte[] apduBuffer = apdu.getBuffer();
        //short dataLen = apdu.setIncomingAndReceive();
        //Util.arrayCopyNonAtomic(apduBuffer, (short) 0, m_dataArray, m_apduLogOffset, (short) (5 + dataLen));
        //m_apduLogOffset = (short) (m_apduLogOffset + 5 + dataLen);

        // ignore the applet select command dispached to the process
        if (selectingApplet())
            return;

        // APDU instruction parser
        if (apduBuffer[ISO7816.OFFSET_CLA] == CLA_SIMPLEAPPLET) {
            switch ( apduBuffer[ISO7816.OFFSET_INS] )
            {
                case INS_GENERATEKEY:  GenerateKey(apdu); break;
                case INS_ENCRYPTION_DEF: DataEncryption1(apdu); break;
                case INS_ENCRYPTION_OPT: DataEncryption2(apdu); break;
                case INS_MEASURE_TIME: MeasureTimes(apdu); break;
                default :
                    // The INS code is not supported by the dispatcher
                    ISOException.throwIt( ISO7816.SW_INS_NOT_SUPPORTED ) ;
            }
        }
        else ISOException.throwIt( ISO7816.SW_CLA_NOT_SUPPORTED);
    }

    /**
     * Instruction to generate new AES key. Ciphers are initialized and key is
     * included in response.
     * @param apdu 
     */
    void GenerateKey(APDU apdu) {
        byte[]  apdubuf = apdu.getBuffer();
        apdu.setIncomingAndReceive();
        // Generate random data
        m_secureRandom.generateData(apdubuf, ISO7816.OFFSET_CDATA , AES_KEY_LENGHT);
        // SET KEY VALUE
        m_aesKey.setKey(apdubuf, ISO7816.OFFSET_CDATA);

        // INIT CIPHERS WITH NEW KEY
        m_encryptCipher.init(m_aesKey, Cipher.MODE_ENCRYPT);
        m_decryptCipher.init(m_aesKey, Cipher.MODE_DECRYPT);
        
        apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, AES_KEY_LENGHT);
    }
    
    /**
     * Default encryption used in example.
     * @param apdu 
     */
    void DataEncryption1(APDU apdu) {
        byte[] apdubuf = apdu.getBuffer();
        short  dataLen = apdu.setIncomingAndReceive();
        
        if((dataLen % AES_BLOCK_LENGTH) != 0)
            ISOException.throwIt(SW_CIPHER_DATA_LENGTH_BAD);
        
        Cipher cipher;
        switch(apdubuf[ISO7816.OFFSET_P1]) {
            case MODE_ENC:
                cipher = m_encryptCipher;
                break;
            case MODE_DEC:
                cipher = m_decryptCipher;
                break;
            default:
                ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
                return;
        }
        
        switch(apdubuf[ISO7816.OFFSET_P2]) {
            case PART_UPDATE:
                cipher.update(apdubuf, ISO7816.OFFSET_CDATA, dataLen, m_ramArray , (short) 0);
                break;
            case PART_FINAL:
                cipher.doFinal(apdubuf, ISO7816.OFFSET_CDATA, dataLen, m_ramArray , (short) 0);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
                return;
        }
        
        Util.arrayCopyNonAtomic(m_ramArray, (short) 0, apdubuf, ISO7816.OFFSET_CDATA, dataLen);
        apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, dataLen);
    }
    
    /**
     * Slightly improved encryption without useless operations.
     * @param apdu 
     */
    void DataEncryption2(APDU apdu) {
        byte[] apdubuf = apdu.getBuffer();
        short  dataLen = apdu.setIncomingAndReceive();
        
        if((dataLen % AES_BLOCK_LENGTH) != 0)
            ISOException.throwIt(SW_CIPHER_DATA_LENGTH_BAD);
        
        Cipher cipher;
        switch(apdubuf[ISO7816.OFFSET_P1]) {
            case MODE_ENC:
                cipher = m_encryptCipher;
                break;
            case MODE_DEC:
                cipher = m_decryptCipher;
                break;
            default:
                ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
                return;
        }
        
        switch(apdubuf[ISO7816.OFFSET_P2]) {
            case PART_UPDATE:
                cipher.update(apdubuf, ISO7816.OFFSET_CDATA, dataLen, apdubuf, ISO7816.OFFSET_CDATA);
                break;
            case PART_FINAL:
                cipher.doFinal(apdubuf, ISO7816.OFFSET_CDATA, dataLen, apdubuf, ISO7816.OFFSET_CDATA);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
                return;
        }
        
        apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, dataLen);
    }
    
    /**
     * Method used for testing of speed of single operations on the card.
     * To ensure relevant results, each apdu buffer yields 240B of data. 
     * Also, send back measurements sends back 240B of data. Data and encryption
     * keys are zeroes.
     * @param apdu 
     */
    void MeasureTimes(APDU apdu) {
        byte[] apdubuf = apdu.getBuffer();
        short  dataLen = apdu.setIncomingAndReceive();
        
        if((dataLen % AES_BLOCK_LENGTH) != 0)
            ISOException.throwIt(SW_CIPHER_DATA_LENGTH_BAD);
        
        switch(apdubuf[ISO7816.OFFSET_P1]) {
            case TIME_NO_ACTION:
                return;
            case TIME_SEND_BACK_APDU:
                apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, dataLen);
                return;
            case TIME_SEND_BACK_RAM:
                apdu.setOutgoing();
                apdu.setOutgoingLength(dataLen);
                apdu.sendBytesLong(m_ramArray, (short) 0, dataLen);
                return;
            case TIME_ENC_EEPROM:
                m_encryptCipher.doFinal(apdubuf, ISO7816.OFFSET_CDATA , dataLen, apdubuf, ISO7816.OFFSET_CDATA);
                return;
            case TIME_ENC_EEPROM_RAM:
                m_encryptCipher.doFinal(apdubuf, ISO7816.OFFSET_CDATA , dataLen, m_ramArray , (short) 0);
                return;
            case TIME_ENC_RAM:
                m_encryptCipher.doFinal(m_ramArray, (short) 0 , dataLen, m_ramArray, (short) 0);
                return;
            case TIME_CPY_EEPROM_RAM:
                Util.arrayCopyNonAtomic(apdubuf , ISO7816.OFFSET_CDATA , m_ramArray, (short) 0 , dataLen);
                return;
            case TIME_CPY_RAM_EEPROM:
                Util.arrayCopyNonAtomic(m_ramArray, (short) 0 , apdubuf , ISO7816.OFFSET_CDATA , dataLen);
                return;
            default:
                ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
       }
    }
}
