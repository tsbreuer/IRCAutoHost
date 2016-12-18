package autohost.utils;

import autohost.Autohost;
import autohost.IRCClient;
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
					//System.out.println(line);
					bot.Write(line);		
			}
		}
		
			Thread.sleep(timer); // Message Delay interval
		} catch (Exception e) {
			e.printStackTrace();
		}
		}
	}
}
