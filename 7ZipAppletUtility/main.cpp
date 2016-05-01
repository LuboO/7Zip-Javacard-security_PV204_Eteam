/*
 * Simple utility application that can be used for setting up the Javacard 
 * applet. Applet must be on real device and reader must be present in the 
 * system. Utility is able to change user and administrator passwords as well
 * as unblocking device with administrator password after too many unsuccesfull
 * login tries by user. Device that is blocked with administrator tries 
 * (127 tries, limitation of Javacard) can't be unblocked and applet must 
 * be reinstalled. In this case, master key on the device is lost and previously 
 * derived keys can't be recovered.
 */
/*
 * NOTE: Utility can be used with virtual reader and applet simulated by jCardSim, however
 * applet must be installed before use by this utility. For installation command see file
 * keyderivationdemo.apdu in folder JavacardSimulation.
 */

#include <iostream>
#include <windows.h>
#include "applet7zipmanager.h"

/* Enum for available operations */
enum class Operation {
	SET_USER_PIN_USER, SET_USER_PIN_ADMIN, SET_ADMIN_PIN, GENERATE_MASTER_KEY
};

std::vector<BYTE> readPinFromConsole(const std::string & msg);

std::vector<BYTE> readPinFromConsoleTwoTimes(const std::string & msg1 , 
	                                         const std::string & msg2);

void clearByteVector(std::vector<BYTE> & vec);

int main(void) {
	/* Getting operation from user */
	std::cout << "=== 7Zip Applet Utility (v0.9) ===" << std::endl;
	std::cout << std::endl;
	std::cout << "=== Select operation ===" << std::endl;
	std::cout << "    1. Set user pin (user)" << std::endl;
	std::cout << "    2. Set user pin (admin)" << std::endl;
	std::cout << "    3. Set admin pin (admin)" << std::endl;
	std::cout << "    4. Generate new master key (CAUTION: Old key will be lost)" << std::endl;
	std::cout << "Operation (1-4): ";
	std::string pickedOperation;
	std::cin >> pickedOperation;
	std::cout << std::endl;
	Operation op;

	try {
		switch (std::stoi(pickedOperation)) {
		case 1:op = Operation::SET_USER_PIN_USER;   break;
		case 2:op = Operation::SET_USER_PIN_ADMIN;  break;
		case 3:op = Operation::SET_ADMIN_PIN;       break;
		case 4:op = Operation::GENERATE_MASTER_KEY; break;
		default:
			std::cout << "[ERROR] Invalid operation entered." << std::endl;
			return 1;
		}
	}
	catch (std::invalid_argument ex) {
		std::cout << "[ERROR] Operation could not be parsed, " << ex.what() << std::endl;
		return 1;
	}
	catch (std::out_of_range ex) {
		std::cout << "[ERROR] Entered operation is out of range, " << ex.what() << std::endl;
		return 1;
	}

	/* Establishing context for this card communication */
	SCARDCONTEXT ctx = 0;
	LONG rval = 0;
	if ((rval = SCardEstablishContext(SCARD_SCOPE_SYSTEM, NULL, NULL, &ctx)) != SCARD_S_SUCCESS) {
		std::cout << "[ERROR] Problem when establishing context: " << rval << std::endl;
		return 1;
	}

	try {
		/* Creating manager for card communication */
		Applet7ZipManager manager = Applet7ZipManager::getInstance(ctx, /*true*/ false);

		/* Selecting reader - no readers present - error, 
		                      one present - automatic pick, 
							  multiple readers - let user pick one */
		auto readers = manager.getReaders();
		if (readers.empty()) {
			/* No readers found */
			std::cout << "=== No readers found. ===" << std::endl;
			return 0;
		}
		if (readers.size() == 1) {
			/* One reader found */
			std::cout << "=== Selected reader: " << readers[0] << " ===" << std::endl;
			manager.pickReader(readers[0]);
		}
		else {
			/* Multiple readers found */
			std::cout << "==== Select reader ====" << std::endl;
			for (size_t i = 1; i <= readers.size(); ++i) {
				std::cout << "    " << i << ". " << readers[i - 1] << std::endl;
			}
			std::cout << "Reader (1-" << readers.size() << "): ";
			std::string select;
			std::cin >> select;
			std::cout << std::endl;
			size_t s = 0;
			try {
				s = std::stoi(select);
			}
			catch (std::invalid_argument ex) {
				std::cout << "[ERROR] Reader number could not be parsed, " << ex.what() << std::endl;
				SCardReleaseContext(ctx);
				return 1;
			}
			catch (std::out_of_range ex) {
				std::cout << "[ERROR] Reader number is out of range, " << ex.what() << std::endl;
				SCardReleaseContext(ctx);
				return 1;
			}

			if (s > readers.size()) {
				std::cout << "[ERROR] Invalid reader number entered. " << std::endl;
				SCardReleaseContext(ctx);
				return 1;
			}
			else {
				manager.pickReader(readers[s - 1]);
				std::cout << "=== Selected reader: " << readers[s - 1] << " ===" << std::endl;
			}
		}

		/* Executing operation set at the beginning */
		switch (op) {
		case Operation::SET_USER_PIN_USER: {
			std::cout << std::endl << "=== Change of user PIN by user ===" << std::endl;
			std::vector<BYTE> oldPin = std::move(readPinFromConsole("Enter user PIN: "));
			if (oldPin.empty()) {
				std::cout << "[ERROR] No PIN entered." << std::endl;
				SCardReleaseContext(ctx);
				return 1;
			}
			std::vector<BYTE> newPin = std::move(readPinFromConsoleTwoTimes("New user PIN: ", "New user PIN again: "));
			if (newPin.empty()) {
				std::cout << "[ERROR] Different PINs or empty PIN entered." << std::endl;
				SCardReleaseContext(ctx);
				return 1;
			}
			manager.setUserPinUser(oldPin, newPin);
			clearByteVector(oldPin);
			clearByteVector(newPin);
			break;
		}
		case Operation::SET_USER_PIN_ADMIN: {
			std::cout << std::endl << "=== Change of user PIN by admin ===" << std::endl;
			std::vector<BYTE> adminPin = std::move(readPinFromConsole("Enter admin PIN: "));
			if (adminPin.empty()) {
				std::cout << "[ERROR] No PIN entered." << std::endl;
				SCardReleaseContext(ctx);
				return 1;
			}
			std::vector<BYTE> newPin = std::move(readPinFromConsoleTwoTimes("New user PIN: ", "New user PIN again: "));
			if (newPin.empty()) {
				std::cout << "[ERROR] Different PINs or empty PIN entered." << std::endl;
				SCardReleaseContext(ctx);
				return 1;
			}
			manager.setUserPinAdmin(adminPin, newPin);
			clearByteVector(adminPin);
			clearByteVector(newPin);
			break;
		}
		case Operation::SET_ADMIN_PIN: {
			std::cout << std::endl << "=== Change of admin PIN ===" << std::endl;
			std::vector<BYTE> oldPin = std::move(readPinFromConsole("Enter admin PIN: "));
			if (oldPin.empty()) {
				std::cout << "[ERROR] No PIN entered." << std::endl;
				SCardReleaseContext(ctx);
				return 1;
			}
			std::vector<BYTE> newPin = std::move(readPinFromConsoleTwoTimes("New admin PIN: ", "New admin PIN again: "));
			if (newPin.empty()) {
				std::cout << "[ERROR] Different PINs or empty PIN entered." << std::endl;
				SCardReleaseContext(ctx);
				return 1;
			}
			manager.setAdminPin(oldPin, newPin);
			clearByteVector(oldPin);
			clearByteVector(newPin);
			break;
		}
		case Operation::GENERATE_MASTER_KEY: {
			std::cout << std::endl << "=== Overwriting master key on the card ===" << std::endl;
			std::vector<BYTE> userPin = std::move(readPinFromConsole("Enter user PIN: "));
			if (userPin.empty()) {
				std::cout << "[ERROR] No PIN entered." << std::endl;
				SCardReleaseContext(ctx);
				return 1;
			}
			manager.generateMasterKey(userPin);
			clearByteVector(userPin);
			break;
		} 
		default: {
			std::cout << "[ERROR] Bad operation." << std::endl;
			SCardReleaseContext(ctx);
			return 1;
		}
		}

	}
	catch (CardException ex) {
		std::cout << "[ERROR] SmartCard: " << ex.what() << std::endl;
		SCardReleaseContext(ctx);
		return 1;
	}
	std::cout << std::endl << "=== Operation successful ===" << std::endl;
}

