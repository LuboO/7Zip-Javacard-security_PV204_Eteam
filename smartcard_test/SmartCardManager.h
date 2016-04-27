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
class SmartCardManager {
public:
	static const int APDU_PACKET_MAX_SIZE = 256;

	/**
	 *@brief Returns SmartCardManager instance.
	 *@param ctx Valid established smartcard context.
	 *@param debugInfo If set to true, bytes will be printed on screen during transmission.
	 *@return initialized instance
	 */
	static SmartCardManager getInstance(const SCARDCONTEXT & ctx , bool debugInfo);

	/**
	 *@brief Destructor, disconnects from card.
	 */
	~SmartCardManager();

	/** 
	 *@brief Returns list of readers available in system.
	 */
	std::vector<std::string> getReaders() const;

	/**
	 *@brief Picks single reader for communication. Subsequent transmissions 
	 *       will be directed to card in pcked reader.
	 *@param readerName name of the reader
	 */
	void pickReader(std::string readerName);

	/**
	 *@brief Sends given bytes to card and waits for response. Returns response bytes. 
	 *       Throws exception if received bytes doesn't end with 0x9000.
	 *@param bufferSend bytes to be sent
	 *@return received bytes
	 */
	std::vector<BYTE> transmit(const std::vector<BYTE> & bufferSend) const;

	/* Following methods will be in final version */
	/* getDerivedKey */
	/* getDerivedKeyCounter */
	/* getCounter */

private:
	SmartCardManager(const SCARDCONTEXT & ctx) : context(ctx) {}

	const SCARDCONTEXT & context;
	SCARDHANDLE hCard = 0;
	DWORD cardProtocol;
	bool debugInfo = false;
};