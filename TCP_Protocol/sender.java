import java.net.*;
import java.util.Date;
import java.util.Locale;
import java.util.TimerTask;
import java.io.*;
import java.util.Timer;
import java.text.SimpleDateFormat;

public class sender {
	adfasdfasdf
	asdfasdfadfafd
	InetAddress rIP;
	int packnum;
	int rPort;
	int aPort;
	int window;
	String logfile;
	boolean FINflag=false;
	
	
	String content;
	byte[] filecontent;
	
	double alpha=0.125;
	double beta=0.25;
	long sRTT;
	long eRTT;
	long devRTT;
		
	DatagramSocket usock;
	ServerSocket rsock;
	DataInputStream in;
	
	long timeout=1000;
	Timer myTimer;
	
	int nextseqnum=0;
	byte[] sp;
	CreateSendThread sendthread;

	static final int maxSize=576;
	
	long[] timeList;
	SimpleDateFormat sdf;
	
	String slogTime;
	int slogSource;
	int slogDest;
	int slogSeq;
	int slogAck;
	byte slogflag;
	
	String rlogTime;
	int rlogSource;
	int rlogDest;
	int rlogSeq;
	int rlogAck;
	byte rlogflag;
	
	String logres;
	File log;
	
	int total;
	int totalpack;
	int retranspack;
	
	public sender(String filename, InetAddress rIP, int rPort, int aPort, String logfile, int windowsize) throws IOException{
		this.rIP=rIP;
		this.aPort=aPort;
		this.rPort=rPort;
		this.logfile=logfile;
		this.window=windowsize;
		
		String content=readfile("./"+filename);
		filecontent=content.getBytes();
		total=filecontent.length;//total bytes to be sent
		packnum=(filecontent.length/maxSize)+1;//get the number of sending packets
		timeList=new long[packnum];//record the valid sending time for RTT calculation
		
		sdf=new SimpleDateFormat("yyyy-MM-dd hh:mm:ss:SSS",Locale.US);
		
		usock=new DatagramSocket(aPort);
		rsock=new ServerSocket(aPort);
		sendthread=new CreateSendThread();
		start();
	}
	/* after sending the first packet
	 * the main thread is always listening for ACKs from the receiver
	 * each time an ACK comes or the timeout occurs
	 * the main thread will call sending packets thread
	 * to send another packet
	 * the window size of sender is always 1
	 * therefore it is a stop-and-wait mechanism*/
	public void start() throws IOException{
		sp=setPackets(nextseqnum);
		int sl=sp.length;
		DatagramPacket sPack=new DatagramPacket(sp,sl,rIP,rPort);
		usock.send(sPack);
		totalpack++;
		timeList[nextseqnum]=System.currentTimeMillis();
		
		/*sending packets log info*/
		slogTime=sdf.format(new Date());
		slogSource=aPort;
		slogDest=rPort;
		slogSeq=nextseqnum;
		slogAck=0;
		slogflag=sp[13];
		
		logres=writelog();
		
		/*the first time sending a packet, start the timer*/
		setTimer();
		/*listening socket for ACKs*/	
		Socket asock=rsock.accept();
		in = new DataInputStream(asock.getInputStream());
		
		try{
			while(true){
				/* what receiver sends back is just an integer
				 * indicating that the packet represented 
				 * by this # has been received successfully*/
				int acknum=in.readInt();
				boolean fin=in.readBoolean();
				
				/*receiving packets log info*/
				rlogTime=sdf.format(new Date());
				rlogSource=asock.getPort();
				rlogDest=aPort;
				rlogSeq=acknum;
				rlogAck=0;
				if (!fin)
					rlogflag=0x10;
				else
					rlogflag=0x11;
				logres=logres.concat(rwritelog());
				System.out.println("Get ACK: "+acknum);
				
				/*last packet*/
				if(fin){
					try{
					if(!logfile.equals("stdout")){
						log=new File("./"+logfile);
						if(!log.createNewFile()){
							System.out.println("Duplicate log file name! Not Created/Rewrite");
						}
						PrintStream logps=new PrintStream(new FileOutputStream(log));
						logps.println(logres);
						logps.close();
					}
					else
						System.out.println(logres);
					}catch(FileNotFoundException f){
						System.out.println("Unable to create file!");
					}
					
					retranspack=totalpack-packnum;
					System.out.println("Delivery completed successfully");
					System.out.println("Total bytes sent = "+total);
					System.out.println("Segments sent = "+totalpack);
					System.out.println("Segments retransmitted = "+retranspack);

					System.exit(0);
				}
				/*if the ACK corresponds to what the sender wants
				 * send next packet
				 * restart the timer*/
				if(acknum==nextseqnum){
					/*calculate the RTT and update timeout*/
					sRTT=System.currentTimeMillis()-timeList[nextseqnum];
					if(nextseqnum==0){
						eRTT=sRTT;
						devRTT=Math.abs(eRTT-sRTT);
						timeout=eRTT+4*devRTT;
					}
					updateTimeout();
					
					nextseqnum++;
					sendthread.sendData();
					restartTimer();
				}

			}
			
		}catch (IOException e){
			System.out.println("File could not be created!");
		}
	}
	/*Since there are two threads running concurrently
	 * in case of mess of log file
	 * set sending info and receiving info apart*/
	private String rwritelog() {
		
		String source=Integer.toString(rlogSource);
		String dest=Integer.toString(rlogDest);
		String seq=Integer.toString(rlogSeq);
		String ack=Integer.toString(rlogAck);
		String flag=Byte.toString(rlogflag);
		String logeRTT=Long.toString(eRTT);
		String res="TimeStamp: "+rlogTime+" "+
				"Source: "+source+" "+
				"Destination: "+dest+" "+
				"Sequence Number: "+seq+" "+
				"ACK Number: "+ack+" "+
				"Flags: "+flag+" "+
				"Estimated RTT: "+logeRTT+"\n";

		return res;
	}

