package autohost.utils;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class InputDumper extends Thread
{
  private BufferedReader reader;
  private IRCClient client;
  
  protected InputDumper( InputStream in, IRCClient client )
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
    	  if (msg.indexOf("001") >= 0)
    		 System.out.println("Logged in");
    		  
    	  if (msg.contains("PING"))
    		  client.Write("PONG");
    		  
    	  client.log(msg);
        	
      }
    }
    catch( IOException e )
    {
      e.printStackTrace();
    }
  }
}