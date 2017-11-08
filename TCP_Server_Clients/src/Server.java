import java.net.*;
import java.util.ArrayList;
import java.io.*;


public class Server extends ServerSocket{
	
	private ServerSocket serverSocket;
	private int BLOCK_TIME=60;
	private int TIME_OUT=1800;
	private int LAST_HR=3600;
	
	ArrayList<String> userList = new ArrayList<String>();//store the logged in user	
	ArrayList<String> logoutList = new ArrayList<String>();//store the users who have logged out
	
	ArrayList<InetAddress> blockIPList = new ArrayList<InetAddress>();//store the block IP
	ArrayList<String> blockList = new ArrayList<String>();//store the blocked users
	ArrayList<Long> blockTimeList = new ArrayList<Long>();//store the start block time
	
	ArrayList<Long> timeList = new ArrayList<Long>();//store the time stamps, used for checking the time when a user has logged out
	ArrayList<CreateServerThread> threadList = new ArrayList<CreateServerThread>();//store the threads
	
	ArrayList<String> offlineMessage = new ArrayList<String>();
	ArrayList<String> offlineName = new ArrayList<String>();
	ArrayList<Integer> messageType = new ArrayList<Integer>();
	
	String command;
	String infoList[];//store the original information about user and password

	
	/*
	 * The framework of the server is multi-threaded programming. 
	 * Each time the listening server accept a new connection, it will create a new thread.
	 * Besides, creating a new server socket to communicate with the new client
	 * */
	
	
	public Server (int port) throws IOException{
		
		serverSocket = new ServerSocket(port);
		
		/*Get the information of the user-password pairs*/
		String autheninfo = readfile("./user_pass.txt");
		
		infoList = autheninfo.split("\n");
		for(int i=0; i<infoList.length; i++){
			System.out.println(infoList[i]);
		}
		
		
		try{
			while(true){
				System.out.println("Waiting for clients on port "+serverSocket.getLocalPort());
				/*When listening socket accepts a new connection,
				 *it creates a new server socket and a new thread*/
				Socket socket = serverSocket.accept();
				threadList.add(new CreateServerThread(socket));
			}
		}catch (IOException e){
			e.printStackTrace();
		}
	}
	
	
	
	class CreateServerThread extends Thread{
		ArrayList<String> tuserList = userList;//store the logged in user

		private Socket server;
		private DataOutputStream out;
		private DataInputStream in;
		private volatile boolean exit=false; /*Flag to check whether exit this thread*/
		long onlineTime;
		//long commandTime;
		String uname;
		String tempName;
		String tempName_n;
		String blockName;
		InetAddress IP;
		int failCount;
		boolean blockFlag;
		boolean broken=false;
		
		public CreateServerThread(Socket s) throws IOException {
			server = s;
			out = new DataOutputStream(server.getOutputStream());
			in = new DataInputStream(server.getInputStream());
			System.out.println("New Connection: "+ server.getRemoteSocketAddress());
			IP=server.getInetAddress();
			start();
		}
		
