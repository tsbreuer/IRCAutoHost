package autohost.utils;

import autohost.Autohost;
import autohost.RateLimiter;

public class RateLimiterThread extends Thread {
	public Autohost bot;
	private Boolean stopped = false;
	
	public RateLimiterThread(Autohost host){
		this.bot = host;		
	}
	
	public void run(){		
		while(!stopped){
			//System.out.println("loop");
			try {
		for (RateLimiter limiter : bot.limiters) {
			if (limiter.hasNext()){
			String line = limiter.updateQueue();
				if (line != null)
					System.out.println("Return line "+line);
					bot.IgnoreSend(line);	
					System.out.println("Return line2 "+line);
			}
		}
		
			Thread.sleep(200); // Message Delay interval
		} catch (Exception e) {
			e.printStackTrace();
		}
		}
	}
}
