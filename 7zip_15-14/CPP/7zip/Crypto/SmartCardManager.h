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
	 *@return initialized instance
	 */
	static SmartCardManager getInstance(const SCARDCONTEXT & ctx);

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

  /**
   *@brief Logs in user to card.
   *@userPassword user pin for card
   */
	void loginUser(const UString & userPassword) const;

  /**
   *@brief Requests new key from card. Available only after login. 
   *       Counter is updated in card after this request.
   *@return 256 bit key encoded in base64
   */
	UString getNewKey() const;

  /**
   *@brief Current counter on the card is requested.
   *@return Counter encoded as 64 bit integer (counter on card has 8 bytes too)
   */
	uint64_t getCardCounter() const;

  /**
   *@brief Requests key derivation with specific counter.
   *@param counter Counter with which key should be derived
   *@return Base64 encoded 256 bit key
   */
	UString getCtrKey(const uint64_t & counter) const;

  /**
   *@brief Inserts counter in uint64 and #! just before suffix in
   *       archive name. If there is no suffix, counter is appended
   *@param arcName archive name - will be modified
   *@param counter counter in base64 encoding
   */
  static void insertCounterToArcName(UString & arcName, const uint64_t & counter);

  /**
   *@brief Extracts counter after #! from filename. 
   *       Filename can contain suffix, it is ignored in extraction.
   *       Exceptions are thrown on failed extraction or bad counter format.
   *@param arcName archive name
   *@return counter
   */
  static uint64_t extractCounterFromArcName(const UString & arcName);

private:
	const SCARDCONTEXT & context;
	SCARDHANDLE hCard = 0;
	DWORD cardProtocol;

	static const int APDU_PACKET_MAX_SIZE = 256;
  /* Characters used to separate filename and counter */
  static const std::wstring CTR_SEPAR;

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
  static const int SW_DATA_INVALID = 0x6984;

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

  /**
   *@brief Converts last two bytes from response into integer.
   *@param response Response from smartcard.
   */
	int getRetCode(const std::vector<BYTE> & response) const;
};