		public void run() {
			try{
				

				boolean flag=false;
				String command=null;
				
				
					
				while(true){
					

					
					flag = login(server,in,out);
					if(broken){
						uname=" ";//to avoid null pointer exception.
						for(int i=0;i<threadList.size(); i++){
							if(threadList.get(i).uname.equals(" ")){
								threadList.remove(i);
							}
						}
						break;
					}
						
					System.out.print(flag);
					
					
					boolean rcheck=false;
					long tempTime;
					
					
						if(flag == true){
							
						  /*if the login information is right
							check if the user is already in the block list first
							if it's in, then keep looping, 
							otherwise break, and let this user login*/
							
							for(int i=0; i<blockIPList.size(); i++){
								if (IP.equals(blockIPList.get(i))){
									for(int j=0; j<blockList.size(); j++){
										if(uname.equals(blockList.get(j))){
											rcheck=true;
											break;
										}
									}
									break;
								}
							}

							if(!rcheck){
								break;
							}
							else{
								out.writeUTF("It is still blocked!");
							}
						}

						else{
							
							//if the login information is wrong
							boolean wcheck=false;
														
							//check if the user name of wrong information is still in block list.
							for(int i=0; i<blockIPList.size(); i++){
								if (IP.equals(blockIPList.get(i))){
									for(int j=0; j<blockList.size(); j++){
										if(tempName.equals(blockList.get(j))){
											wcheck=true;
											break;
										}
									}
									break;
								}
							}
														
							
							if(wcheck==true){
								out.writeUTF("Note: This user is still blocked!");
							}
							//if wrong user name is not in block list
							//then check if there are 3 consecutive wrong logins
							
							else{
								
								if(failCount==0){
									failCount++;
									tempName_n=tempName;
								}
								
								else if(failCount>0 && !tempName_n.equals(tempName)){
									failCount=1;
									tempName_n=tempName;
								}
								
								
								else if(failCount>0 && tempName_n.equals(tempName)){
									failCount++;
									if(failCount==3){
										failCount=0;
										blockName = tempName;
										
										blockList.add(blockName);
										blockIPList.add(IP);
										tempTime = System.currentTimeMillis();
										blockTimeList.add(tempTime);
										System.out.println(blockName+IP);
										blockFlag=true;
										out.writeUTF(
												"3 consecutive wrong input!\n" +
												"The user "+blockName+
												" is blocked for "+BLOCK_TIME+" seconds!");
										}
									
														
								}

							}
						}
					}			
					
					/*When the user logs in successfully
					 *give a timeout value to the server socket
					 *which means that if the socket hasn't got message from the client for this amount of time
					 *an timeout exception will be thrown
					 *and this user will be logged out automatically*/
				
					if(flag==true){
									
					onlineTime = System.currentTimeMillis();
					server.setSoTimeout(1000*TIME_OUT);
					
					while(!exit){
						
						try{
						
						
							commandList(out);
							command=in.readUTF();
							
							
							System.out.println(command);
							String realCommand = command.split("\\s+")[0];
						
						
						
							if(realCommand.equals("logout")){
							
								out.writeUTF("logout");
							
							
								boolean logoutflag = false;
								/*If this user has already in the logout list
								 *which means that this user has logged in
								 *and logged out successfully before,
								 *then replace itself at the original location
								 *instead of adding it again in the list*/
								for(int i=0; i<logoutList.size(); i++){
									if (uname.equals(logoutList.get(i))){
										logoutList.set(i, uname);
										timeList.set(i, System.currentTimeMillis());
										logoutflag=true;
									}

								}
							
							
								if(logoutflag==false){
									logoutList.add(uname);
									timeList.add(System.currentTimeMillis());
								}
								/*remove this user from online user list
								 *remove current thread from thread list*/
								for(int i=0; i<tuserList.size();i++){
									if(uname.equals(tuserList.get(i))){
										tuserList.remove(i);
										break;
									}
								
								}
								for(int i=0;i<threadList.size();i++){
									if(uname.equals(threadList.get(i).uname)){
										threadList.remove(i);
										break;
									}
								}
								server.close();
								exit=true;/*Jump out of the loop, exit this thread*/
							}
							else if (realCommand.equals("whoelse")){
								
								whoelse(out, tuserList, uname);
							}
							else if (realCommand.equals("wholasthr")){
								
								wholasthr();
							}
							else if (realCommand.equals("broadcast")){
								try{
									String mes = command.substring(10);
									broadcast(mes);	
								}catch(StringIndexOutOfBoundsException e){
									out.writeUTF("Enter some messages please!");
								}
														
								
							}
							else if (realCommand.equals("message")){
								for(int i=0; i<command.split(" ").length; i++){
									System.out.println(command.split(" ")[i]+"!@");
									System.out.print(i+"\n");
								}
								try{
									String user = command.split(" ")[1];
									int userLength = user.length();
									String mes = command.substring(9+userLength);
									message(user, mes);
								}catch (StringIndexOutOfBoundsException e){
									out.writeUTF("Enter some messages please!");
								}catch (ArrayIndexOutOfBoundsException f){
									out.writeUTF("Invalid input type!");
								}
								
							}
							else if(realCommand.equals("readoffmes")){
								readOfflineMessage();
							}
							else if(realCommand.equals("readbchis")){
								readBroadcastHistory();
							}
							else if(realCommand.equals("clearoffmes")){
								clearOffMes();
							}

							else{
								out.writeUTF("Wrong Command! Type in command again please\n");
							}

						
						}catch(SocketTimeoutException e){
							/*When timeout occurs, just act as the client has input logout.*/
							out.writeUTF(uname+": Time out!");
							long onlineTime_in=System.currentTimeMillis();
							out.writeUTF("logout");
							
							boolean logoutflag = false;
							
							for(int i=0; i<logoutList.size(); i++){
								if (uname.equals(logoutList.get(i))){
									logoutList.set(i, uname);
									timeList.set(i, onlineTime_in);
									logoutflag=true;
								}

							}
							
							
							if(logoutflag==false){
								logoutList.add(uname);
								timeList.add(onlineTime_in);
							}
							
							for(int i=0; i<tuserList.size();i++){
								if(uname.equals(tuserList.get(i))){
									tuserList.remove(i);
									threadList.remove(i);
									break;
								}
								
							}
							exit=true;
						
						}
					}
					
					
					
				}
				
				

				
			}
			catch (IOException e){
				/*Here is the operations corresponding to CTRL+C
				 *If the client press CTRL+C during the log in process
				 *We don't know the user's name, so set the thread's name whitespace
				 *And remove this thread from thread list*/
				if(uname.equals(" ")){
					System.out.println("The client at "+server.getRemoteSocketAddress()+" has input CTRL+C to log out!");
					uname=" ";//to avoid null pointer exception.
					for(int i=0;i<threadList.size(); i++){
						if(threadList.get(i).uname.equals(" ")){
							threadList.remove(i);
						}
					}
				}
				else{
					System.out.println("The client "+uname+" has input CTRL+C to log out.");
				}
				/*Then deal with this client as if it has logged out*/
				
				boolean logoutflag = false;
				
				for(int i=0; i<logoutList.size(); i++){
					if (uname.equals(logoutList.get(i))){
						logoutList.set(i, uname);
						timeList.set(i, System.currentTimeMillis());
						logoutflag=true;
					}

				}
				
				
				if(logoutflag==false){
					if(uname!=null && !uname.equals(" ")){
						logoutList.add(uname);
						timeList.add(System.currentTimeMillis());
					}
				}
				
				for(int i=0; i<tuserList.size();i++){
					if(uname.equals(tuserList.get(i))){
						tuserList.remove(i);
						threadList.remove(i);
						break;
					}
				
				}
				//e.printStackTrace();
			}
		}
		
