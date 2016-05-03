// SmartCardManager.h
/*
 * Code made by LuboO (lubomir.obratil@gmail.com)
 * Use it as you please but I guarantee nothing.
 */

#include <winscard.h>
#include <stdexcept>
#include <string>
#include <iomanip>
#include <sstream>
#include <vector>
#include <iostream>
#include "../../Common/MyString.h"
#include "../Common/ConvertUtils.h"

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
class SmartCardManager {
public:
	/**
	 *@brief Returns SmartCardManager instance.
	 *@param ctx Valid established smartcard context.
	 *@param debugInfo If set to true, bytes will be printed on screen during transmission.
	 *@return initialized instance
	 */
	static SmartCardManager getInstance(const SCARDCONTEXT & ctx/* , bool debugInfo*/);

	/**
	 *@brief Destructor, disconnects from card.
	 */
	~SmartCardManager();

	/** 
	 *@brief Returns list of readers available in system.
	 */
	std::vector<UString> getReaders() const;

	/**
	 *@brief Picks single reader for communication. Subsequent transmissions 
	 *       will be directed to card in pcked reader.
	 *@param readerName name of the reader
	 */
	void pickReader(const UString & readerName);

	void loginUser(const UString & userPassword) const;

	UString getNewKey() const;

	std::vector<BYTE> getCardCounter() const;

	UString getCtrKey(const std::vector<BYTE> & counter) const;

private:
	const SCARDCONTEXT & context;
	SCARDHANDLE hCard = 0;
	DWORD cardProtocol;
	//bool debugInfo = false;

	static const int APDU_PACKET_MAX_SIZE = 256;

	/* Following command will select correct 7Zip applet on the device.
	   Should you use applet with different AID, change it here. */
	static const std::vector<BYTE> selectAppletCommand;

	/* Class and  instruction bytes used by card */
	static const BYTE CLA_7ZIPAPPLET = 0x13;
	static const BYTE INS_LOGIN_USER = 0x70;
	static const BYTE INS_RETRIEVE_CURRENT_CTR = 0x75;
	static const BYTE INS_DERIVE_NEW_KEY = 0x76;
	static const BYTE INS_DERIVE_CTR_KEY = 0x77;

	/* Return codes used by card */
	static const int SW_NO_ERROR = 0x9000;
	static const int SW_INCORRECT_P1P2 = 0x6A86;
	static const int SW_VERIFICATION_FAILED = 0x6300;
	static const int SW_PIN_VERIFICATION_REQUIRED = 0x6301;

	SmartCardManager(const SCARDCONTEXT & ctx) : context(ctx) {}
	/**
	 *@brief Sends given bytes to card and waits for response. Returns response bytes.
	 *       Throws exception if received bytes doesn't end with 0x9000.
	 *@param bufferSend bytes to be sent
	 *@return received bytes
	 */
	std::vector<BYTE> transmit(const std::vector<BYTE> & bufferSend) const;

	/**
	 *@brief Constructs apdu packet with given instruction and data.
	 *@param instruction instruction byte
	 *@param data length of data will be set to LC byte in packet and data will be appended
	 *@return constructed packet
	 */
	std::vector<BYTE> constructApdu(BYTE instruction,
		                            const std::vector<BYTE> & data) const;

	int getRetCode(const std::vector<BYTE> & response) const;
};