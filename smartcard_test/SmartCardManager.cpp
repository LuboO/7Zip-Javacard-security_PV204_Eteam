#include "SmartCardManager.h"

SmartCardManager SmartCardManager::getInstance(const SCARDCONTEXT & ctx , bool debugInfo) {
	LONG rval = 0;
	if ((rval = SCardIsValidContext(ctx)) != SCARD_S_SUCCESS)
		throw CardException("SCardIsValidContext", rval);

	SmartCardManager mngr(ctx);
	mngr.debugInfo = debugInfo;

	return mngr;
}

SmartCardManager::~SmartCardManager() {
	if(hCard != 0)
		SCardDisconnect(hCard, SCARD_LEAVE_CARD);
}

std::vector<std::string> SmartCardManager::getReaders() const {
	LONG rval = 0;
	if ((rval = SCardIsValidContext(context)) != SCARD_S_SUCCESS)
		throw CardException("SCardIsValidContext" , rval);

	LPTSTR pReaderList = NULL;
	LPTSTR pReaders = NULL;
	DWORD cch = SCARD_AUTOALLOCATE;

	rval = SCardListReaders(
		context,
		NULL,
		(LPTSTR)&pReaderList,
		&cch);
	if (rval != SCARD_S_SUCCESS && rval != SCARD_E_NO_READERS_AVAILABLE)
		throw CardException("SCardListReaders", rval);
	
	std::vector<std::string> readers;
	std::string reader;
	pReaders = pReaderList;

	/* Null if no readers are present */
	if (!pReaders) 
		return readers;

	/* Format of returned string: name1\0name2\0name3\0...\0nameN\0\0 */
	while (*pReaders != '\0') {
		reader.clear();
		while (*pReaders != '\0') {
			reader.push_back(*pReaders);
			++pReaders;
		}
		readers.push_back(reader);
		++pReaders;
	}
	if ((rval = SCardFreeMemory(context, pReaderList)) != SCARD_S_SUCCESS)
		throw CardException("SCardFreeMemory", rval);

	return readers;
}

void SmartCardManager::pickReader(std::string readerName) {
	LONG rval = 0;
	if((rval = SCardIsValidContext(context)) != SCARD_S_SUCCESS)
		throw CardException("SCardIsValidContext" , rval);
	/* Name must be converted into wstring */
	std::wstring rname(readerName.begin(), readerName.end());

	rval = SCardConnect(
		context,
		(LPCWSTR)rname.c_str(),
		SCARD_SHARE_SHARED,
		SCARD_PROTOCOL_T0 | SCARD_PROTOCOL_T1,
		&hCard,
		&cardProtocol);
	if (rval != SCARD_S_SUCCESS)
		throw CardException("SCardConnect", rval);
}

std::vector<BYTE> SmartCardManager::transmit(const std::vector<BYTE>& bufferSend) const {
	if (hCard == 0)
		throw CardException("reader was not picked");

	if (debugInfo) {
		std::cout << "Transmission start" << std::endl;
		std::cout << ">>>>" << std::endl;
		for (BYTE b : bufferSend)
			std::cout << std::hex << std::setw(2) << std::setfill('0') << (int)b << " ";

		std::cout << std::endl << ">>>>" << std::endl;
	}

	/* Pick right protocol for communication */
	const SCARD_IO_REQUEST * protocol;
	switch (cardProtocol) {
	case SCARD_PROTOCOL_T0:
		protocol = SCARD_PCI_T0;
		break;
	case SCARD_PROTOCOL_T1:
		protocol = SCARD_PCI_T1;
		break;
	default:
		throw CardException("unknown card protocol");
	}

	LONG rval = 0;
	DWORD bufferReceiveSize = APDU_PACKET_MAX_SIZE;
	std::vector<BYTE> bufferReceive(bufferReceiveSize, 0);

	rval = SCardTransmit(
		hCard,
		protocol,
		&bufferSend[0],
		bufferSend.size(),
		NULL,
		&bufferReceive[0],
		&bufferReceiveSize);
	if (rval != SCARD_S_SUCCESS)
		throw CardException("SCardTransmmit", rval);

	/* Check last two bytes of response */
	if (bufferReceive[bufferReceiveSize - 2] != 0x90
		|| bufferReceive[bufferReceiveSize - 1] != 00)
		throw CardException(
			"bad response", 
			bufferReceive[bufferReceiveSize - 2],
			bufferReceive[bufferReceiveSize - 1]);

	if (debugInfo) {
		std::cout << "<<<<" << std::endl;
		for (size_t i = 0; i < bufferReceiveSize; ++i)
			std::cout << std::hex << std::setw(2) << std::setfill('0') << (int)bufferReceive[i] << " ";
		
		std::cout << std::endl;
		std::cout << "<<<<" << std::endl;
		std::cout << "Transmission end" << std::endl;
	}

	return bufferReceive;
}
