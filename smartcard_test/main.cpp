#include <iostream>

#include "SmartCardManager.h"

int main(int argc, char *argv[]) {

	try {
		SCARDCONTEXT ctx = 0;
		LONG rval = 0;
		/* Establishing context and passing it to the manager instance */
		if ((rval = SCardEstablishContext(SCARD_SCOPE_SYSTEM, NULL, NULL, &ctx)) != SCARD_S_SUCCESS)
			throw CardException("SCardEstablishContext", rval);

		SmartCardManager manager = SmartCardManager::getInstance(ctx , true);

		/* List smartcard readers present in computer */
		auto readers = manager.getReaders();
		std::cout << "Available smartcard readers:" << std::endl;
		for (auto reader : readers) {
			std::cout << reader << std::endl;
		}
		std::cout << std::endl;

		/* If there are any readers, pick first and start communication with inserted card */
		if (readers.size() > 0) {
			manager.pickReader(readers[0]);

			std::vector<BYTE> response;
			response = manager.transmit(
			{
				0x80, 0xb8, 0x00, 0x00,
				0x11, 0x0A, 0x37, 0x5A,
				0x69, 0x70, 0x41, 0x70,
				0x70, 0x6C, 0x65, 0x74,
				0x05, 0x00, 0x00, 0x02,
				0x0F, 0x0F
			});

			response = manager.transmit(
			{
				0x00, 0xa4, 0x00, 0x00,
				0x0A, 0x37, 0x5A, 0x69,
				0x70, 0x41, 0x70, 0x70,
				0x6C, 0x65, 0x74
			});

			response = manager.transmit(
			{
				0x13, 0x01, 0x00, 0x00,
				0x00
			});
		}
		else {
			std::cout << "No readers are present." << std::endl;
		}

		/* Releasing context */
		if ((rval = SCardReleaseContext(ctx)) != SCARD_S_SUCCESS)
			throw CardException("SCardReleaseContext", rval);
	}
	catch (CardException ex) {
		std::cout << "[ERROR] " << ex.what() << std::endl;
	}	
}
