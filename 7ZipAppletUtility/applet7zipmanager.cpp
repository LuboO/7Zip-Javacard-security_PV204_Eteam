#include "applet7zipmanager.h"


const std::vector<BYTE> Applet7ZipManager::selectAppletCommand = {
	0x00, 0xa4, 0x00, 0x00,
	0x0A, 0x37, 0x5A, 0x69,
	0x70, 0x41, 0x70, 0x70,
	0x6C, 0x65, 0x74
};

Applet7ZipManager Applet7ZipManager::getInstance(const SCARDCONTEXT & ctx, bool debugInfo) {
	LONG rval = 0;
	if ((rval = SCardIsValidContext(ctx)) != SCARD_S_SUCCESS)
		throw CardException("SCardIsValidContext", rval);

	Applet7ZipManager mngr(ctx);
	mngr.debugInfo = debugInfo;

	return mngr;
}

Applet7ZipManager::~Applet7ZipManager() {
	if (hCard != 0)
		SCardDisconnect(hCard, SCARD_LEAVE_CARD);
}

std::vector<std::string> Applet7ZipManager::getReaders() const {
	LONG rval = 0;
	if ((rval = SCardIsValidContext(context)) != SCARD_S_SUCCESS)
		throw CardException("SCardIsValidContext", rval);

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

void Applet7ZipManager::pickReader(const std::string & readerName) {
	LONG rval = 0;
	if ((rval = SCardIsValidContext(context)) != SCARD_S_SUCCESS)
		throw CardException("SCardIsValidContext", rval);
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

	/* Temp code for testing */
	/*transmit({
		0x80, 0xb8, 0x00, 0x00,
		0x11, 0x0A, 0x37, 0x5A,
		0x69, 0x70, 0x41, 0x70,
		0x70, 0x6C, 0x65, 0x74,
		0x05, 0x00, 0x00, 0x02,
		0x0F, 0x0F
	});*/
	/* Selects 7Zip applet on the card */
	transmit(selectAppletCommand);
}
 
void Applet7ZipManager::setUserPinUser(const std::vector<BYTE>& oldPin, 
	                                   const std::vector<BYTE>& newPin) {
	auto loginUser = constructApdu(INS_LOGIN_USER, oldPin);
	auto setPin    = constructApdu(INS_SET_PIN_USER, newPin);
	transmit(loginUser);
	transmit(setPin);
}

void Applet7ZipManager::setUserPinAdmin(const std::vector<BYTE>& adminPin, 
	                                    const std::vector<BYTE>& newPin) {
	auto loginAdmin = constructApdu(INS_LOGIN_ADMIN, adminPin);
	auto setPin     = constructApdu(INS_SET_PIN_USER, newPin);
	transmit(loginAdmin);
	transmit(setPin);
}

void Applet7ZipManager::setAdminPin(const std::vector<BYTE>& oldPin, 
	                                const std::vector<BYTE>& newPin) {
	auto loginAdmin = constructApdu(INS_LOGIN_ADMIN, oldPin);
	auto setPin     = constructApdu(INS_SET_PIN_ADMIN, newPin);
	transmit(loginAdmin);
	transmit(setPin);
}

void Applet7ZipManager::generateMasterKey(const std::vector<BYTE>& userPin) {
	auto loginUser  = constructApdu(INS_LOGIN_USER, userPin);
	auto generateMK = constructApdu(INS_GENERATE_MASTER_KEY, {});
	transmit(loginUser);
	transmit(generateMK);
}

std::vector<BYTE> Applet7ZipManager::transmit(const std::vector<BYTE>& bufferSend) {
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

std::vector<BYTE> Applet7ZipManager::constructApdu(BYTE instruction, 
	                                               const std::vector<BYTE>& data) const {
	if (data.size() > APDU_PACKET_MAX_SIZE)
		throw CardException("supplied data are too big for communication");

	std::vector<BYTE> apdu(5, 0);
	apdu[0] = CLA_7ZIPAPPLET;
	apdu[1] = instruction;
	apdu[4] = data.size();
	for (BYTE i : data) {
		apdu.push_back(i);
	}
	return apdu;
}
