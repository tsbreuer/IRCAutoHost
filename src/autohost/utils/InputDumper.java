package autohost.utils;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class InputDumper extends Thread
{
  private BufferedReader reader;
  protected InputDumper( InputStream in )
  {
    new DataInputStream( in );
    this.reader = new BufferedReader(new InputStreamReader(in));
  }
  
  public void run()
  {
    try
    {
      String msg;
      while ( ( msg = reader.readLine()) != null )
      {
        System.out.println( msg );
      }
    }
    catch( IOException e )
    {
      e.printStackTrace();
    }
  }
}