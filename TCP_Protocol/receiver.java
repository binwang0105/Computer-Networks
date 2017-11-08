import java.net.*;
import java.io.*;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;

public class receiver {
	
	static int maxSize=576;
	Socket sSocket;
	DataOutputStream out;
	static boolean[] ackList;
	
	SimpleDateFormat sdf;
	String logTime;
	int logSource;
	int logDest;
	int logSeq;
	int logAck;
	byte logflag;
	String logres;
	File log;
	
	
	public static void main(String args[]) throws IOException, ArrayIndexOutOfBoundsException{
		try{
		String filename=args[0];
		int listenPort=Integer.parseInt(args[1]);
		InetAddress senderIP=InetAddress.getByName(args[2]);
		int senderPort=Integer.parseInt(args[3]);
		String logname=args[4];
		
		new receiver(filename,listenPort,senderIP, senderPort,logname);
		}catch (IOException e){
			System.out.println("Wrong Input Format. Please input again!");
		}catch (ArrayIndexOutOfBoundsException e){
			System.out.println("Wrong Input Format. Please input again!");
		}
		
	}
	
	public receiver(String filename, int listenPort, InetAddress senderIP, int senderPort, String logname)throws IOException{

		DatagramSocket aSocket = null;
		int expected = 0; 
		byte[] seq=new byte[4];
		byte[] checksum=new byte[2];
		byte[] res;
		byte[] data;
		
		String finalres=null;
		String tempres=null;

		sdf=new SimpleDateFormat("yyyy-MM-dd hh:mm:ss:SSS",Locale.US);


		try {
			aSocket = new DatagramSocket(listenPort);
			byte[] buffer = new byte[1024];
			DatagramPacket receivepack = new DatagramPacket(buffer,buffer.length);
			
			
			while(true) {
				aSocket.receive(receivepack);
				/*Keep listening at the port*/
				logTime=sdf.format(new Date());
				
				int packlength=receivepack.getLength();
				res=receivepack.getData();
				
				/*Parse the header of receiving packets
				 * write the log file information of receiving packets*/
				logSource=(res[1]&0xFF)+((res[0]<<8)&0xFF00);
				logDest=(res[3]&0xFF)+((res[2]<<8)&0xFF00);
				
				byte[] tres=new byte[packlength];
				
				for(int i=0;i<packlength;i++){
					tres[i]=res[i];
				}
				byte flag=tres[13];
				
				data=new byte[packlength-20];
				System.arraycopy(res, 20, data, 0, packlength-20);
				
				for(int i=0; i<4;i++){
					seq[i]=res[i+4];
				}
				int temp=(seq[3]&0xFF)+((seq[2]<<8)&0xFF00)+
						((seq[1]<<16)&0xFF0000)+
						((seq[0]<<24)&0xFF000000);
				logAck=(res[11]&0xFF)+((res[10]<<8)&0xFF00)+
						((res[9]<<16)&0xFF0000)+
						((res[8]<<24)&0xFF000000);
				
				logSeq=temp;
				logflag=flag;
				if(temp==0 && logres==null)
					logres=writeLog();
				else
					logres=logres.concat(writeLog());

				checksum=checksum(tres);
				
				byte[] cktemp=new byte[2];
				cktemp[0]=res[16];
				cktemp[1]=res[17];
				
				/*Only when the first packet arrives and is not corrupted
				 * the TCP socket start the connection to send ACKs back*/
				if(temp==0 && cktemp[0]==checksum[0] && cktemp[1]==checksum[1]){
					sSocket=new Socket(senderIP,senderPort);
					//sSocket.getLocalPort();
					out=new DataOutputStream(sSocket.getOutputStream());
				}
				/*packets received successfully, no corruption*/
				if(temp==expected && cktemp[0]==checksum[0] && cktemp[1]==checksum[1]){
					/*Last Packet flag*/
					if(flag!=0x11){
						System.out.println("Send ACK: "+expected);
						/*log the information of sending ACKs*/
						logTime=sdf.format(new Date());
						logSource=listenPort;
						logDest=senderPort;
						logSeq=expected;
						logAck=0;
						logflag=flag;
						logres=logres.concat(writeLog());
						
						out.writeInt(expected);
						out.writeBoolean(false);
						out.flush();
						if(expected==0){
							finalres=new String(data,"UTF-8");
						}
					
						else{
							tempres=new String(data,"UTF-8");
							finalres=finalres.concat(tempres);
						}
						expected++;
					}
					else{
						System.out.println("Send ACK: "+expected);
						logTime=sdf.format(new Date());
						logSource=listenPort;
						logDest=senderPort;
						logSeq=expected;
						logAck=0;
						logflag=flag;
						logres=logres.concat(writeLog());
						
						out.writeInt(expected);
						out.writeBoolean(true);
						out.flush();
						if(expected==0){
							finalres = new String(data,"UTF-8");
							break;
						}
						else{

							tempres=new String(data,"UTF-8");
							finalres=finalres.concat(tempres);
							break;
						}

					}
				}
				/*
				else{
					System.out.println("Error: packet "+temp);

				}*/

			}
			if(!logname.equals("stdout")){
				log=new File("./"+logname);
				PrintStream logps=new PrintStream(new FileOutputStream(log));
				logps.println(logres);
				logps.close();
			}
			else
				System.out.println(logres);


			File file=new File("./"+filename);
			if(!file.createNewFile()){
				System.out.println("Duplicate transmitted file name! Not Created/Rewrite");
				
			}
			
			PrintStream ps=new PrintStream(new FileOutputStream(file));
			ps.println(finalres);
			ps.close();



			
				System.out.println("Delivery Completed Successfully");
			
			
		}
		catch (SocketException e) {
			System.out.println("Socket Error: " + e.getMessage());
		}
		catch (IOException e) {
			System.out.println("IO Error: " + e.getMessage());
		}
		finally {
			if (aSocket != null)
				aSocket.close();
		}
	}
	
	public String writeLog() throws FileNotFoundException{
		
			
			String source=Integer.toString(logSource);
			String dest=Integer.toString(logDest);
			String seq=Integer.toString(logSeq);
			String ack=Integer.toString(logAck);
			String flag=Byte.toString(logflag);
			String res="TimeStamp: "+logTime+" "+
					"Source: "+source+" "+
					"Destination: "+dest+" "+
					"Sequence Number: "+seq+" "+
					"ACK Number: "+ack+" "+
					"Flags: "+flag+"\n";
			return res;
	
	}

	/*Use the same algorithms with sender's to calculate the checksum*/
	public byte[] checksum(byte[] result){
		/* passing the value in the function
		 * so we should make a copy of this packet first*/
		int length=result.length;
		byte[] res=new byte[length];
		for(int i=0; i<length;i++){
			res[i]=result[i];
		}
		res[16]=0;
		res[17]=0;
		
		byte[] ck=new byte[2];
		long ckSum=0;
	
		if(length%2==0){
			for(int i=0; i<length; i+=2){
				ckSum+=(((res[i+1]<<8)&0xFF00) ^ (res[i]&0x00FF));
			}
		}
		else{
			for(int i=0; i<length-1;i+=2)
				ckSum+=(((res[i+1]<<8)&0xFF00) ^ (res[i]&0x00FF));
			ckSum+=res[length-1]&0x00FF;
		}
		ckSum=(ckSum>>16)+(ckSum & 0xFFFF);
		ckSum+=(ckSum>>16);
		char tempck=(char)(~ckSum);
		ck[0]=(byte)((tempck>>8)&0xFF);
		ck[1]=(byte)(tempck&0xFF);
		
		return ck;
	}

}