		//login function
				public boolean login(Socket s, DataInputStream d_in, DataOutputStream d_out) throws IOException{
					DataOutputStream out = d_out;
					DataInputStream in = d_in;
					Socket server = s;
					boolean log_flag =false;
					boolean nameFlag =false;
					
					
					try{
						out.writeUTF(">Username: ");
						out.flush();
									
						String name = in.readUTF();
					
						System.out.println(server.getRemoteSocketAddress()+"\t"+name);
					
						out.writeUTF(">Password: ");
						out.flush();
					
						String password = in.readUTF();
						System.out.println(server.getRemoteSocketAddress()+"\t"+password);
					
						String loginfo = name.concat(" "+password);
						System.out.println(loginfo);
						/*Check if this user has been unblocked
						 *which means that it has been more than BLOCK_TIME since the user was blocked*/
						for(int i=0; i<blockList.size(); i++){
							if((System.currentTimeMillis()-blockTimeList.get(i))/1000>=BLOCK_TIME){
								out.writeUTF(blockList.get(i)+" has been unblocked.");
								for(int j=0; j<blockList.size(); j++){
									out.writeUTF(blockList.get(j)+"!@");
									out.writeUTF((blockTimeList.get(j).toString()));
								}
								blockList.remove(i);
								blockTimeList.remove(i);
								blockIPList.remove(i);
								
							}
						}
					
					
						for(int i=0; i<infoList.length; i++){
							/*log information matches the user-password pairs*/
							if(loginfo.equals(infoList[i])) {
								for(int j=0; j<tuserList.size(); j++){
									/*Check if there are duplicated login*/
									if(name.equals(tuserList.get(j))){
										nameFlag = true;
										break;
									}
								}
								if(!nameFlag){
							
									log_flag = true;
									break;
								}
							}
							
						}
					

					
						if (log_flag ==false){
							if(nameFlag==false){
								tempName = name;
								/*If the login information does not match
								 *Check if this user is in the block list
								 *If so, return the flag directly*/
								for(int i=0; i<blockIPList.size(); i++){
									if (IP.equals(blockIPList.get(i))){
										for(int j=0; j<blockList.size(); j++){
											if(tempName.equals(blockList.get(j))){
												return log_flag;
											}
										}
									}
								}
								out.writeUTF("Username or Password is invalid, please type again!");
								out.flush();
							
							}
							else{
								
								/*Duplicated log in, check the block list as well*/
								for(int i=0; i<blockIPList.size(); i++){
									if (IP.equals(blockIPList.get(i))){
										for(int j=0; j<blockList.size(); j++){
											if(name.equals(blockList.get(j))){
												return log_flag;
											}
										}
										
									}
								}
								out.writeUTF("Duplicated log in!");
								out.flush();
							}
						
						}
					
						else{
							/*Log in successfully*/
							
							uname = name;
							for(int i=0; i<blockIPList.size(); i++){
								if (IP.equals(blockIPList.get(i))){
									for(int j=0; j<blockList.size(); j++){
										if(name.equals(blockList.get(j))){
											return log_flag;
										}
									}
									
								}
							}
							
							userList.add(name);

							out.writeUTF("    |---Log in Successfully!---|");
							out.writeUTF("    |----Welcome to HSYCHAT----|");
							out.writeUTF("    |----Author: Songyan Hou---|"+"\n\n");
							
							out.flush();
						}

						System.out.println(tuserList);

					
					}catch (IOException e){
						broken=true;
						System.out.println("Login suspended!");
						
					}
					
					return log_flag;
					
				}
				
		

