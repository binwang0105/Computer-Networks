import java.net.*;
import java.io.*;


public class Client {
	
	String username=null;
	String password=null;
	
	private Socket client;
	private BufferedReader input;
	private DataOutputStream out;
	private DataInputStream in;

	
	
	public Client (String serverName, int port)throws Exception{
		
		System.out.println ("Connecting to "+serverName + " on port "+ port);
		client = new Socket(serverName, port);
		System.out.println ("Just connected to "+client.getRemoteSocketAddress());

		
		
		InputStream inFromServer = client.getInputStream();
		in = new DataInputStream(inFromServer);
		OutputStream outToServer = client.getOutputStream();
		out = new DataOutputStream(outToServer);
		input = new BufferedReader(new InputStreamReader(System.in));
		
		

		new readLineThread();
		while (true){
			String clientInput = input.readLine();
			out.writeUTF(clientInput);

		}
	
	}
 
	

	
	
    class readLineThread extends Thread{
        
        private DataInputStream readIn;
        public readLineThread(){
            try {
            	readIn = new DataInputStream(client.getInputStream());
                start();
            }catch (Exception e) {
            	System.out.println("The server has logged out! You could exit now.");
            	System.exit(0);
            }
        }
          
        @Override
        public void run() {
            try {
                while(true){
                    String result = readIn.readUTF();
                    
                    if("logout".equals(result)){
                    	System.out.println("You've logged out!");
                        break;
                    }else{
                        System.out.println(result);
                    }
                }
                
                in.close();
                out.close();
                client.close();
                System.exit(0);
            }catch (Exception e) {
            	System.out.println("The server has logged out! Connection closed!");
            	System.exit(0);
            }
        }
    }


	

	
	
	public static void main(String [] args){
		String serverName = args[0];
		int port = Integer.parseInt(args[1]); 
		try{
			new Client(serverName,port);
			
		}catch (Exception e){
			e.printStackTrace();
		}
	}
}