	private String writelog() {
		// TODO Auto-generated method stub
		String source=Integer.toString(slogSource);
		String dest=Integer.toString(slogDest);
		String seq=Integer.toString(slogSeq);
		String ack=Integer.toString(slogAck);
		String flag=Byte.toString(slogflag);
		String logeRTT=Long.toString(eRTT);
		String res="TimeStamp: "+slogTime+" "+
				"Source: "+source+" "+
				"Destination: "+dest+" "+
				"Sequence Number: "+seq+" "+
				"ACK Number: "+ack+" "+
				"Flags: "+flag+" "+
				"Estimated RTT: "+logeRTT+"\n";

		return res;

	}
	
	/*based on the equation of calculating the estimated RTT and devRTT
	 * update the timeout*/
	public void updateTimeout(){
		eRTT=(long) ((1-alpha)*eRTT+alpha*sRTT);
		devRTT=(long) ((1-beta)*devRTT+beta*Math.abs(eRTT-sRTT));
		timeout=eRTT+4*devRTT;
	}
	
	/*Each time we restart the timer, which means timeout occurs
	 * we double the timeout value according to TCP conventions
	 * and send the packet with same sequence number*/
	public void restartTimer(){

		myTimer.cancel();
		myTimer=new Timer();

		TimerTask tt=new TimerTask() {
			public void run(){
				timeout=timeout*2;
				sendthread.sendData();
				restartTimer();
			}	
		};
		
		myTimer.schedule(tt, timeout);
		
	}
	public void setTimer(){

		myTimer=new Timer();
		
		TimerTask tt=new TimerTask() {
			public void run(){
				timeout=timeout*2;
				sendthread.sendData();
				restartTimer();

			}
			
		};
		
		myTimer.schedule(tt, timeout);
	}

	/*Start a new thread, send a packet*/
	class CreateSendThread extends Thread{

		public void run(){

		}
		public void sendData(){
			sp=setPackets(nextseqnum);
			int sl=sp.length;
			DatagramPacket sPack=new DatagramPacket(sp,sl,rIP,rPort);
		
			try{
				usock.send(sPack);
				totalpack++;
				
				slogTime=sdf.format(new Date());
				slogSource=aPort;
				slogDest=rPort;
				slogSeq=nextseqnum;
				slogAck=0;
				slogflag=sp[13];
				logres=logres.concat(writelog());
				
				timeList[nextseqnum]=System.currentTimeMillis();
				//System.out.println(timeout);
			}catch (IOException e){
				e.printStackTrace();
			}
			
		}
	}
	/*
	 * Here I set the parameter num to be the sequence number
	 * the range of num is 0--packnum-1*/
	