		private void clearOffMes() {
			// TODO Auto-generated method stub
			for(int i=0; i<offlineMessage.size(); i++){
				if(messageType.get(i)==2){
					if(uname.equals(offlineName.get(i))){
						offlineName.remove(i);
						offlineMessage.remove(i);
						messageType.remove(i);
					}
				}
			}
			
		}

		private void wholasthr() throws IOException {
			// TODO Auto-generated method stub
			try{
				//output the online users first
				for(int i=0; i<threadList.size(); i++){
					String res=threadList.get(i).uname;
					
					
					/*Exclude the client itself from this list*/
					if (res==uname){
						if(i==threadList.size()-1){
							break;
						}
						else{
							i++;
						}
						res=threadList.get(i).uname;
						if(res!=null){
							out.writeUTF(res+" pause");
						}
					}
					else{
						if(res!=null){
							out.writeUTF(res+" pause");
						}
					}
					
				
				}
				
				for(int i=0; i<logoutList.size(); i++){
					System.out.println(logoutList.get(i));
					System.out.print((System.currentTimeMillis()-timeList.get(i))/1000+"\n");
				}
				
				for(int i=0; i<threadList.size();i++){
					System.out.println(threadList.get(i).uname);
				}
				/*Then print out the users logged out in "LAST_HOUR"
				 *if the user exists both in user list and logout list
				 *skip this one in logout list*/
				boolean hrflag=false;
				
				for(int m=0; m<logoutList.size(); m++){
					for(int n=0; n<threadList.size(); n++){
						System.out.print(n+"\n\n\n");
						if(logoutList.get(m).equals(threadList.get(n).uname)){
							System.out.println("TestInfo!");
							hrflag=true;
							break;
						}						

					}
					if(!hrflag){
						
						if((System.currentTimeMillis()-timeList.get(m))/1000<=LAST_HR){
							out.writeUTF(logoutList.get(m));
							
						}
					}
					hrflag=false;
					
				}
				
				
				
				
			}catch (IOException e){
				e.printStackTrace();
			}

			
			
		}

		

		
		public void whoelse(DataOutputStream out, ArrayList<String> name, String self) throws IOException{
			
			DataOutputStream nameOut = out;
			ArrayList<String> nameList = name;
			String selfName = self;
			String res;
			int selfNum=0;
			
			try{
				//remove the user itself in the list
				//get the location of the user in the list
				for(int i=0; i<nameList.size(); i++){
					if(selfName.equals(nameList.get(i))){
						selfNum = i;
						break;
					}
				}
				
				//output the user list
				//if the user is not at the first location, then initialize the result
				if(selfNum!=0){
					res = nameList.get(0)+"\n";
					for(int j=1; j<nameList.size(); j++){
						//check the list
						//skip the user itself
						if(j==selfNum){
							j++;
							//check if the user's location is at the end of the list
							//if so, break the loop
							if(j>=nameList.size())
								break;
							//if not, keep concatenate the following strings
							else
								res = res.concat(nameList.get(j)+"\n");
						}
						//if not the user, concatenating the strings
						else
						res = res.concat(nameList.get(j)+"\n");
					}
					
					nameOut.writeUTF(res);
				}
				
				//if the user is at the head of the list and the size of list is bigger than 1
				//it means that there are at least two names in the list and the first is user itself
				//then we could initialize the result with the second name
				//and loop the list to concatenate the strings
				else if(selfNum==0 && nameList.size()>1){
					res = nameList.get(1)+"\n";
					for(int j=2; j<nameList.size(); j++){
						res = res.concat(nameList.get(j)+"\n");
					}
					
					nameOut.writeUTF(res);
				}
				//if the user is at the head of the list and list size is just 1
				//it means that there is no other name in the list
				//output null
				else{
					//nameOut.writeBoolean(false);
				}
				
				
				
			}catch (IOException e){
				e.printStackTrace();
			}
			
			
			
		}
		
