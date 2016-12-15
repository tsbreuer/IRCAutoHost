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
    	System.out.println("Added message to queue");
    }
    
    public String updateQueue(){
    	try{
    	System.out.println("Checking for queues..");
    	long currentTime = System.currentTimeMillis();
    	if ((currentTime - this.lastSentTime) >= delay){
    		String msg = outgoing.poll();
    		if (msg != null){
    			System.out.println("Sending raw line :"+"PRIVMSG "+this.target+" "+msg);
    			this.lastSentTime = currentTime;
    			return "PRIVMSG "+this.target+" "+msg;
    		
    		}
    	}
    	} 
    	catch (Exception e) {
    		e.printStackTrace();
    	}
    	return null;
    	
    }

	public boolean hasNext() {
            return !this.outgoing.isEmpty();
        
	}

}
