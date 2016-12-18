package autohost.utils;

import autohost.Autohost;
import autohost.RateLimiter;

public class RateLimiterThread extends Thread {
	public IRCClient bot;
	private Boolean stopped = false;
	private int timer = 200;
	
	public RateLimiterThread(IRCClient client){
		this.bot = client;		
	}

	public RateLimiterThread(IRCClient client, int timer){
		this.bot = client;		
		this.timer = timer;
	}
	

	public void run(){		
		while(!stopped){
			//System.out.println("loop");
			try {
		for (RateLimiter limiter : bot.limiters) {
			if (limiter.hasNext()){
			String line = limiter.updateQueue();
				if (line != null)
<<<<<<< HEAD
					System.out.println("Return line "+line);
					bot.IgnoreSend(line);	
					System.out.println("Return line2 "+line);
=======
					//System.out.println(line);
					bot.Write(line);		
>>>>>>> 59a28bb224cb54d1921b76ae731f2771ea56f8e4
			}
		}
		
			Thread.sleep(timer); // Message Delay interval
		} catch (Exception e) {
			e.printStackTrace();
		}
		}
	}
}
