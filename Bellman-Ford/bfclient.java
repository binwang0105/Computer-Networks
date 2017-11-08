import java.net.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.io.*;

public class bfclient {
	
	DatagramSocket lsock;
	ArrayList<DatagramSocket> wsockList=new ArrayList<DatagramSocket>();
	
	//Record the distance vector info
	ArrayList<InetAddress> addrList=new ArrayList<InetAddress>();
	ArrayList<Integer> portList;
	ArrayList<Float> costList=new ArrayList<Float>();
	//Record the link info, each element here corresponds to a certain destination in DV
	ArrayList<InetAddress> linkAddr=new ArrayList<InetAddress>();
	ArrayList<Integer> linkPort=new ArrayList<Integer>();
	
	ArrayList<InetAddress> downaddrList=new ArrayList<InetAddress>();
	ArrayList<Integer> downportList=new ArrayList<Integer>();
	ArrayList<Float> downcostList=new ArrayList<Float>();

	//Record the neighbor info. Tell the client where to send its DV
	ArrayList<InetAddress> neighAddrList=new ArrayList<InetAddress>();
	ArrayList<Integer> neighPortList=new ArrayList<Integer>();
	ArrayList<Long> neighTime=new ArrayList<Long>();
	ArrayList<Float> neighCostList=new ArrayList<Float>();
	
	//Store the DatagramPacket for different neighbors
	ArrayList<DatagramPacket> dvList=new ArrayList<DatagramPacket>();
	
	//Create a thread listening the command
	ListenThread lthread;
	int timeout;
	//ArrayList<Timer> timerList=new ArrayList<Timer>();
	Timer myTimer=new Timer();
	int lport;
	InetAddress localaddr=InetAddress.getLocalHost(); 
	SimpleDateFormat sdf;
	
	public bfclient(int lport, int timeout, ArrayList<InetAddress> addrList,
			ArrayList<Integer> portList, ArrayList<Float> costList) throws IOException {
		// TODO Auto-generated constructor stub
		/* The three lists for each client store the DV information
		 * which are the current estimate distance from this node to other nodes*/
		this.lport=lport;
		this.timeout=timeout;
		this.addrList=new ArrayList<InetAddress>(addrList);
		this.portList=new ArrayList<Integer>(portList);
		this.costList=new ArrayList<Float>(costList);
		//Initially, all the link info is just the destination info
		this.linkAddr=new ArrayList<InetAddress>(addrList);
		this.linkPort=new ArrayList<Integer>(portList);
		//At first, the routing table information is just the neighbors' information
		this.neighAddrList=new ArrayList<InetAddress>(addrList);
		this.neighPortList=new ArrayList<Integer>(portList);
		this.neighCostList=new ArrayList<Float>(costList);
		this.neighTime=new ArrayList<Long>();
		this.wsockList=new ArrayList<DatagramSocket>();
		//this.timerList=new ArrayList<Timer>();
		this.dvList=new ArrayList<DatagramPacket>();
		this.downaddrList=new ArrayList<InetAddress>();
		this.downcostList=new ArrayList<Float>();
		this.downportList=new ArrayList<Integer>();
		
		long iniTime=System.currentTimeMillis();
		for(int i=0; i<neighAddrList.size(); i++){
			neighTime.add(iniTime);
		}
		sdf=new SimpleDateFormat("yyyy-MM-dd hh:mm:ss",Locale.US);
		try{
			lsock=new DatagramSocket(lport);
			//for each neighbor, assign a UDP write socket
			for(int i=0; i<neighAddrList.size(); i++){
				wsockList.add(new DatagramSocket());
			}
			for(int k=0; k<neighAddrList.size(); k++){
				byte[] dv=makeDV(neighAddrList.get(k), neighPortList.get(k));
				dvList.add(new DatagramPacket(dv, dv.length, neighAddrList.get(k), neighPortList.get(k)));
			}
			for(int j=0; j<neighAddrList.size(); j++){
				wsockList.get(j).send(dvList.get(j));
			}
			dvList.clear();
			//start a listen thread to get command input
			lthread=new ListenThread();
			lthread.start();
			start();
		}catch(SocketException s){
			s.printStackTrace();
		}
		
	}

