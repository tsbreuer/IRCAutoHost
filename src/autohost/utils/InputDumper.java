package autohost.utils;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import autohost.IRCClient;

public class InputDumper extends Thread
{
  private BufferedReader reader;
  private IRCClient client;
  
  public InputDumper( InputStream in, IRCClient client )
  {
	this.client = client;
    this.reader = new BufferedReader(new InputStreamReader(in));
  }
  
  public void run()
  {
    try
    {
      String msg;
      while ( ( msg = reader.readLine()) != null )
      {
    	  if (msg.indexOf("001") >= 0){
    		  	if (!msg.contains("cho@ppy.sh QUIT")){
    		  		System.out.println("Logged in");
    		  		System.out.println("Line: "+ msg);
    		  }
    	  }
    	  if (msg.contains("PING")){
    		  String pingRequest = msg.substring(msg.indexOf("PING")+5);
    		  client.Write("PONG "+pingRequest);
    	  }
    	  client.log(msg);
    	 // if (!msg.contains("QUIT"))
        	//System.out.println(msg);
      }
    }
    catch( IOException e )
    {
      e.printStackTrace();
    }
  }
}