
#include "StdAfx.h"
#include "SmartCardManager.h"

const std::vector<BYTE> SmartCardManager::selectAppletCommand = {
	0x00, 0xa4, 0x00, 0x00,
	0x0A, 0x37, 0x5A, 0x69,
	0x70, 0x41, 0x70, 0x70,
	0x6C, 0x65, 0x74
};

const std::wstring SmartCardManager::CTR_SEPAR = L"#!";

SmartCardManager SmartCardManager::getInstance(const SCARDCONTEXT & ctx) {
	LONG rval = 0;
	if ((rval = SCardIsValidContext(ctx)) != SCARD_S_SUCCESS)
		throw CardException("Manager initialized with invalid context.");

	SmartCardManager mngr(ctx);

	return mngr;
}

SmartCardManager::~SmartCardManager() {
	if(hCard != 0)
		SCardDisconnect(hCard, SCARD_LEAVE_CARD);
	hCard = 0;
}

std::vector<UString> SmartCardManager::getReaders() const {
	LONG rval = 0;
	if ((rval = SCardIsValidContext(context)) != SCARD_S_SUCCESS)
		throw CardException("Card context is no longer valid!");

	LPTSTR pReaderList = NULL;
	LPTSTR pReaders = NULL;
	DWORD cch = SCARD_AUTOALLOCATE;

	rval = SCardListReaders(
		context,
		NULL,
		(LPTSTR)&pReaderList,
		&cch);
	if (rval != SCARD_S_SUCCESS && rval != SCARD_E_NO_READERS_AVAILABLE)
		throw CardException("SCardListReaders failed", rval);
	
	std::vector<UString> readers;
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
		readers.push_back(ConvertUtils::cvrtStrToUni(reader));
		++pReaders;
	}
	if ((rval = SCardFreeMemory(context, pReaderList)) != SCARD_S_SUCCESS)
		throw CardException("SCardFreeMemory failed", rval);

	return readers;
}

void SmartCardManager::pickReader(const UString & readerName) {
	LONG rval = 0;
	if((rval = SCardIsValidContext(context)) != SCARD_S_SUCCESS)
		throw CardException("Card context is no longer valid.");

	rval = SCardConnect(
		context,
		ConvertUtils::cvrtUniToStr(readerName).c_str(), // This convert... :D
		SCARD_SHARE_SHARED,
		SCARD_PROTOCOL_T0 | SCARD_PROTOCOL_T1,
		&hCard,
		&cardProtocol);
	if (rval != SCARD_S_SUCCESS) {
		if (rval == SCARD_E_UNKNOWN_READER)
			throw CardException("Invalid reader name entered.");
		else
			throw CardException("SCardConnect failed", rval);
	}

  /* Only for testing */
  /*rval = getRetCode(transmit({
  0x80, 0xb8, 0x00, 0x00,
  0x11, 0x0A, 0x37, 0x5A,
  0x69, 0x70, 0x41, 0x70,
  0x70, 0x6C, 0x65, 0x74,
  0x05, 0x00, 0x00, 0x02,
  0x0F, 0x0F
  }));

  if (rval != SW_NO_ERROR)
    throw CardException("wtf", rval);*/

	/* Selects 7Zip applet on the card */
	if ((rval = getRetCode(transmit(selectAppletCommand))) != SW_NO_ERROR) {
		if (rval == SW_INCORRECT_P1P2) {
			throw CardException("Applet isn't installed or can't be selected on device.");
		}
		else {
			throw CardException("Invalid card response", rval);
		}
	}
}

void SmartCardManager::loginUser(const UString & userPassword) const {
	auto loginUser = constructApdu(INS_LOGIN_USER, ConvertUtils::cvrtUniToByteArr(userPassword));
	int  retCode   = 0;
	if ((retCode = getRetCode(transmit(loginUser))) != SW_NO_ERROR) {
		if (retCode == SW_VERIFICATION_FAILED) 
			throw CardException("Incorrect user PIN entered.");
		throw CardException("Invalid card response", retCode);
	}
}

UString SmartCardManager::getNewKey() const {
	auto getKeyIns  = constructApdu(INS_DERIVE_NEW_KEY, {});
	auto derivedKey = transmit(getKeyIns);
	int  retCode    = 0;
	if ((retCode = getRetCode(derivedKey)) != SW_NO_ERROR) {
		if (retCode == SW_PIN_VERIFICATION_REQUIRED)
			throw CardException("Card operation requires user authorization.");
		throw CardException("Invalid card response", retCode);
	}
  /* Key must have 32B + 2B response */
  if (derivedKey.size() != 34)
    throw CardException("Data received from card have invalid length.");
	/* Convert received binary key to Base64 (so it can be used as password). 
	   Last two bytes are response bytes and ignored. */
	return ConvertUtils::encodeBase64(&derivedKey[0], derivedKey.size() - 2);
}