	class ListenThread extends Thread{
		private BufferedReader input;
		public void run(){
			input = new BufferedReader(new InputStreamReader(System.in));
			System.out.println("\nDistributed Bellman-Ford Algorithm Implementation\n");
			while(true){
				try {
					System.out.println("=======COMMAND LIST=======");
					System.out.println("||       SHOWRT         ||");
					System.out.println("||       LINKDOWN       ||");
					System.out.println("||       LINKUP         ||");
					System.out.println("||       NEIGHBOR       ||");
					System.out.println("||       ADDLINK        ||");
					System.out.println("||       CHANGECOST     ||");
					System.out.println("||       CLOSE          ||");
					System.out.println("==========================\n");
					System.out.println("Please Input Command");
					String command=input.readLine();
					String[] rcommand=command.split(" ");
					if(rcommand[0].equals("SHOWRT")){
						showRT();
					}else if(rcommand.length==3&&rcommand[0].equals("LINKDOWN")){
						linkDown(rcommand[1], rcommand[2]);
						
					}else if(rcommand.length==3&&rcommand[0].equals("LINKUP")){
						linkUp(rcommand[1], rcommand[2]);
					}else if(rcommand[0].equals("CLOSE")){
						//break;
						lsock.close();
						System.out.println("Client Closed.");
						System.exit(0);
					}else if(rcommand[0].equals("NEIGHBOR")){
						showNeigh();
					}else if(rcommand[0].equals("ADDLINK")){
						addLink(rcommand[1], rcommand[2], rcommand[3]);
					}else if(rcommand[0].equals("CHANGECOST")){
						changeNeighCost(rcommand[1], rcommand[2], rcommand[3]);
					}else{
						System.out.println("Wrong Input Command! Please try again!");
					}
				}catch (IOException e) {
					System.out.println("Client Closed by CTRL-C.");
				}
			}
		}
	}

