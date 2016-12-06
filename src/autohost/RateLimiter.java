package autohost;

import java.util.LinkedList;
import java.util.Queue;


public class RateLimiter {
    private int delay;
    public String target;
    private Queue<String> outgoing = new LinkedList<>();
    private long lastSentTime = 0;

    public RateLimiter(String target, int delay) {
    	this.target = target;
        this.delay = delay;
    }
    
    public void addMessage(String message){
    	this.outgoing.add(message);
    }
    
    public void updateQueue(Autohost host){
    	long currentTime = System.currentTimeMillis();
    	if ((currentTime - this.lastSentTime) >= delay){
    		String msg = outgoing.poll();
    		if (msg != null){
    		host.sendRawLine("PRIVMSG "+this.target+" "+msg);
    		this.lastSentTime = currentTime;
    		}
    	}
    }

}
