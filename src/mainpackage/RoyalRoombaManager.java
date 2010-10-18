package mainpackage;
import java.io.FileWriter;
import java.io.IOException;

import com.rabbitmq.client.*;

public class RoyalRoombaManager{
	
	//Declare constant variables for RabbitMQ server
	public static final String HOST = "www.vorce.net";
	public static final String EXCHANGE = "amq.topic";
	public static final String ROUTING_KEY_1 = "roomba1";
	public static final String ROUTING_KEY_2 = "roomba2";
	public static final String SERVER_KEY = "server";
	public static final int PORT = AMQP.PROTOCOL.PORT;
	
	//Declare roombacomm variables. Variables are not
	//set to constants in case we want to implement easy reconnection
	//for DCs
	public static RoombaControl roomba1;
	public static RoombaControl roomba2;
	public static String port1 = "COM40";
	public static String port2 = "COM41";
	
	public static void main(String args[]){
		
		//Instantiate roombas
		//Comment out if need to test
		//roomba1 = new RoombaControl(port1);
		//roomba2 = new RoombaControl(port2);		
		
		try {
			//Set up RabbitMQ Connection
			ConnectionFactory factory = new ConnectionFactory();
			Connection conn = factory.newConnection(HOST);
			Channel channel = conn.createChannel();
			
			//Declare exchange to be used and bind a queue
			//And bind 2 routing keys (one for each roomba
			//to the queue
			channel.exchangeDeclare(EXCHANGE, "topic", true);
			String queueName = channel.queueDeclare().getQueue();
			channel.queueBind(queueName, EXCHANGE, ROUTING_KEY_1);
			channel.queueBind(queueName, EXCHANGE, ROUTING_KEY_2);
			channel.queueBind(queueName, EXCHANGE, SERVER_KEY);
			
			//Print out queue name to notify connection
			System.out.println("Queue Binding Complete");
			System.out.println("Queue Name is: "+queueName);
			
			//Instantiate a consumer to consume the queues
			boolean noAck = false;
			QueueingConsumer consumer = new QueueingConsumer(channel);
			channel.basicConsume(queueName, noAck, consumer);
			
			//Infinite Loop to listen to deliveries
			//from the rabbitmq server
			//Loop can be terminated by changing loopTermination
			boolean loopTermination = true;
			
			while (loopTermination) {
			    QueueingConsumer.Delivery delivery;
			    try {
			        delivery = consumer.nextDelivery();
				    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);			    		
				    System.out.println(delivery.getEnvelope().getRoutingKey()+" "+(new String(delivery.getBody())));
				    
				    //Check the routing key of each delivery's envelope and pass them
				    //To the respective roombas for actions
				    //Check RoombaControl.java for the list of commands
				    if(delivery.getEnvelope().getRoutingKey().equals(ROUTING_KEY_1)){
				    	//Roomba1
					    //roomba1.roombaAction(new String(delivery.getBody()));	
				    }else if(delivery.getEnvelope().getRoutingKey().equals(ROUTING_KEY_2)){
				    	//Roomba2
					    //roomba2.roombaAction(new String(delivery.getBody()));	
				    }else if(delivery.getEnvelope().getRoutingKey().equals("SERVER_KEY")){
				    	
				    	if(delivery.getBody().equals("STOP_CONSUME")){
				    		loopTermination = false;
				    	}
				    }
			
			    } catch (InterruptedException ie) {
			        ie.printStackTrace();
			    }
			}
			
		} catch (Exception ex) {
			System.err.println("Main thread caught exception: " + ex);
		    ex.printStackTrace();
		    System.exit(1);
		}
	}
	
	public static void publish(String portname,String thismessage){
		
		
	}
	
	public static void trackRoomba(String portname, int distance, int angle){
		try 
		{ 
		    String filename= "data.txt"; 
		    boolean append = true; 
		    FileWriter fw = new FileWriter(filename,append); 

		    fw.write("add a line\n");//appends the string to the file 
		    fw.close(); 
		} 

		catch(IOException ioe) 
		{ 
		    System.err.println("IOException: " + ioe.getMessage()); 
		} 
	}
	
}
