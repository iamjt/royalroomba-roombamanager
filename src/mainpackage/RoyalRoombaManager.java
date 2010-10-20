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
	public static final ConnectionFactory FACTORY = new ConnectionFactory();;
	public static final String PUBLISH_KEY_1 = "roomba-out-1";
	public static final String PUBLISH_KEY_2 = "roomba-out-2";
	public static Connection conn;
	public static Channel channel;
	
	//Declare roombacomm variables. Variables are not
	//set to constants in case we want to implement easy reconnection
	//for DCs
	public static RoombaControl roomba1;
	public static RoombaControl roomba2;
	public static String port1 = "COM40";
	public static String port2 = "COM41";
	
	//Map variables used to track roombas
	//initial values of x and y are tentative
	public static final double PI = 3.1415;
	public static double roomba1X = -200;
	public static double roomba1Y = 0;
	public static double roomba1Angle = 0;
	public static double roomba2X = 200;
	public static double roomba2Y = 0;
	public static double roomba2Angle = 180;
	
	public static void main(String args[]){
		
		try {
			//Set up RabbitMQ Connection
			conn = FACTORY.newConnection(HOST);
			channel = conn.createChannel();
			
			//Instantiate roombas
			//Comment out if need to test
			roomba1 = new RoombaControl(port1);
			//roomba2 = new RoombaControl(port2);
			
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
				    
			        //Debug Statement for the message consumed
			        //System.out.println(delivery.getEnvelope().getRoutingKey()+" "+(new String(delivery.getBody())));
				    
				    //Check the routing key of each delivery's envelope and pass them
				    //To the respective roombas for actions
				    //Check RoombaControl.java for the list of commands
				    if(delivery.getEnvelope().getRoutingKey().equals(ROUTING_KEY_1)){
				    	//Roomba1
					    roomba1.roombaAction(new String(delivery.getBody()));	
				    }else if(delivery.getEnvelope().getRoutingKey().equals(ROUTING_KEY_2)){
				    	//Roomba2
					    roomba2.roombaAction(new String(delivery.getBody()));	
				    }else if(delivery.getEnvelope().getRoutingKey().equals("SERVER_KEY")){
				    	//Terminate the consumption loop
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
		
		trackRoomba("COM40", 30, 15);
	}
	
	public static void trackRoomba(String portname, int distance, int angle){
		
		if(!((distance==0)&&(angle==0))){
			//instantiate offsets to calculate
			double xOffset=0, yOffset=0, currentAngle;
			
			if(portname.equals("COM40")){
				currentAngle = roomba1Angle + angle;
			}else{
				currentAngle = roomba2Angle + angle;
			}
			
			//Calculate the cartesian offsets from the angle
			//Angles in radians
			//currentAngle is the current direction the roomba is facing.
			//Negative because roomba calculates its direction in the opposite
			//clockwise manner as compared to the usual anticlock wise
			//used in polar coordinates formulae
			xOffset = Math.cos( -currentAngle/360 *PI ) * distance;
			yOffset = Math.sin( -currentAngle/360 *PI ) * distance;
			
			//Add the offsets to the respective roomba coordinates
			//and update the angle to the roomba angle
			if(portname.equals("COM40")){
				roomba1X += xOffset;
				roomba1Y += yOffset;
				roomba1Angle = currentAngle;
			}else{
				roomba2X += xOffset;
				roomba2Y += yOffset;
				roomba2Angle = currentAngle;
			}
			
			//Output to text for debugging in flash
			/*try 
			{ 
			    String filename= "data.txt"; 
			    boolean append = true; 
			    FileWriter fw = new FileWriter(filename,append); 
			    String data = xOffset+","+yOffset+"\n";
			    System.out.println(data);
			    fw.write(data);//appends the string to the file 
			    fw.flush();
			    fw.close(); 
			 
			}catch(IOException ioe){ 
			   ioe.printStackTrace();
			   System.exit(1);
			}*/
			
			//Calculate Relative position of the 2 roombas
			double roomba1EnemyX = roomba2X-roomba1X;
			double roomba1EnemyY = roomba2Y-roomba1Y;
			double roomba2EnemyX = roomba1X-roomba2X;
			double roomba2EnemyY = roomba1Y-roomba2Y;
			
			//publish the relative positions to the server
			publish("roomba-enemy-1", roomba1EnemyX+","+roomba1EnemyY);
			publish("roomba-enemy-2", roomba2EnemyX+","+roomba2EnemyY);
		}
	}
	
	//publish function to publish to the server
	//SERVER KEYS
	//X is 1 or 2
	//roomba-enemy-X
	//roomba-speed-X
	//roomba-collide-X
	//roomba-out-X
	public static void publish(String key,String thismessage){
		
		try {
			channel.basicPublish(EXCHANGE, key, null, thismessage.getBytes());
		} catch (IOException e) {
			System.out.println("error publishing!");
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
