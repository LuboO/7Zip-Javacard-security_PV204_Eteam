:: This .bat file will first remove reader driver then install it again.
:: It is necessary, driver is sometimes stuck and has to be reinstalled.
:: Optionally it will remove driver at the end again.
:: IMPORTANT: Script must be executed from shell with elevated rights (run as administrator)
:: When you wish to restart card and reader just terminate script with Ctrl-C and start it again
:: If you need to just remove VirtualReader run .\devcon.exe remove *BixVirtual*

:: Remove driver
cd .\BixVReader\Win7_x64\
.\devcon.exe remove *BixVirtual*

:: Install driver
.\devcon.exe install BixVReader.inf root\BixVirtualReader

:: Run virtual reader with simulated card from jCardSim
cd ../..
java -cp AppletDevel.jar;jcardsim-2.2.2-all.jar com.licel.jcardsim.remote.BixVReaderCard 7zipapplet.cfg

:: Remove virtual reader again
cd .\BixVReader\Win7_x64\
.\devcon.exe remove *BixVirtual*

:: Return to starting directory
cd ../..