	private void start() throws IOException {
		// TODO Auto-generated method stub
		/* Communication format:
		 * Each DatagramPacket contains information about the cost from the client to other nodes
		 * (IP--Port--Cost)
		 * add the original cost from sender to receiver to the head of the packet
		 * in order to restore the value after LINKUP command
		 * when updating information, the client sends one packet containing all DV info
		 * and then send this packet to different neighbors*/
		try{
			resendTimer();			
			while(true){
				byte [] buffer = new byte[1024];
				DatagramPacket neighbourDV = new DatagramPacket(buffer, buffer.length);
				lsock.receive(neighbourDV);					
				//when receiving a new packet, check the sender's address first
				InetAddress nAddress=neighbourDV.getAddress();
				DatagramPacket temp=neighbourDV;
				byte[] desport=new byte[4];
				byte[] temparr=temp.getData();
				for(int i=0; i<4; i++){
					desport[i]=temparr[i];
				}
				int nPort=bytesToInt(desport);
				int len=temp.getLength();
				//System.out.println("Packets received from "+nPort);
				if(len==5){
				byte[] mes=temp.getData();
				if(mes[4]==0){//receive LINKDOWN message from one neighbor
					System.out.println("Received LINKDOWN MESSAGE from "+nAddress+":"+nPort);
					for(int i=0; i<neighAddrList.size(); i++){
						if(nAddress.equals(neighAddrList.get(i))&&neighPortList.get(i).equals(nPort)){
							downaddrList.add(nAddress);
							downportList.add(nPort);
							downcostList.add(neighCostList.get(i));
							neighAddrList.remove(i);
							neighPortList.remove(i);
							neighTime.remove(i);
							neighCostList.remove(i);
							wsockList.remove(i);
							for(int j=0, l=addrList.size(); j<l; ++j){
								if(linkAddr.get(j).equals(nAddress)&&linkPort.get(j).equals(nPort)){
									boolean check=false;
									for(int m=0; m<neighAddrList.size(); m++){
										if(addrList.get(j).equals(neighAddrList.get(m))&&portList.get(j).equals(neighPortList.get(m))){
											costList.set(j, neighCostList.get(m));
											linkAddr.set(j, addrList.get(j));
											linkPort.set(j, portList.get(j));
											check=true;
											break;
										}
									}
									if(!check){
										addrList.remove(j);
										portList.remove(j);
										costList.remove(j);
										linkAddr.remove(j);
										linkPort.remove(j);
										--l; --j;
									}
								}
							}
							for(int k=0; k<neighAddrList.size(); k++){
								byte[] dv=makeDV(neighAddrList.get(k), neighPortList.get(k));
								dvList.add(new DatagramPacket(dv, dv.length, neighAddrList.get(k), neighPortList.get(k)));
							}
							for(int j=0; j<neighAddrList.size(); j++){
								wsockList.get(j).send(dvList.get(j));
							}
							dvList.clear();
							resendTimer();	
							break;
						}
					}
				}else if(mes[4]==1){
					System.out.println("Received LINKUP MESSAGE from "+nAddress+":"+nPort);
					for(int i=0; i<downaddrList.size(); i++){
						if(nAddress.equals(downaddrList.get(i))&&downportList.get(i).equals(nPort)){								
							neighAddrList.add(nAddress);
							neighPortList.add(nPort);
							neighCostList.add(downcostList.get(i));
							neighTime.add(System.currentTimeMillis());
							wsockList.add(new DatagramSocket());
							boolean check=false;
							int pos=0;
							for(int j=0; j<addrList.size(); j++){
								if(addrList.get(j).equals(nAddress)&&portList.get(j).equals(nPort)){
									pos=j;
									check=true;
									break;
								}
							}
							if(!check){
								addrList.add(nAddress);
								portList.add(nPort);
								costList.add(downcostList.get(i));
								linkAddr.add(nAddress);
								linkPort.add(nPort);
							}else if(costList.get(pos)>downcostList.get(i)){
								costList.set(pos,downcostList.get(i));
								linkAddr.set(pos, nAddress);
								linkPort.set(pos, nPort);
							}
							downaddrList.remove(i);
							downportList.remove(i);
							downcostList.remove(i);
							//update the DV
							for(int k=0; k<neighAddrList.size(); k++){
								byte[] dv=makeDV(neighAddrList.get(k), neighPortList.get(k));
								dvList.add(new DatagramPacket(dv, dv.length, neighAddrList.get(k), neighPortList.get(k)));
							}
							for(int j=0; j<neighAddrList.size(); j++){
								wsockList.get(j).send(dvList.get(j));
							}
							dvList.clear();
							resendTimer();
							break;
						}
					}
				}
			}else{
				int neighPos=0;//record the position if the sender is already in the neighbor list
				boolean nFlag=false;
				for(int i=0; i<neighAddrList.size(); i++){
					if(nAddress.equals(neighAddrList.get(i))&&neighPortList.get(i).equals(nPort)){
						neighPos=i;
						nFlag=true;
						break;
					}
				}
				//if it is a new neighbor client, add it into the neighbor list
				//and set its time stamp to be current system time
				//parse the packet and get the original cost from this neighbor to the client
				//assign a new socket for this neighbor as well
				if(!nFlag){
					neighAddrList.add(nAddress);
					neighPortList.add(nPort);
					neighTime.add(System.currentTimeMillis());
					neighPos=neighAddrList.size()-1;
					float newNeighCost=0f;

					byte[] tres=temp.getData();
					byte[] res=new byte[4];
					for(int i=4; i<8; i++){
						res[i-4]=tres[i];
					}
					newNeighCost=bytesToFloat(res);
					neighCostList.add(newNeighCost);					
					wsockList.add(new DatagramSocket());
				}else{
					//if not, update this neighbor's sending packet time to be current system time
					float newNeighCost=0f;
					byte[] tres=temp.getData();
					byte[] res=new byte[4];
					for(int i=4; i<8; i++){
						res[i-4]=tres[i];
					}
					newNeighCost=bytesToFloat(res);
					neighCostList.set(neighPos,newNeighCost);
					neighTime.set(neighPos, System.currentTimeMillis());
					nFlag=false;
				}
				//check if the client's DV table is updated after receiving the packet
				boolean updateFlag=false;
				updateFlag=updateDV(neighbourDV, nAddress, nPort);

				//if the table is updated, send the client's DV table to all neighbors
				if(updateFlag){
					resendTimer();
					for(int i=0; i<neighAddrList.size(); i++){
						byte[] dv=makeDV(neighAddrList.get(i), neighPortList.get(i));
						dvList.add(new DatagramPacket(dv, dv.length, neighAddrList.get(i), neighPortList.get(i)));
					}
					for(int j=0; j<neighAddrList.size(); j++){
						wsockList.get(j).send(dvList.get(j));
					}
					dvList.clear();
					}
				}	
			}
		}catch (IOException e){
			System.out.println("I/O errors");
		}
	}
	public void changeNeighCost(String addr, String port, String cost) throws IOException{
		// TODO Auto-generated method stub
		try{
			InetAddress cip=InetAddress.getByName(addr);
			int cport=Integer.parseInt(port);
			float ccost=Float.parseFloat(cost);
			boolean check =false;
			for(int i=0; i<neighAddrList.size(); i++){
				if(neighAddrList.get(i).equals(cip)&&neighPortList.get(i).equals(cport)){
					neighCostList.set(i, ccost);
					System.out.println("Cost to neighbor "+cip+":"+cport+ " has changed to: "+ccost);
					check=true;
					break;
				}
			}
			if(!check){
				System.out.println("This client is not linked directly!");
			}
			if(check){
				for(int k=0; k<neighAddrList.size(); k++){
					byte[] dv=makeDV(neighAddrList.get(k), neighPortList.get(k));
					dvList.add(new DatagramPacket(dv, dv.length, neighAddrList.get(k), neighPortList.get(k)));
				}
				for(int j=0; j<neighAddrList.size(); j++){
					wsockList.get(j).send(dvList.get(j));
				}
				dvList.clear();
				resendTimer();
			}

		}catch (IOException e){
			e.printStackTrace();
		}
		
	}
	public void addLink(String addr, String port, String cost) throws IOException {
		// TODO Auto-generated method stub
		try {
			InetAddress addip=InetAddress.getByName(addr);
			int addport=Integer.parseInt(port);
			float addcost=Float.parseFloat(cost);
			boolean check =false;
			for(int i=0; i<neighAddrList.size(); i++){
				if(neighAddrList.get(i).equals(addip)&&neighPortList.get(i).equals(addport)){
					System.out.println("This client is already a neighbor!");
					check=true;
					break;
				}
			}
			if(!check){
				neighAddrList.add(addip);
				neighPortList.add(addport);
				neighCostList.add(addcost);
				wsockList.add(new DatagramSocket());
				neighTime.add(System.currentTimeMillis());
				System.out.println("Client "+addip+":"+addport+" has become a new neighbor!");
				boolean inAddr=false;
				for(int i=0; i<addrList.size(); i++){
					if(addrList.get(i).equals(addip)&&portList.get(i).equals(addport)){
						inAddr=true;
						break;
					}
				}
				if(!inAddr){
					addrList.add(addip);
					portList.add(addport);
					costList.add(addcost);
					linkAddr.add(addip);
					linkPort.add(addport);
				}
				for(int k=0; k<neighAddrList.size(); k++){
					byte[] dv=makeDV(neighAddrList.get(k), neighPortList.get(k));
					dvList.add(new DatagramPacket(dv, dv.length, neighAddrList.get(k), neighPortList.get(k)));
				}
				for(int j=0; j<neighAddrList.size(); j++){
					wsockList.get(j).send(dvList.get(j));
				}
				dvList.clear();
				resendTimer();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	public void showNeigh() {
		// TODO Auto-generated method stub
		String date=sdf.format(new Date());
		System.out.println("Current Time: "+date+"\nThe neighbor list:");
		for(int i=0; i<neighAddrList.size(); i++){
			System.out.println(neighAddrList.get(i)+":"+neighPortList.get(i)+", Cost = "+neighCostList.get(i));
		}
	}
	public void linkUp(String IP, String port) throws IOException{
		// TODO Auto-generated method stub
		try{
			InetAddress upip=InetAddress.getByName(IP);
			int upport=Integer.parseInt(port);
			boolean flag=false;
			for(int i=0; i<downaddrList.size(); i++){
				if(upip.equals(downaddrList.get(i))&&downportList.get(i).equals(upport)){
					flag=true;
					byte[] upmes=new byte[5];
					byte[] listenPort=intToBytes(lport);
					for(int j=0; j<4; j++) upmes[j]=listenPort[j]; 
					upmes[4]=1;
					DatagramPacket uppack=new DatagramPacket(upmes,5,upip,upport);
					wsockList.add(new DatagramSocket());
					wsockList.get(wsockList.size()-1).send(uppack);					
					neighAddrList.add(upip);
					neighPortList.add(upport);
					neighCostList.add(downcostList.get(i));
					neighTime.add(System.currentTimeMillis());
					boolean check=false;
					int pos=0;
					for(int j=0; j<addrList.size(); j++){
						if(addrList.get(j).equals(upip)&&portList.get(j).equals(upport)){
							pos=j;
							check=true;
							break;
						}
					}
					if(!check){
						addrList.add(upip);
						portList.add(upport);
						costList.add(downcostList.get(i));
						linkAddr.add(upip);
						linkPort.add(upport);
					}else if(costList.get(pos)>downcostList.get(i)){
						costList.set(pos,downcostList.get(i));
						linkAddr.set(pos, upip);
						linkPort.set(pos, upport);
					}					
					downaddrList.remove(i);
					downportList.remove(i);
					downcostList.remove(i);
					//update the DV
					for(int k=0; k<neighAddrList.size(); k++){
						byte[] dv=makeDV(neighAddrList.get(k), neighPortList.get(k));
						dvList.add(new DatagramPacket(dv, dv.length, neighAddrList.get(k), neighPortList.get(k)));
					}
					for(int j=0; j<neighAddrList.size(); j++){
						wsockList.get(j).send(dvList.get(j));
					}
					dvList.clear();
					resendTimer();
					break;
				}
			}
			if(!flag){
				System.out.println("This link has not been broken before! Please check again!");
			}
		}catch (IOException e){
			e.printStackTrace();
		}
	}
	public void linkDown(String IP, String port) throws IOException{
		// TODO Auto-generated method stub
		try {
			InetAddress downip=InetAddress.getByName(IP);
			int downport=Integer.parseInt(port);
			boolean flag=false;
			for(int i=0; i<neighAddrList.size(); i++){
				if(downip.equals(neighAddrList.get(i))&&neighPortList.get(i).equals(downport)){
					flag=true;
					downaddrList.add(downip);
					downportList.add(downport);
					downcostList.add(neighCostList.get(i));
					//send link down message to the neighbor
					byte[] downmes=new byte[5];
					byte[] listenPort=intToBytes(lport);
					for(int j=0; j<4; j++) downmes[j]=listenPort[j]; 
					downmes[4]=0;
					DatagramPacket downpack=new DatagramPacket(downmes,downmes.length,downip,downport);
					wsockList.get(i).send(downpack);
					//remove this neighbor from the list
					neighAddrList.remove(i);
					neighPortList.remove(i);
					neighTime.remove(i);
					neighCostList.remove(i);
					wsockList.remove(i);
					for(int j=0, len=addrList.size(); j<len; j++){
						if(linkAddr.get(j).equals(downip)&&linkPort.get(j).equals(downport)){
							boolean check=false;
							for(int m=0; m<neighAddrList.size(); m++){
								if(addrList.get(j).equals(neighAddrList.get(m))&&portList.get(j).equals(neighPortList.get(m))){
									costList.set(j, neighCostList.get(m));
									linkAddr.set(j, addrList.get(j));
									linkPort.set(j, portList.get(j));
									check=true;
									break;
								}
							}
							if(!check){
								System.out.println("Temporary Removed Nodes "+addrList.get(j)+":"+portList.get(j));
								addrList.remove(j);
								portList.remove(j);
								costList.remove(j);
								linkAddr.remove(j);
								linkPort.remove(j);
								--len; --j;
							}
						}
					}
					//update the DV Temporary
					for(int k=0; k<neighAddrList.size(); k++){
						byte[] dv=makeDV(neighAddrList.get(k), neighPortList.get(k));
						dvList.add(new DatagramPacket(dv, dv.length, neighAddrList.get(k), neighPortList.get(k)));
					}
					for(int j=0; j<neighAddrList.size(); j++){
						wsockList.get(j).send(dvList.get(j));
					}
					resendTimer();
					dvList.clear();
					break;
				}
			}
			if(!flag){
				System.out.println("This node is not the neighbor of the client! Please try again!");
			}
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			System.out.println("This node is not the neighbor of this client! Please try again!");
			e.printStackTrace();
		}	
	}
	public void showRT() {
		// TODO Auto-generated method stub
		String date=sdf.format(new Date());
		System.out.println("Current Time: "+date+"\nThe DV Table:");
		for(int i=0; i<addrList.size(); i++){
			System.out.println("Destination = "+addrList.get(i)+":"+portList.get(i)+", Cost = "+costList.get(i)
					+", Link = ("+linkAddr.get(i)+":"+linkPort.get(i)+")");
		}
	}
	/*Transfer the DV information into byte array for future sending to the given neighbor*/
	public byte[] makeDV(InetAddress neighIP, Integer neighPort) {
		ArrayList<Byte> res=new ArrayList<Byte>();		
		//add the original cost to the given neighbor into the packets
		byte [] ocost=new byte[4];
		byte [] listenPort=new byte[4];
		int lis=lport;
		listenPort=intToBytes(lis);
		for(int n=0; n<neighAddrList.size(); n++){
			if(neighIP.equals(neighAddrList.get(n))&&neighPortList.get(n).equals(neighPort)){
				ocost = floatToBytes(neighCostList.get(n));
			}
		}
		for(int m=0; m<4; m++){
			res.add(listenPort[m]);
		}
		for(int m=0; m<4; m++){
			res.add(ocost[m]);
		}
		//retrieve the routing table information of each destination
		for(int i=0; i<addrList.size(); i++){
			byte [] addr=addrList.get(i).getAddress();
			byte [] port=intToBytes(portList.get(i));
			byte [] cost=floatToBytes(costList.get(i));
			byte [] linkaddr=linkAddr.get(i).getAddress();
			byte [] linkport=intToBytes(linkPort.get(i));
			
			for(int j=0; j<4; j++){
				res.add(addr[j]);
			}
			for(int k=0; k<4; k++){
				res.add(port[k]);
			}
			for(int l=0; l<4; l++){
				res.add(cost[l]);
			}
			for(int m=0; m<4; m++){
				res.add(linkaddr[m]);
			}
			for(int n=0; n<4; n++){
				res.add(linkport[n]);
			}
		}
		byte[] resDV=new byte[res.size()];
		for(int i=0; i<resDV.length; i++){
			resDV[i]=res.get(i);
		}
		return resDV;
	}
	public boolean updateDV(DatagramPacket neighbourDV, InetAddress nAddress, int nPort) throws IOException {
		// TODO Auto-generated method stub
		boolean update=false;	
		DatagramPacket tempDV=neighbourDV;
		int packlength=tempDV.getLength();
		byte[] tres=tempDV.getData();
		byte[] res=new byte[packlength];
		for(int i=0; i<packlength; i++){
			res[i]=tres[i];
		}
		byte[] ip=new byte[4];
		byte[] portarray=new byte[4];
		byte[] costarray=new byte[4];
		byte[] linkaddr=new byte[4];
		byte[] linkport=new byte[4];
		//check if the sender is in the DV of client	
		boolean dvFlag=false;
		int neighpos=0;//the position of the sender in client's DV table
		float neighcost=0f;

		for(int i=0; i<addrList.size(); i++){
			if(nAddress.equals(addrList.get(i))&&portList.get(i).equals(nPort)){
				neighpos=i;
				dvFlag=true;
				break;
			}
		}
		//if not, add this sender into DV table
		//set update flag to be true
		//find the cost from this new sender to the client
		if(!dvFlag){
			addrList.add(nAddress);
			portList.add(nPort);
			neighpos=addrList.size()-1;
			linkAddr.add(nAddress);
			linkPort.add(nPort);
			byte[] localip=new byte[4];
			byte[] localport=new byte[4];
			byte[] localcost=new byte[4];
			for(int j=8; j<res.length; j+=20){
				for(int k=0; k<4; k++){
					localip[k]=res[j+k];
				}
				for(int n=0; n<4; n++){
					localport[n]=res[j+n+4];
				}
				//System.out.println(InetAddress.getByAddress(localip));
				//System.out.println(bytesToInt(localport));
				if(localaddr.equals(InetAddress.getByAddress(localip))&&lport==bytesToInt(localport)){
					for(int m=0; m<4; m++){
						localcost[m]=res[j+m+8];
					}
					neighcost=bytesToFloat(localcost);
					costList.add(neighcost);
				}
			}
			update=true;
		}else{
			//if the sender is already in the DV table
			//record the cost between these two nodes
			//System.out.println("test!!");			
			neighcost=costList.get(neighpos);			
		}
		//temp array list record the link address equals to the sender
		
		ArrayList<InetAddress> tempLinkAddr=new ArrayList<InetAddress>();
		ArrayList<Integer> tempLinkPort=new ArrayList<Integer>();
		for(int i=0; i<linkAddr.size(); i++){
			boolean f1=linkAddr.get(i).equals(nAddress)&&linkPort.get(i)==nPort;
			boolean f2=(!linkAddr.get(i).equals(addrList.get(i)))||
					(!linkPort.get(i).equals(portList.get(i)));
			//System.out.println(f1+"  "+f2);
			//System.out.println("sender: "+"LINK: "+nAddress+" ADDR: "+nPort);
			//System.out.println("Initial addr: "+"LINK: "+linkAddr.get(i)+" ADDR: "+addrList.get(i));
			//System.out.println("Initial Port: "+"LINK: "+linkPort.get(i)+" ADDR: "+portList.get(i));
			if(f1&&f2){
				tempLinkAddr.add(addrList.get(i));
				tempLinkPort.add(portList.get(i));
			}
		}
		//System.out.println("Initial size: "+tempLinkPort.size());
		//parse the packet sent from the sender
		//check each DV of this sender and update the client's DV using BF algorithm
		for(int num=8; num<res.length; num+=20){//the real DV starts from res[8]
			
			for(int i=0; i<4; i++){
				ip[i]=res[num+i];
			}
			for(int j=0; j<4; j++){
				portarray[j]=res[num+j+4];
			}
			for(int k=0; k<4; k++){
				costarray[k]=res[num+k+8];
			}
			for(int m=0; m<4; m++){
				linkaddr[m]=res[num+m+12];
			}
			for(int n=0; n<4; n++){
				linkport[n]=res[num+n+16];
			}
			try{
				InetAddress addr=InetAddress.getByAddress(ip);
				int port = bytesToInt(portarray);
				float cost=bytesToFloat(costarray);
				InetAddress firstAddr=InetAddress.getByAddress(linkaddr);
				int firstPort=bytesToInt(linkport);
				
				if(firstAddr.equals(localaddr)&&firstPort==lport) {/*System.out.println("SKIP! DEST: "+port);*/continue;}
				if(addr.equals(localaddr)&&port==lport) continue;
				
				//for each node in sender's DV, check if it is in the temp list
				//if so, remove it from the list, indicating that the sender still keeps that link
				
				Iterator<InetAddress> sListIterator = tempLinkAddr.iterator();
				Iterator<Integer>pListIterator = tempLinkPort.iterator();
				while(sListIterator.hasNext()&&pListIterator.hasNext()){  
				    InetAddress e = sListIterator.next();
				    int p=pListIterator.next();
				    if(e.equals(addr)&&p==port){
				        pListIterator.remove();
				        sListIterator.remove();  
				    }  
				}
				boolean check=false;
				int pos=0;//if the destination is already in the DV, record the position 
				for(int i=0; i<addrList.size(); i++){
					if(addrList.get(i).equals(addr)&&portList.get(i).equals(port)){
						check=true;
						pos=i;
						break;
					}
				}
				//if the destination is not in client's DV table
				//add this new destination and set the first link to be the sender
				//set the update flag true
				if(check==false){
					addrList.add(addr);
					portList.add(port);
					costList.add(cost+neighcost);
					linkAddr.add(linkAddr.get(neighpos));
					linkPort.add(linkPort.get(neighpos));
					update=true;
				}else{
					//if the destination is already in the DV table
					//check if the sum of client--sender and sender--destination is smaller than client--destination
					//if so, update the DV table, set the link to be sender, and set update flag true
					if(costList.get(pos)>neighcost+cost){
						costList.set(pos, neighcost+cost);
						linkAddr.set(pos, linkAddr.get(neighpos));
						linkPort.set(pos, linkPort.get(neighpos));
						update=true;
					}
					if(costList.get(pos)<neighcost+cost&&linkAddr.get(pos).equals(nAddress)&&linkPort.get(pos).equals(nPort)){
						addrList.remove(pos);
						portList.remove(pos);
						costList.remove(pos);
						linkAddr.remove(pos);
						linkPort.remove(pos);
					}
				}				
			}catch(IOException e){
				e.printStackTrace();
			}
		}
		if(!tempLinkPort.isEmpty()){
			for(int i=0; i<tempLinkPort.size(); i++){
				boolean f=false;
				for(int j=0; j<neighAddrList.size(); j++){
					if(tempLinkAddr.get(i).equals(neighAddrList.get(j))&&
							tempLinkPort.get(i).equals(neighPortList.get(j))){
						int setpos=0;
						for(int m=0; m<addrList.size();m++){
							if(tempLinkAddr.get(i).equals(addrList.get(m))&&tempLinkPort.get(i).equals(portList.get(m))){
								setpos=m;break;
							}
						}
						costList.set(setpos, neighCostList.get(j));
						linkAddr.set(setpos, tempLinkAddr.get(i));
						linkPort.set(setpos, tempLinkPort.get(i));
						f=true;
					}
				}
				if(!f){
					int delpos=0;
					for(int m=0; m<addrList.size(); m++){
						if(tempLinkAddr.get(i).equals(addrList.get(m))&&tempLinkPort.get(i).equals(portList.get(m))){
							delpos=m;
							break;
						}
					}
					//System.out.println("To be deleted: "+tempLinkPort.get(i));
					addrList.remove(delpos);
					portList.remove(delpos);
					costList.remove(delpos);
					linkAddr.remove(delpos);
					linkPort.remove(delpos);
					//for(int n=0; n<portList.size(); n++) System.out.println("Left: "+portList.get(n));
				}
			}
			update=true;
		}
		for(int i=0; i<addrList.size(); i++){
			for(int j=0; j<neighAddrList.size(); j++){
				if(addrList.get(i).equals(neighAddrList.get(j))&&portList.get(i).equals(neighPortList.get(j))){
					if(costList.get(i)>neighCostList.get(j)){
						costList.set(i, neighCostList.get(j));
						linkAddr.set(i, addrList.get(i));
						linkPort.set(i, portList.get(i));
					}
					break;
				}
			}
		}
		return update;
	}
	private byte[] floatToBytes(float cost) {
		float temp=cost;
		byte [] res=new byte[4];
		int l=Float.floatToIntBits(temp);
		res=intToBytes(l);
		return res;
	}
	private byte[] intToBytes(int port) {
		int temp=port;
		byte[] res=new byte[4];
		//from the most significant to the least significant
		res[0]= (byte) ((temp>>24)&0xFF);
		res[1]= (byte) ((temp>>16)&0xFF);
		res[2]= (byte) ((temp>>8)&0xFF);
		res[3]= (byte) (temp&0xFF);
		return res;
	}
	private float bytesToFloat(byte[] costarray) {
		float res=0.0f;
		int temp=bytesToInt(costarray);
		res=Float.intBitsToFloat(temp);
		return res;
	}
    private int bytesToInt(byte[] b) {  
        int i = (b[0] << 24) & 0xFF000000;  
        i |= (b[1] << 16) & 0xFF0000;  
        i |= (b[2] << 8) & 0xFF00;  
        i |= b[3] & 0xFF;  
        return i;  
    }
    /* Each time this function is called, reset the timer first
     * Then check if there are neighbors who have not sent packets to the client for 3*timeout long
     * Set the timer task after one timeout long to be:
     * 1.sending the DV table to all neighbors
     * 2.restart this function*/
    
	public void resendTimer(){

		myTimer.cancel();
		myTimer=new Timer();
		try {
			checkTimeout();
		} catch (IOException e2) {
			e2.printStackTrace();
		}		
		TimerTask tt=new TimerTask(){
			public void run(){
				try {
					checkTimeout();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				for(int i=0; i<neighAddrList.size(); i++){
					byte[] dv=makeDV(neighAddrList.get(i), neighPortList.get(i));
					dvList.add(new DatagramPacket(dv, dv.length, neighAddrList.get(i), neighPortList.get(i)));
				}
				for(int j=0; j<neighAddrList.size(); j++){
					try {
						wsockList.get(j).send(dvList.get(j));
						//System.out.println("Timeout Resend to"+neighPortList.get(j));
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				dvList.clear();
				resendTimer();
			}	
		};
		myTimer.schedule(tt, timeout*1000);
	}
	public void checkTimeout() throws IOException{
		long curTime=System.currentTimeMillis();
		/* Use system time to check if there are any neighbors who have not sent packet to this client for 3*timeout long
		 * Each time the client receives a packet
		 * it will check the address and update the receiving time of this sending neighbor
		 * If neighbors found, remove them from the neighbor list and DV table*/
		boolean flag=false;
		for(int i=0; i<neighTime.size(); i++){
			if(curTime-neighTime.get(i)>=3*timeout*1000){
				flag=true;
				InetAddress timeoutIP=neighAddrList.get(i);
				Integer timeoutPort=neighPortList.get(i);
				System.out.println("Connection with client "+
						timeoutIP.toString()+":"+timeoutPort+
						" has broken due to 3*TIMEOUT");
				neighAddrList.remove(i);
				neighPortList.remove(i);
				neighCostList.remove(i);
				neighTime.remove(i);
				wsockList.remove(i);
				for(int j=0, len=addrList.size(); j<len; ++j){
					boolean f1=timeoutIP.equals(addrList.get(j))&&timeoutPort.equals(portList.get(j));
					boolean f2=timeoutIP.equals(linkAddr.get(j))&&timeoutPort.equals(linkPort.get(j));
					if(f1){						
						addrList.remove(j);
						portList.remove(j);
						costList.remove(j);
						linkAddr.remove(j);
						linkPort.remove(j);
						--len; --j;
						//System.out.println("Check");
					}else if((!f1)&&f2){
						boolean delflag=false;
						for(int m=0; m<neighAddrList.size(); m++){
							if(addrList.get(j).equals(neighAddrList.get(m))&&portList.get(j).equals(neighPortList.get(m))){
								costList.set(j, neighCostList.get(m));
								linkAddr.set(j, addrList.get(j));
								linkPort.set(j, portList.get(j));
								//System.out.println("Update: "+portList.get(j));
								delflag=true;
								break;
							}
						}
						if(!delflag){
							addrList.remove(j);
							portList.remove(j);
							costList.remove(j);
							linkAddr.remove(j);
							linkPort.remove(j);
							--len; --j;
						}
					}
				}
			}
		}
		if(flag){
			for(int i=0; i<neighAddrList.size(); i++){
				byte[] dv=makeDV(neighAddrList.get(i), neighPortList.get(i));
				dvList.add(new DatagramPacket(dv, dv.length, neighAddrList.get(i), neighPortList.get(i)));
			}
			for(int j=0; j<neighAddrList.size(); j++){
				wsockList.get(j).send(dvList.get(j));
			}
			resendTimer();
			dvList.clear();
		}
	}
	public static void main(String args[]) throws IOException{
		try{
			int lport=Integer.parseInt(args[0]);
			int timeout=Integer.parseInt(args[1]);
			ArrayList<InetAddress> addrList=new ArrayList<InetAddress>();
			ArrayList<Integer> portList = new ArrayList<Integer>();
			ArrayList<Float> costList = new ArrayList<Float>();
			for(int i=2; i<args.length; i+=3){
				addrList.add(InetAddress.getByName(args[i]));
				portList.add(Integer.parseInt(args[i+1]));
				costList.add(Float.parseFloat(args[i+2]));
			}
			new bfclient(lport,timeout, addrList, portList, costList);
		}catch (IOException e){
			System.out.println("Wrong Input Format! Please Type Again!");
		}
	}
}