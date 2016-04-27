Setup is inspired by articles:
Test signing of driver:
https://msdn.microsoft.com/en-us/library/windows/hardware/dn741535(v=vs.85).aspx
Accessing Javacard simulator via PC/SC:
https://jcardsim.org/blogs/work-jcardsim-through-pcsc-virtual-reader

Following steps describe how to simulate Javacard applet and access it 
through PC/SC specification. For PC/SC access to device can be used
for example Winscard.dll by Microsoft.

1. Installation of BixVReader
    1.1 You already have source code of the driver in subfolder BixVReader. Important
    driver files are in BixVReader/Win7_x64. Do not move these.
    
    1.2 In order to install the driver you will need DevCon. It is distributed
    in WDK 8 and can be downloaded from http://go.microsoft.com/fwlink/p/?LinkID=324284
    
    1.3 After succesfull installation you need to sign the driver 
    (installation of unsigned drivers is not possible from Windows 7 on). 
    I already signed BixVReader in this repo, so you don't have to 
    repeat the whole procedure again. You only have to add pv204eteam certificate
    as trusted into your computer.
    
    1.4 Run elevated command prompt (Right click, Run as administrator..) 
    and run following command
        
        bcdedit  /set  testsigning  on
        
    1.5 Restart the computer. After startup, in bottom right corner should
    be written Test Mode or something like that.
    
    1.6 Locate CertMgr.exe on your computer. I found it in 
    C:\Program Files (x86)\Windows Kits\8.1\bin\x64
    
    1.7 Add certificate pv204eteam.cer into Trusted Root CAs and trusted 
    publishers on your computer. Copy CertMgr.exe into same folder as 
    pv204eteam.cer and run commands (still elevated shell):
    
        CertMgr.exe /add CertificateFileName.cer /s /r localMachine root
        CertMgr.exe /add CertificateFileName.cer /s /r localMachine trustedpublisher
        
    1.8 Now, BixVReader should be ready to install. Locate file devenv.exe
    you installed before in WDK 8. Should be in following or similar folder
    C:\Program Files\Windows Kits\8.0\Tools\x64\
    IMPORTANT: Always take x64 versions of files
    Also locate files starting WUDFUpdate_* located in
    C:\Program Files\Windows Kits\8.0\Redist\wdf\x64
    Copy all files into folder BixVReader/Win7_x64.
    
    1.9 Run following command in order to install BixVReader:
    
        ./devcon.exe install BixVReader.inf root\BixVirtualReader
        
    1.10 After successful installation move on to step 2.
    
2. Simulation of javacard
    2.1 You need to have installed Java on your computer and added java executable
    in path.
    
    2.2 Go to folder JavacardSimulation
    
    2.3 There is prepared applet on which you can test your installation. AppletTester.jar
    Also there is netbeans project AppletTester in which you can modify the applet.
    After modification just replace the built .jar file in JavacardSimulation folder.
    
    2.4 To run simulator execute this command:
    
        java -cp AppletTester.jar;jcardsim-2.2.2-all.jar com.licel.jcardsim.utils.APDUScriptTool 7zipapplet.cfg saysomething.apdu
        
    2.5 Command will send packets from saysomething.apdu to applet configured in 
    file 7zipapplet.cfg. For more info about this read 
    https://jcardsim.org/docs/quick-start-guide-using-in-cli-mode
    
    Expected output should be this:
    
    CLA: 80, INS: b8, P1: 00, P2: 00, Lc: 11, 0a, 37, 5a, 69, 70, 41, 70, 70, 6c, 65, 74, 05, 00, 00, 02, 0f, 0f, Le: 0a, 37, 5a, 69, 70, 41, 70, 70, 6c, 65, 74, SW1: 90, SW2: 00
    CLA: 00, INS: a4, P1: 00, P2: 00, Lc: 0a, 37, 5a, 69, 70, 41, 70, 70, 6c, 65, 74, Le: 00, SW1: 90, SW2: 00
    CLA: 13, INS: 01, P1: 00, P2: 00, Lc: 00, Le: 3b, 48, 69, 2c, 20, 49, 20, 61, 6d, 20, 37, 2d, 5a, 69, 70, 20, 61, 70, 70, 6c, 65, 74, 2c, 20, 66, 65, 65, 6c, 20, 66, 72, 65, 65, 20, 74, 6f, 20, 74, 65, 73, 74, 20, 79, 6f, 75, 72, 20, 73, 6b, 69, 6c, 6c, 73, 20, 6f, 6e, 20, 6d, 65, 2e, SW1: 90, SW2: 00
    
    Comments on apdu packets are in file saysomething.apdu
    
    2.6 Now that you have correct configuraiton of driver and simulator, 
    you can run it together to simulate real card in your system.
    for this go into elevated shell and run command 
    
        java -cp AppletTester.jar;jcardsim-2.2.2-all.jar com.licel.jcardsim.remote.BixVReaderCard 7zipapplet.cfg
        
    Command will run in shell, do not close the shell session. If you did everything right, 
    you should see Unknown smartcard in your system. You can remove it by 
    entering Ctrl-C in the shell.
    
    2.7 Often BixVReader refuse to cooperate and must be reinstalled 
    in order to work again. In general, after simulating card and terminating,
    you will have to reinstall it. For this, go to folder BixVReader/Win7_x64
    and use commands:
        
        .\devcon.exe remove *BixVirtual*
        .\devcon.exe install BixVReader.inf root\BixVirtualReader
        
    For convenience, I wrote batch script that will remove (if exists) 
    BixVReader, install it and run Javacard simulator in it. After termination
    it will again remove BixVReader. It is located in virtualreaderstart.bat
    Run it in elevated shell session for best results :)
    
3. Access Javacard through PC/SC
    3.1 I created sample project that can access simulated card in the system.
    Project is in VisualStudio 2015, and folder smartcard_test. Just open
    solution, build it and run it when smartcard is being emulated. It should
    list readers in your system (typically one Bix VIRTUAL_CARD_READER 0) 
    pick first one and do some communication with applet. Project uses
    WinSCard.dll for accessing PC/SC. More complete version of class can
    be used in final project for card transmissions. I tried to be straightforward
    with the code, in case you have any questions just tell me so.
    