		public void broadcast(String s) throws IOException {
			// TODO Auto-generated method stub
			
			int length = threadList.size();
			try{
				for(int i=0; i<length; i++){
					//loop the thread list, send message to other users
					//if find the sender itself, skip it
					if(uname.equals(threadList.get(i).uname)){
						//Be cautious: if the sender itself is at the end of the list
						//Break directly
						if(i == length-1){
							break;
						}
						else{
							i++;
						}
						threadList.get(i).out.writeUTF(uname+": "+s+"\n");
					}
					else
						//Not the sender itself, send the message
						threadList.get(i).out.writeUTF(uname+": "+s+"\n");
				}
				
				offlineMessage.add(s);
				offlineName.add(uname);
				messageType.add(1);
				
			}catch (IOException e){
				e.printStackTrace();
			}

			
			
		}
		
		
		
		
		private void message(String user, String mes) throws IOException {
			// TODO Auto-generated method stub
			boolean userflag =false;
			try{
				
				for(int i=0; i<threadList.size(); i++){
					System.out.println(threadList.get(i).uname);
				}
				for(int i=0; i<threadList.size(); i++){
					if(threadList.get(i).uname!=null){
						if(threadList.get(i).uname.equals(user)){
							threadList.get(i).out.writeUTF(uname+": "+mes+"\n");
							userflag = true;
							
							break;
						}
					}
				}
				//if the receiver is not online, tell the sender
				if(!userflag){
					out.writeUTF("This user is not online!\nOr the command type is invalid.\nPlease check again!");
					offlineMessage.add(uname+":"+mes);
					offlineName.add(user);
					messageType.add(2);
				}
				
			}catch (IOException e){
				e.printStackTrace();
			}catch (StringIndexOutOfBoundsException f){
				
			}
			

			
		}
		
		private void readOfflineMessage() throws IOException{
			
			try{
			for(int i=0; i<offlineMessage.size(); i++){
				if(messageType.get(i)==2){
					if(offlineName.get(i).equals(uname)){
						out.writeUTF("Offline Messages from "+offlineMessage.get(i));
					}
				}
			}
			}catch(IOException e){
				e.printStackTrace();
			}
		}
		
		private void readBroadcastHistory() throws IOException{
			// TODO Auto-generated method stub
			try{
				out.writeUTF("*****Broadcast History*****");
				for(int i=0; i<offlineMessage.size(); i++){
					if(messageType.get(i)==1){
						
						out.writeUTF(offlineName.get(i)+": "+offlineMessage.get(i));
					}
				}
				
			}catch (IOException e){
				e.printStackTrace();
			}
			
		}
		
		
		public void commandList(DataOutputStream out) throws IOException{
			
			try{
			
				out.writeUTF("||=========COMMANDS LIST==========||"+"\n"+
							 "||            whoelse             ||"+"\n"+
							 "||           wholasthr            ||"+"\n"+
							 "||      broadcast <message>       ||"+"\n"+
							 "||    message <user> <message>    ||"+"\n"+
							 "||           readoffmes           ||"+"\n"+
							 "||           readbchis            ||"+"\n"+
							 "||          clearoffmes           ||"+"\n"+
							 "||             logout             ||"+"\n"+
							 "||=====CHOOSE COMMANDS PLEASE=====||"+"\n");
			}catch (IOException e){
				e.printStackTrace();
			}
			
		}
		
		
		public void setname(String name){
			userList.add(name);
		}
		
	}
	

	
	//Read the user-password contents from user_pass.txt
	public String readfile(String path) throws IOException, FileNotFoundException{
		String authen=null;
		String temp;
		String pathname = path;
		try{
			File userpw = new File (pathname);
			//store the text in buffer for efficiency
			InputStreamReader reader = new InputStreamReader(new FileInputStream(userpw));
			BufferedReader br = new BufferedReader(reader);
			//read the user-password information and store them in s String.
		
		
			authen=br.readLine();		
			while((temp=br.readLine()) != null){
				authen=authen.concat("\n"+temp);					
			}
			br.close();
		}catch(FileNotFoundException f){
			f.printStackTrace();
		}
		//Now the authentication info is stored in string "authen"
				
		return authen;
	}
	



	public static void main(String [] args) throws IOException{
	
		int port = Integer.parseInt(args[0]);
		new Server(port);
	}

}