std::vector<BYTE> readPinFromConsole(const std::string & msg) {
	// Hide user input
	HANDLE hStdin = GetStdHandle(STD_INPUT_HANDLE);
	DWORD mode = 0;
	GetConsoleMode(hStdin, &mode);
	SetConsoleMode(hStdin, mode & (~ENABLE_ECHO_INPUT));

	std::string input;
	std::vector<BYTE> rval;
	std::cout << msg;
	std::cin >> input;
	std::cout << std::endl;
	for (int i = 0; i < input.size(); ++i) {
		rval.push_back(input[i]);
		input[i] = 0;
	}
	input.clear();

	// Enable console again
	SetConsoleMode(hStdin, mode);
	return rval;
}

std::vector<BYTE> readPinFromConsoleTwoTimes(const std::string & msg1, 
	                                         const std::string & msg2) {
	std::vector<BYTE> pin1 = std::move(readPinFromConsole(msg1));
	std::vector<BYTE> pin2 = std::move(readPinFromConsole(msg2));
	
	if (pin1.size() != pin2.size())
		return{};

	for (size_t i = 0; i < pin1.size(); ++i) {
		if (pin1[i] != pin2[i])
			return{};
	}
	clearByteVector(pin2);
	return pin1;
}

void clearByteVector(std::vector<BYTE>& vec) {
	for (size_t i = 0; i < vec.size(); ++i) {
		vec[i] = 0;
	}
	vec.clear();
}
