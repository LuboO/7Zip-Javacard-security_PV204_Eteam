# 7Zip Javacard security PV204 Eteam

## Topic
Extended 7Zip application that implements derivation of archive passwords from security token. Master key is stored in Javacard applet on Smartcard. When encrypting file, new key is derived based on counter in card and password is created from key. Counter is stored in filename. Decryption is reverse process, counter is taken from file name, passed to card and password is derived again.

Project was created as assignment for course PV204 Security Technologies, Faculty of Informatics, Masaryk University, Brno, Czech Republic.

## Authors
* Bau Pericón, Agustí, _agustibau@gmail.com_
* Obrátil, Ľubomír, _lubomir.obratil@gmail.com_
* Mmokwa, Aobakwe Alloycius, _moral support_

## Dependencies
Project is 7-Zip extension so it inherits any dependencies 7-Zip might have. Extended code is written in C++11 standard. Code for smartcard communication uses WinSCard.lib (Microsoft SDK library for PC/PS communication).

## Building
In folder _7zip_15-14\CPP\7zip\UI\GUI_ is configured solution file for Microsoft Visual Studio 2015, load it into IDE and build it. Take resulting binary and replace it for binary 7zG.exe in your 7-Zip installation directory. Alternatively you can use built binaries in folder _bin_

## Project structure
* **7zip_15-14**  
  Extended 7-Zip code
* **7ZipAppletUtility**  
  Console utility for changing default user and admin passwords on applet as well as generating new master key.
* **docs**  
  Presentations, reports and such...
* **Javacard**
  Javacard Applet related files and projects.
* **bin**
  Folder with build 7-Zip sources.
