package autohost.utils;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class InputDumper extends Thread
{
  private BufferedReader reader;
<<<<<<< HEAD
  protected InputDumper( InputStream in )
  {
    new DataInputStream( in );
=======
  private IRCClient client;
  
  protected InputDumper( InputStream in, IRCClient client )
  {
	this.client = client;
>>>>>>> 59a28bb224cb54d1921b76ae731f2771ea56f8e4
    this.reader = new BufferedReader(new InputStreamReader(in));
  }
  
  public void run()
  {
    try
    {
      String msg;
      while ( ( msg = reader.readLine()) != null )
      {
<<<<<<< HEAD
        System.out.println( msg );
=======
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
>>>>>>> 59a28bb224cb54d1921b76ae731f2771ea56f8e4
      }
    }
    catch( IOException e )
    {
      e.printStackTrace();
    }
  }
}