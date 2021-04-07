#define MIFARE_1K 225

#define CARD_ARRIVED 200
#define COMMAND_SUCCESSFUL 201
#define COMMAND_FAILED 202

//application state
#define WAITING_FOR_CARD 100
#define WAITING_FOR_COMMAND 101
#define PROCESSING_COMMAND 102
#define FORGET_CARD 555
#define WRITE_DATA 666
#define READ_DATA 777

#include<SPI.h>
#include<MFRC522.h>
MFRC522 rfidModule(10,3);
MFRC522::MIFARE_Key key;
uint8_t applicationState;

void onCard(uint8_t cardType)
{
  Serial.print(CARD_ARRIVED);
  Serial.print(cardType);
  Serial.println();
  applicationState=WAITING_FOR_COMMAND;
  while(!Serial.available()) delay(100);
  int i;
  byte commandBytes[3];
  i=0;
  while(Serial.available() && i<=2)
  {
    commandBytes[i]=Serial.read();
    i++;
  }
  int command=((commandBytes[0]-48)*100);
  command+=((commandBytes[1]-48)*10);
  command+=((commandBytes[2]-48)*1);
  if(command==FORGET_CARD)
  {
    rfidModule.PICC_HaltA();
    applicationState=WAITING_FOR_COMMAND;
  }
  //code to read data starts here
  if(command==READ_DATA)
  {
    byte sector=1;
    byte blockAddr=4;
    byte dataBlock[16];
    byte trailerBlock=7;
    MFRC522::StatusCode status;
    byte buffer[18];
    byte size = sizeof(buffer);
    status=(MFRC522::StatusCode) rfidModule.PCD_Authenticate(MFRC522::PICC_CMD_MF_AUTH_KEY_A, trailerBlock, &key, &(rfidModule.uid));  
    if (status != MFRC522::STATUS_OK) 
    {
        rfidModule.PICC_HaltA();
        Serial.print(COMMAND_FAILED);
        applicationState=WAITING_FOR_COMMAND;
        return;
    }
    // Read data from the block
    status=(MFRC522::StatusCode) rfidModule.MIFARE_Read(blockAddr, buffer, &size);
    if (status != MFRC522::STATUS_OK) 
    {
        rfidModule.PICC_HaltA();
        rfidModule.PCD_StopCrypto1(); //important
        Serial.print(COMMAND_FAILED);
        applicationState=WAITING_FOR_COMMAND;
        return;
    }
    char bytesToSend[19];
    bytesToSend[0]=(byte)50; // 2
    bytesToSend[1]=(byte)48; // 0
    bytesToSend[2]=(byte)49; // 1
    for(int i=3;i<19;i++) bytesToSend[i]=buffer[i-3];
    Serial.print(bytesToSend);
    rfidModule.PICC_HaltA();
    rfidModule.PCD_StopCrypto1(); //important
    applicationState=WAITING_FOR_COMMAND;
    return;    
  }//code to read data ends here
  
  if(command==WRITE_DATA)
  {
    byte sector=1;
    byte blockAddr=4;
    byte dataBlock[16];
    byte trailerBlock=7;
    MFRC522::StatusCode status;
    byte buffer[18];
    byte size = sizeof(buffer);
    byte dataBytes[16];
    i=0;
    while(Serial.available() && i<16)
    {
      dataBytes[i]=Serial.read(); 
      i++;
    }
    if(i!=16)
    {
      rfidModule.PICC_HaltA();
      Serial.print(COMMAND_FAILED);
      applicationState=WAITING_FOR_COMMAND;
      return;
    }
    //code to write data to card starts here

    // Authenticate using key A
    status = (MFRC522::StatusCode) rfidModule.PCD_Authenticate(MFRC522::PICC_CMD_MF_AUTH_KEY_A, trailerBlock, &key, &(rfidModule.uid));
    if (status != MFRC522::STATUS_OK)
    {
        rfidModule.PICC_HaltA();
        Serial.print(COMMAND_FAILED);
        applicationState=WAITING_FOR_COMMAND;
        return;
    }
    /*status = (MFRC522::StatusCode) rfidModule.PCD_Authenticate(MFRC522::PICC_CMD_MF_AUTH_KEY_B, trailerBlock, &key, &(rfidModule.uid));
    if (status != MFRC522::STATUS_OK) 
    {
        rfidModule.PICC_HaltA();
        Serial.print(COMMAND_FAILED);
        applicationState=WAITING_FOR_COMMAND;
        return;
    }*/
    status=(MFRC522::StatusCode) rfidModule.MIFARE_Read(blockAddr, buffer, &size);
    if (status != MFRC522::STATUS_OK) 
    {
        rfidModule.PICC_HaltA();
        rfidModule.PCD_StopCrypto1(); //important
        Serial.print(COMMAND_FAILED);
        applicationState=WAITING_FOR_COMMAND;
        return;
    }
    char bytesToSend[19];
    bytesToSend[0]=(byte)50; // 2
    bytesToSend[1]=(byte)48; // 0
    bytesToSend[2]=(byte)49; // 1
    for(int i=3;i<19;i++) bytesToSend[i]=buffer[i-3];
    status = (MFRC522::StatusCode) rfidModule.PCD_Authenticate(MFRC522::PICC_CMD_MF_AUTH_KEY_A, trailerBlock, &key, &(rfidModule.uid));
    if (status != MFRC522::STATUS_OK)
    {
        rfidModule.PICC_HaltA();
        Serial.print(COMMAND_FAILED);
        applicationState=WAITING_FOR_COMMAND;
        return;
    }
    status = (MFRC522::StatusCode) rfidModule.MIFARE_Write(blockAddr, dataBytes, 16);
    if (status != MFRC522::STATUS_OK) 
    {
        rfidModule.PICC_HaltA();
        rfidModule.PCD_StopCrypto1();  //important
        Serial.print(COMMAND_FAILED);
        applicationState=WAITING_FOR_COMMAND;
        return;
    }
    Serial.print(bytesToSend);
    rfidModule.PICC_HaltA();
    rfidModule.PCD_StopCrypto1(); //important
    applicationState=WAITING_FOR_COMMAND;
    return;    
    //code to write data to card ends here 
  }
  
  // will have to be removed
  applicationState=WAITING_FOR_COMMAND;
}


void setup() {
  // put your setup code here, to run once:
Serial.begin(9600);
SPI.begin();
rfidModule.PCD_Init();
for(byte i=0;i<6;i++)
{
  key.keyByte[i]=0xFF;
}
applicationState=WAITING_FOR_CARD;
}

void loop() {
  // put your main code here, to run repeatedly:
if(rfidModule.PICC_IsNewCardPresent() && rfidModule.PICC_ReadCardSerial())
{
  MFRC522::PICC_Type cardType=rfidModule.PICC_GetType(rfidModule.uid.sak);
  if(cardType==MFRC522::PICC_TYPE_MIFARE_1K)
  {
    onCard(MIFARE_1K);
  }
  else
  {
    rfidModule.PICC_HaltA();
  }
}
}