	public byte[] getHeaders(int num){
		int listlength;
		byte[] header = new byte[20];
		if (num==packnum-1){
			header[13]=0x11;
			listlength=filecontent.length-num*maxSize+20;
		}
		else{
			header[13]=0x10;
			listlength=maxSize+20;
		}
		
		/*header0-header3: local port and remote port*/
		header[0]= (byte)((aPort>>8)&0XFF);
		header[1]= (byte)(aPort&0XFF);
		header[2]= (byte)((rPort>>8)&0XFF);
		header[3]= (byte)(rPort&0XFF);
				
		/*header4-header7: sequence number*/
		header[4]= (byte)((num>>24)&0XFF);
		header[5]= (byte)((num>>16)&0XFF);
		header[6]= (byte)((num>>8)&0XFF);
		header[7]= (byte)(num&0XFF);
		/*header8-header11: acknowledge number
		 * since we don't need ACK number in this scenario
		 * we set this filed to be 0*/
		header[8]=header[9]=header[10]=header[11]=0;
		/*head length field and unused
		 * length:20bytes--5words*/
		header[12]=0x50;
		/* unused field+flag bit fields
		 * ignore other flags, set ACK bit 1, check the FIN flag*/

		header[18]=header[19]=header[14]=header[15]=0;
		
		/*checksum field
		 * first we got the whole packet data
		 * except leaving checksum field blank*/
		byte[] temp=new byte[listlength];
		long ckSum=0;

		for(int i=0; i<20; i++)
			temp[i]=header[i];
		if(num<packnum-1){
			for(int j=0; j<maxSize; j++){
				temp[j+20]=filecontent[num*maxSize+j];
			}
			//System.out.println("packnum:"+num);
		}
		else{
			for(int j=0; j<filecontent.length-num*maxSize; j++){
				temp[j+20]=filecontent[num*maxSize+j];
			}
			//System.out.println("packnum:"+num);
		}
		
		/*bit operations to calculate the checksum with all bytes in the packet
		 * attention: the number of packets matters when calculating the checksum
		 * differentiate the odd and even number*/
		if(listlength%2==0){
		for(int i=0; i<listlength; i+=2){
			ckSum+=(((temp[i+1]<<8)&0xFF00) ^ (temp[i]&0x00FF));
		}
		}
		else{
			for(int i=0; i<listlength-1;i+=2)
				ckSum+=(((temp[i+1]<<8)&0xFF00) ^ (temp[i]&0x00FF));
			
			ckSum+=temp[listlength-1]&0x00FF;
		}
		ckSum=(ckSum>>16)+(ckSum & 0xFFFF);
		ckSum+=(ckSum>>16);
		char tempck=(char)(~ckSum);
		
		/*fill in the checksum field*/
		header[16]=(byte)((tempck>>8)&0xFF);
		header[17]=(byte)(tempck&0xFF);
		
		return header;
	}
	/*set packets, get headers and fill in the data fields next
	 * take close care of the last packet*/	
	public byte[] setPackets(int num){
		byte[] res=new byte[maxSize+20];
		byte[] last=new byte[filecontent.length-num*maxSize+20];
		byte[] header=getHeaders(num);
		for(int i=0; i<header.length; i++){
			res[i]=header[i];
			last[i]=header[i];
		}
		if(num<packnum-1){
			for(int j=0; j<maxSize; j++){
				res[j+20]=filecontent[num*maxSize+j];
			}
			return res;
		}
		
		else{
			for(int j=0; j<filecontent.length-num*maxSize; j++){
				last[j+20]=filecontent[num*maxSize+j];
			}
			return last;
		}
		
		
	}


	public static void main(String args[]) throws IOException{
		try{
		String sendfile=args[0];
		InetAddress rIP = InetAddress.getByName(args[1]);
		int rPort=Integer.parseInt(args[2]);
		int aPort=Integer.parseInt(args[3]);
		String logfile=args[4];
		int windowsize;
		if (args.length<6)
			windowsize=1;
		else
			windowsize=Integer.parseInt(args[5]);

		
		new sender(sendfile,rIP,rPort,aPort,logfile,windowsize);
		}catch (IOException e){
			System.out.println("Wrong Input Format. Please input again!");
		}catch (ArrayIndexOutOfBoundsException e){
			System.out.println("Wrong Input Format. Please input again!");
		}
		
		
	}
	
	public String readfile(String path) throws IOException, FileNotFoundException{
		String content=null;
		String temp;
		String pathname = path;
		try{
			File testfile = new File (pathname);
			//store the text in buffer for efficiency
			InputStreamReader reader = new InputStreamReader(new FileInputStream(testfile));
			BufferedReader br = new BufferedReader(reader);
			//read the file content and store them in s String.		
			content=br.readLine();
			while((temp=br.readLine()) != null){
				content=content.concat("\n"+temp);			
			}
			br.close();
		}catch(FileNotFoundException f){
			System.out.println("File Not Found!");
			System.exit(0);
		}
				
		return content;
	}

}
