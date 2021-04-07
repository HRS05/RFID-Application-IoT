import com.fazecast.jSerialComm.*;
import java.util.*;
import com.google.gson.*;
import java.io.*;           
import java.sql.*;
import java.text.*;

//class to establish data base connection
class DBConnection
{
public static Connection getConnection()
{
Connection connection=null;
try
{
Class.forName("org.apache.derby.jdbc.ClientDriver");
connection=DriverManager.getConnection("jdbc:derby://localhost:1527/rfidapp1db");
}catch(Exception e)
{
System.out.println(e); 
}
return connection;
}//funtion ends
}//class ends

// a POJO class (plain old java class)
class MIFARE1KConfiguration implements java.io.Serializable
{
private int[] defaultKey;
private int[] key;
MIFARE1KConfiguration()
{
this.defaultKey=null;
this.key=null;
}
public void setDefalutKey(int [] defaultKey)
{
this.defaultKey=defaultKey;
}
public int[] getDefaultKey()
{
return this.defaultKey;
} 

public void setKey(int [] key)
{
this.key=key;
}
public int[] getKey()
{
return this.key;
} 
}//MIFARE1K class ends


class Main
{
private static MIFARE1KConfiguration mifare1KConfiguration;
public final static int CARD_ARRIVED=200;
public final static int COMMAND_SUCCESSFUL=201;
public final static int COMMAND_FAILED=202;
enum MESSAGE{CARD_ARRIVED,UNKNOWN,COMMAND_SUCCESSFUL,COMMAND_FAILED};
public final static int FORGET_CARD=555;
public final static byte FORGET_CARD_BYTES[]={53,53,53};
public final static int WRITE_DATA=666;
public final static byte WRITE_DATA_BYTES[]={54,54,54};
public final static int READ_DATA=777;
public final static byte READ_DATA_BYTES[]={55,55,55};


public static void main(String gg[])
{
//load configuration from json file
try
{
Gson gson=new Gson(); 
mifare1KConfiguration=(MIFARE1KConfiguration)gson.fromJson(new FileReader("conf"+File.separator+"mifare1k.json"),MIFARE1KConfiguration.class);
//int dk[]=mifare1KConfiguration.getDefaultKey();
//int k[]=mifare1KConfiguration.getKey();
//int i;
//for(i=0;i<dk.length;i++) System.out.print(dk[i]+" ");
//System.out.println();
//for(i=0;i<k.length;i++) System.out.print(k[i]+" ");
//System.out.println();


}catch(Exception exception)
{
System.out.println(exception);  //should be logged in a log file
                               //instead of printing it
return;
}

SerialPort ports[]=SerialPort.getCommPorts();
if(ports==null || ports.length==0)
{
System.out.println("No COM Port available");
return;
}
Scanner scanner=new Scanner(System.in);
int choice;
int i;
//code to list all COM ports
while(true)
{
System.out.println("-----------------");
System.out.println("List of COM Ports");
System.out.println("-----------------");
for(i=0;i<ports.length;i++)
{
System.out.println((i+1)+". "+ports[i].getSystemPortName());  
}
System.out.println((i+1)+". End Appliaction");
System.out.println("-----------------");
System.out.print("Select port : ");
choice=scanner.nextInt(); 
if(choice<=0 || choice>ports.length+1)
{
System.out.println("\n\nInvalid choice\n\n");
continue;
}
break;
}
if(choice==ports.length+1) return;
SerialPort serialPort=ports[choice-1];
serialPort.openPort();
if(!serialPort.isOpen())
{
System.out.println("Unable to open COM port : "+serialPort.getSystemPortName());
return;
}
serialPort.setBaudRate(9600);
try
{
int numberOfBytesAvailable;
byte bytes[];
byte bytesToSend[];
int bytesRead;
while(true)
{
System.out.println("waiting.......");
while(serialPort.bytesAvailable()==0) Thread.sleep(1000);
numberOfBytesAvailable=serialPort.bytesAvailable();
bytes=new byte[numberOfBytesAvailable];
bytesRead=serialPort.readBytes(bytes,numberOfBytesAvailable);
/*for(i=0;i<bytesRead;i++)
{
System.out.println(bytes[i]);
}*/
MESSAGE message=getMessage(bytes);
if(message==MESSAGE.UNKNOWN)
{
serialPort.writeBytes(FORGET_CARD_BYTES,3);
continue;
}
if(message==MESSAGE.CARD_ARRIVED)
{
choice=0;
while(choice<1 || choice>4)
{
System.out.println();
System.out.println();
System.out.println("1. Read From Card");
System.out.println("2. Write Student Data TO Card");
System.out.println("3. Destory Card");
System.out.println("4. Forget Card");
System.out.print("Select an option: ");
choice=scanner.nextInt();
if(choice<1 || choice>4) System.out.println("Invalid Option selected");
}
if(choice==1)
{
int rollNumber;
bytesToSend=READ_DATA_BYTES;
serialPort.writeBytes(bytesToSend,3);
System.out.println("waiting.......");
while(serialPort.bytesAvailable()==0) Thread.sleep(1000);
numberOfBytesAvailable=serialPort.bytesAvailable();
if(numberOfBytesAvailable<19)
{
System.out.println("operation FAILED, remove card");
continue; 
}
bytes=new byte[numberOfBytesAvailable];
bytesRead=serialPort.readBytes(bytes,numberOfBytesAvailable);
message=getMessage(bytes);
if(message==MESSAGE.UNKNOWN) 
{
System.out.println("operation FAILED, remove card");
continue;
}
if(message==MESSAGE.COMMAND_SUCCESSFUL)
{
byte bb[]=new byte[16];
for(i=0;i<16;i++) bb[i]=bytes[i+3];
String id=new String(bb);
Connection connection=DBConnection.getConnection();
PreparedStatement preparedStatement;
ResultSet resultSet; 
preparedStatement=connection.prepareStatement("select * from student_card_id where card_id=?");
preparedStatement.setString(1,id);
resultSet=preparedStatement.executeQuery();
if(resultSet.next()==false)
{
resultSet.close();
preparedStatement.close();
connection.close();
System.out.println("Card not in use , remove card");
continue;
} 
rollNumber=resultSet.getInt("roll_number");
resultSet.close();
preparedStatement.close();
preparedStatement=connection.prepareStatement("select * from student where roll_number=?");
preparedStatement.setInt(1,rollNumber);
resultSet=preparedStatement.executeQuery();
resultSet.next();
String name=resultSet.getString("name").trim();
java.sql.Date sqlDateOfBirth=resultSet.getDate("date_of_birth");
resultSet.close();
preparedStatement.close();
connection.close();
SimpleDateFormat simpleDateFormat=new SimpleDateFormat("dd/MM/yyyy");
String stringDateOfBirth=simpleDateFormat.format(sqlDateOfBirth);
System.out.println("Name : "+name);
System.out.println("Date of birth : "+stringDateOfBirth);
System.out.println("\n");
continue;
}
if(message==MESSAGE.COMMAND_FAILED)
{
System.out.println("operation FAILED, remove card");
continue;
}
continue;
}

//wrting in card starts 
else if(choice==2)
{
int rollNumber;
System.out.print("Enter roll number of the student : ");
rollNumber=scanner.nextInt();
Connection connection=DBConnection.getConnection();
PreparedStatement preparedStatement;
preparedStatement=connection.prepareStatement("select * from student where roll_number=?");
preparedStatement.setInt(1,rollNumber);
ResultSet resultSet=preparedStatement.executeQuery();
if(resultSet.next()==false)
{
resultSet.close();
preparedStatement.close();
connection.close();
System.out.println("Invalid roll number, operation cancelled");
serialPort.writeBytes(FORGET_CARD_BYTES,3);
continue;
}
String name=resultSet.getString("name").trim();
java.sql.Date sqlDateOfBirth=resultSet.getDate("date_of_birth");
resultSet.close();
preparedStatement.close();
SimpleDateFormat simpleDateFormat=new SimpleDateFormat("dd/MM/yyyy");
String stringDateOfBirth=simpleDateFormat.format(sqlDateOfBirth);
System.out.println("Name : "+name);
System.out.println("Date of birth : "+stringDateOfBirth);
String confirmWriteOperation;
System.out.print("Write Data (Y/N) : ");
confirmWriteOperation=scanner.next();
if(confirmWriteOperation.equalsIgnoreCase("Y")==false)
{
System.out.println("Data not written, operation cancelled");
serialPort.writeBytes(FORGET_CARD_BYTES,3);
connection.close();
continue;
}
String id=UUID.randomUUID().toString().replaceAll("-","f").substring(0,16);
bytes=id.getBytes();
bytesToSend=new byte[19];
int e;
for(e=0;e<WRITE_DATA_BYTES.length;e++)
{
bytesToSend[e]=WRITE_DATA_BYTES[e]; 
}
for(e=0;e<bytes.length;e++)
{
bytesToSend[e+3]=bytes[e];
}
serialPort.writeBytes(bytesToSend,19);

System.out.println("waiting.......");
while(serialPort.bytesAvailable()==0) Thread.sleep(1000);
numberOfBytesAvailable=serialPort.bytesAvailable();
if(numberOfBytesAvailable<19)
{
System.out.println("operation FAILED, remove card");
connection.close();
continue; 
}
bytes=new byte[numberOfBytesAvailable];
bytesRead=serialPort.readBytes(bytes,numberOfBytesAvailable);

message=getMessage(bytes);
if(message==MESSAGE.UNKNOWN)
{
System.out.println("operation FAILED remove card");
serialPort.writeBytes(FORGET_CARD_BYTES,3);
connection.close();
continue;
}
if(message==MESSAGE.COMMAND_SUCCESSFUL)
{
byte bb[]=new byte[16];
for(i=0;i<16;i++) bb[i]=bytes[i+3];
String oldId=new String(bb);
preparedStatement=connection.prepareStatement("delete from student_card_id where card_id=?");
preparedStatement.setString(1,oldId);
preparedStatement.executeUpdate();
preparedStatement.close();
preparedStatement=connection.prepareStatement("delete from student_card_id where roll_number=?");
preparedStatement.setInt(1,rollNumber);
preparedStatement.executeUpdate();
preparedStatement.close();
preparedStatement=connection.prepareStatement("insert into student_card_id values(?,?)");
preparedStatement.setString(1,id);
preparedStatement.setInt(2,rollNumber);
preparedStatement.executeUpdate();
preparedStatement.close();
connection.close();
System.out.println("operation SUCCESSFUL, remove card");
continue;
}
if(message==MESSAGE.COMMAND_FAILED)
{
System.out.println("operation FAILED, remove card");
connection.close();
continue;
}
continue;
}// writing in card ends

//destroy card starts
else if(choice==3)
{
int rollNumber;
bytesToSend=READ_DATA_BYTES;
serialPort.writeBytes(bytesToSend,3);
System.out.println("waiting.......");
while(serialPort.bytesAvailable()==0) Thread.sleep(1000);
numberOfBytesAvailable=serialPort.bytesAvailable();
if(numberOfBytesAvailable<19)
{
System.out.println("operation FAILED, remove card");
continue; 
}
bytes=new byte[numberOfBytesAvailable];
bytesRead=serialPort.readBytes(bytes,numberOfBytesAvailable);
message=getMessage(bytes);
if(message==MESSAGE.UNKNOWN) 
{
System.out.println("operation FAILED, remove card");
continue;
}
if(message==MESSAGE.COMMAND_SUCCESSFUL)
{
byte bb[]=new byte[16];
for(i=0;i<16;i++) bb[i]=bytes[i+3];
String id=new String(bb);
Connection connection=DBConnection.getConnection();
PreparedStatement preparedStatement;
ResultSet resultSet; 
preparedStatement=connection.prepareStatement("select * from student_card_id where card_id=?");
preparedStatement.setString(1,id);
resultSet=preparedStatement.executeQuery();
if(resultSet.next()==false)
{
resultSet.close();
preparedStatement.close();
connection.close();
System.out.println("Card not in use , remove card");
continue;
} 
rollNumber=resultSet.getInt("roll_number");
resultSet.close();
preparedStatement.close();
preparedStatement=connection.prepareStatement("select * from student where roll_number=?");
preparedStatement.setInt(1,rollNumber);
resultSet=preparedStatement.executeQuery();
resultSet.next();
String name=resultSet.getString("name").trim();
java.sql.Date sqlDateOfBirth=resultSet.getDate("date_of_birth");
resultSet.close();
preparedStatement.close();
SimpleDateFormat simpleDateFormat=new SimpleDateFormat("dd/MM/yyyy");
String stringDateOfBirth=simpleDateFormat.format(sqlDateOfBirth);
System.out.println("Name : "+name);
System.out.println("Date of birth : "+stringDateOfBirth);
System.out.print("Want to destroy card (Y/N) : ");
String confirmWriteOperation=scanner.next();
if(confirmWriteOperation.equalsIgnoreCase("Y")==false)
{
System.out.println("Data not written, operation cancelled");
serialPort.writeBytes(FORGET_CARD_BYTES,3);
connection.close();
continue;
}
preparedStatement=connection.prepareStatement("delete from student_card_id where card_id=?");
preparedStatement.setString(1,id);
preparedStatement.executeUpdate();
preparedStatement.close();
connection.close();
System.out.println("Card SUCCESSFULLY destroyed");
System.out.println("\n");
continue;
}
if(message==MESSAGE.COMMAND_FAILED)
{
System.out.println("operation FAILED, remove card");
continue;
}
continue;
}

else if(choice==4)
{
serialPort.writeBytes(FORGET_CARD_BYTES,3);
continue;
}
}  //CARD_ARRIVED part ends
if(message==MESSAGE.COMMAND_SUCCESSFUL)
{
System.out.println("operation SUCCESSFUL, remove card");
continue;
}
if(message==MESSAGE.COMMAND_FAILED)
{
System.out.println("operation FAILED, remove card");
continue;
}
}
}catch(Exception exception)
{
System.out.println(exception);
}
finally
{
if(serialPort.isOpen()) serialPort.closePort();
}

//wait for something to arrive from serial port 
// parse whatever arrives from serial port
}

public static MESSAGE getMessage(byte bytes[])
{
if(bytes==null || bytes.length<3) return MESSAGE.UNKNOWN;
int messageNumber=(bytes[0]-48)*100;
messageNumber+=((bytes[1]-48)*10);
messageNumber+=(bytes[2]-48);
if(messageNumber==CARD_ARRIVED) return MESSAGE.CARD_ARRIVED;
if(messageNumber==COMMAND_SUCCESSFUL) return MESSAGE.COMMAND_SUCCESSFUL;
if(messageNumber==COMMAND_FAILED) return MESSAGE.COMMAND_FAILED;
return MESSAGE.UNKNOWN;
}

}//class ends