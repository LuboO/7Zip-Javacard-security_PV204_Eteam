#include <winscard.h>
#include <stdexcept>
#include <string>
#include <iomanip>
#include <sstream>
#include <vector>
#include <iostream>

/* Custom exception with return code formatting */
class CardException : public std::runtime_error {
public:
	CardException(const std::string & message)
		: std::runtime_error(message) {
		this->message = message;
	}

	CardException(const std::string & message, int errorCode)
		: std::runtime_error(message) {
		std::stringstream ss;
		ss << message << ", error code 0x";
		ss << std::hex << std::setw(8) << std::setfill('0') << errorCode;
		this->message = ss.str();
	}

	CardException(const std::string & message, BYTE sw1, BYTE sw2)
		: std::runtime_error(message) {
		std::stringstream ss;
		ss << message << ", response bytes 0x";
		ss << std::hex << std::setw(2) << std::setfill('0') << (int)sw1;
		ss << std::hex << std::setw(2) << std::setfill('0') << (int)sw2;
		this->message = ss.str();
	}

	const char * what() {
		return message.c_str();
	}

private:
	std::string message;
};

/* Manager for connection to SmartCard.
Must be provided valid context, after
that can hold handle of card in picked
reader and communicate with the card. */
class Applet7ZipManager {
public:
	/**
	 *@brief Returns SmartCardManager instance.
	 *@param ctx Valid established smartcard context.
	 *@param debugInfo If set to true, bytes will be printed on screen during transmission.
	 *@return initialized instance
	 */
	static Applet7ZipManager getInstance(const SCARDCONTEXT & ctx, bool debugInfo);

	/**
	 *@brief Destructor, disconnects from card.
	 */
	~Applet7ZipManager();

	/**
	 *@brief Returns list of readers available in system.
	 */
	std::vector<std::string> getReaders() const;

	/**
	 *@brief Picks single reader for communication. Subsequent transmissions
	 *       will be directed to card in pcked reader.
	 *@param readerName name of the reader
	 */
	void pickReader(const std::string & readerName);

	/**
	 *@brief Logs in user and sets new pin. Fails on wrong pin value or wrong new pin length.
	 *@param oldPin current pin
	 *@param newPin pin to be set
	 */
	void setUserPinUser(const std::vector<BYTE> & oldPin, 
		                const std::vector<BYTE> & newPin);

	/**
	 *@brief Logs in admin and sets new pin of user. Fails on wrong pin value or wrong new pin length.
	 *@param adminPin admin
	 *@param newPin pin to be set
	 */
	void setUserPinAdmin(const std::vector<BYTE> & adminPin,
		                 const std::vector<BYTE> & newPin);

	/**
	 *@brief Logs in admin and sets new pin. Fails on wrong pin value or wrong new pin length.
	 *@param oldPin current pin
	 *@param newPin pin to be set
	 */
	void setAdminPin(const std::vector<BYTE> & oldPin, 
		             const std::vector<BYTE> & newPin);

	/**
	 *@brief Logs in user and generates new master key on device. Use with care. Fails on wrong pin value.
	 *@param userPin user pin
	 */
	void generateMasterKey(const std::vector<BYTE> & userPin);

private:
	const SCARDCONTEXT & context;
	SCARDHANDLE hCard = 0;
	DWORD cardProtocol;
	bool debugInfo = false;

	Applet7ZipManager(const SCARDCONTEXT & ctx) : context(ctx) {}

	static const int APDU_PACKET_MAX_SIZE = 256;

	/* Following command will select correct 7Zip applet on the device.
	Should you use applet with different AID, change it here. */
	static const std::vector<BYTE> selectAppletCommand;

	/* Class and  instruction bytes used by card */
	static const BYTE CLA_7ZIPAPPLET = 0x13;
	static const BYTE INS_LOGIN_USER = 0x70;
	static const BYTE INS_SET_PIN_USER = 0x71;
	static const BYTE INS_LOGIN_ADMIN = 0x72;
	static const BYTE INS_SET_PIN_ADMIN = 0x73;
	static const BYTE INS_GENERATE_MASTER_KEY = 0x74;

	/**
	 *@brief Sends given bytes to card and waits for response. Returns response bytes.
	 *       Throws exception if received bytes doesn't end with 0x9000.
	 *@param bufferSend bytes to be sent
	 *@return received bytes
	 */
	std::vector<BYTE> transmit(const std::vector<BYTE> & bufferSend) ;

	/*
	 *@brief Constructs apdu packet with given instruction and data.
	 *@param instruction instruction byte
	 *@param data length of data will be set to LC byte in packet and data will be appended
	 *@return constructed packet
	 */
	std::vector<BYTE> constructApdu(BYTE instruction , 
		                            const std::vector<BYTE> & data) const;
};