uint64_t SmartCardManager::getCardCounter() const {
	auto getCounter = constructApdu(INS_RETRIEVE_CURRENT_CTR, {});
	auto counter    = transmit(getCounter);
	int  retCode    = 0;
	if ((retCode = getRetCode(counter)) != SW_NO_ERROR) {
		if (retCode == SW_PIN_VERIFICATION_REQUIRED) 
			throw CardException("Card operation requires user authorization.");
		throw CardException("Invalid card response", retCode);
	}
  /* Counter must have 8B + 2B response */
  if (counter.size() != 10)
    throw CardException("Data received from card have invalid length.");

  /* Deleting response bytes */
  counter.pop_back();counter.pop_back(); 
  return ConvertUtils::bytesToUInt64(counter);
}

UString SmartCardManager::getCtrKey(const uint64_t & counter) const {
  auto ctr        = ConvertUtils::uInt64ToBytes(counter);
	auto getKeyIns  = constructApdu(INS_DERIVE_CTR_KEY, ctr);
	auto derivedKey = transmit(getKeyIns);
	int  retCode    = 0;
	if ((retCode = getRetCode(derivedKey)) != SW_NO_ERROR) {
    if (retCode == SW_PIN_VERIFICATION_REQUIRED)
      throw CardException("Card operation requires user authorization.");
    else if (retCode == SW_DATA_INVALID)
      throw CardException("Counter was not yet used for key generation.");
		throw CardException("Invalid card response", retCode);
	}
  /* Key must have 32B + 2B response */
  if(derivedKey.size() != 34)
    throw CardException("Data received from card have invalid length.");
	/* Convert binary key to Base64, last two bytes are response bytes and are not included */
	return ConvertUtils::encodeBase64(&derivedKey[0], derivedKey.size() - 2);
}

void SmartCardManager::insertCounterToArcName(UString & arcName, const uint64_t & counter) {
  std::wstring fname(arcName);
  std::string ctr   = std::to_string(counter);
  size_t lastSeparPos = fname.find_last_of(L"/\\");
  size_t lastDotPos   = fname.find_last_of(L".");
  UString toInsert;
  toInsert += CTR_SEPAR.c_str();
  toInsert.AddAscii(ctr.c_str());

  size_t insertAt;
  if (lastDotPos == std::string::npos)        insertAt = arcName.Len();
  else if (lastSeparPos == std::string::npos) insertAt = lastDotPos;
  else if (lastDotPos > lastSeparPos)         insertAt = lastDotPos;
  else                                        insertAt = arcName.Len();

  arcName.Insert(insertAt, toInsert);
}

uint64_t SmartCardManager::extractCounterFromArcName(const UString & arcName) {
  std::wstring fname(arcName);
  size_t ctrSeparPos = fname.rfind(CTR_SEPAR);
  size_t lastDotPos  = fname.find_last_of(L".");
  if (ctrSeparPos == std::string::npos)
    throw CardException("Can't find counter in archive name.");

  if (lastDotPos == std::string::npos)
    lastDotPos = fname.length();
  else if (lastDotPos <= ctrSeparPos)
    lastDotPos = fname.length();
  
  ctrSeparPos += CTR_SEPAR.length();
  fname = fname.substr(ctrSeparPos, lastDotPos - ctrSeparPos);

  uint64_t counter;
  try {
    counter = std::stoull(fname);
  }
  catch (std::invalid_argument ex) {
    throw CardException("Counter in filename can't be parsed.");
  }
  catch (std::out_of_range ex) {
    throw CardException("Counter in filename is out of range of 64 bits.");
  }
  return counter;
}

std::vector<BYTE> SmartCardManager::transmit(const std::vector<BYTE>& bufferSend) const {
	if (hCard == 0)
		throw CardException("Card reader was not selected!");

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

	bufferReceive.resize(bufferReceiveSize);
	return bufferReceive;
}

std::vector<BYTE> SmartCardManager::constructApdu(BYTE instruction,
	                                              const std::vector<BYTE>& data) const {
	if (data.size() > APDU_PACKET_MAX_SIZE)
		throw CardException("Maximum size of APDU buffer exceeded!");

	std::vector<BYTE> apdu(5, 0);
	apdu[0] = CLA_7ZIPAPPLET;
	apdu[1] = instruction;
	apdu[4] = data.size();
	for (BYTE i : data) {
		apdu.push_back(i);
	}
	return apdu;
}

int SmartCardManager::getRetCode(const std::vector<BYTE> & response) const {
	int rval = 0;
	if (response.size() < 2)
		throw CardException("Can't read card response!");
	
	rval =  (int) response.at(response.size() - 2) << 8;
	rval |= (int) response.at(response.size() - 1);
	return rval;
}
