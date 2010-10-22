package mainpackage;
import roombacomm.*;

public class RoombaControl {
	
	public RoombaCommSerial roombacomm;
	public String comPort;
	public String roombaKey;
	public boolean left;
	public boolean right;
	public int velocity;
	public int radius;
	public int turnConstant;
	public int leftFactor ;
	public int rightFactor;
	public int speedCap;
	public int prevSpeed;
	
	public RoombaControl(String portNumber){
		
		//Instantiate variables
		comPort = portNumber;
		velocity = 0;
		radius = 0;
		leftFactor = -450;
		rightFactor = 450;
		left = false;
		right = false;
		prevSpeed = 0;
		speedCap = 500;
		
		//Connect to roomba
		roombacomm = new RoombaCommSerial(true);
		roombacomm.controller = this;
		
		//print debug msg if cant connect
		if( !roombacomm.connect( portNumber ) ) {
			roombaPublish("startup", "ROOMBA_CONNECT_FAILED");
			System.out.println("Couldn't connect to "+portNumber);
            System.exit(1);
        }

	        
		// connect to port
		roombacomm.connect( portNumber );
	
		// startup the roomba
		System.out.println("starting up roomba at "+ comPort);
		roombacomm.startup();
		roombacomm.control();
		roombacomm.pause(50);
		roombacomm.reset();
		
		roombaPublish("startup", "ROOMBA_CONNECT_OK");
	}
	
	//Reset the roomba incase of wheelie
	public void resetRoomba(){
		roombacomm.startup();
		roombacomm.control();
		roombacomm.pause(50);
		roombacomm.reset();
	}

	
	//Function command the roomba
	public void roombaAction(String action){
		
		//LIST OF COMMANDS
		//----------------
		//DISCONNECT
		//ACCELERATE
		//DECELERATE
		//BOOST
		//MAX_SPEED
		//NORMAL_SPEED
		//STOP
		//TURN_LEFT
		//TURN_RIGHT
		//SLOW_DOWN
		//SPIN
		
		//Set direction to straight ahead
		if(!action.contains("TURN")){
			radius = 0x8000;
		}
		
		if(action.equals("RESET")){
			resetRoomba();
		}
		
		//Action to dc roomba
		if(action.equals("DISCONNECT")){
			roombacomm.stop();
			roombacomm.disconnect();
		}
		
		//Action to accelerate forward
		//Forward velocity is increased at a constant rate of 11mm/s
		//and a variable increase at set 10% of the velocity
		//Will have to cancel out any backward motion first before accelerating forward
		//MAX roomba speed is at 500mm/s
		if(action.equals("ACCELERATE")){
			
			//forward velocity caps at speed cap
			if(velocity <= speedCap){
				velocity += 11;
				
				if((0.05*velocity) <= 0 ){
					velocity -= (int)(0.05*velocity);
				}else{
					velocity += (int)(0.05*velocity);
				}
			}
		}
		
		//Action to move accelerate backwards
		//will have to cancel out the forward velocity first before
		//roomba can move backwards
		if(action.equals("DECELERATE")){
			
			if(velocity >= -speedCap){
				velocity -= 11;
				
				if((0.05*velocity) >= 0 ){
					velocity -= (int)(0.05*velocity);
				}else{
					velocity += (int)(0.05*velocity);
				}
			}
		}
		
		
		//Nitro pack boasts increased acceleration by as much as
		//an additional 20%
		if(action.equals("BOOST")){
			
			//forward velocity caps at 400mm/s
			if(velocity <= speedCap){
				velocity +=11;
				velocity += (int)(0.3*velocity);
			}
		}
		
		//Sets max speed cap to 500;
		if(action.equals("MAX_SPEED")){
			speedCap = 500;
		}
		
		//Set speed cap to 400;
		if(action.equals("NORMAL_SPEED")){
			speedCap = 400;
		}
		
		//Stop the roomba
		if(action.equals("STOP")){
			velocity = 0;
			roombacomm.stop();
		}
		
		//TURNING
		//turning has 2 components
		//a CONSTANT turnConstant to regulate the initial radius (set at 1 roomba unit)
		//And the speed that the Roomba is moving at
		//The larger the speed, the larger the radius of the turn
		//Turning will also slow down the roomba at a rate of 5% per call
		//5% Might be too big, need to test
		
		//Turns left
		if(action.equals("TURN_LEFT")){
			
			if(left){
				leftFactor +=2;
			}else{
				left = true;
				leftFactor = -450;
			}
			
			radius = leftFactor;			
		}else{
			left = false;
		}
		
		//turns right
		if(action.equals("TURN_RIGHT")){
			
			if(right){
				rightFactor -=2;
			}else{
				right = true;
				rightFactor = 450;
			}
			
			radius = rightFactor;			
		}else{
			right = false;
		}
		
		//Slows down the roomba
		if(action.equals("SLOW_DOWN")){
			
			if(Math.abs(velocity) <= 10){
				
				velocity = 0;
			}
			
			if(velocity>0){
				velocity -=2;
				roombaAction("DECELERATE");
			}
			
			if(velocity<0){
				velocity +=2;
				roombaAction("ACCELERATE");
			}
		}
		
		if(action.equals("STRAIGHT")){
			roombacomm.speed = 300;
			roombacomm.goStraight(600);
			roombacomm.speed = 0;
		}
		
		if(velocity < -500){
			velocity = -500;
		}
		
		if(velocity > 500){
			velocity = 500;
		}
		
		
		//Rooomba command to move;
		if(!action.contains("SPIN")){
			roombacomm.drive(-velocity, radius);
		}else{
			
			if(action.equals("SPIN")){
				
				roombacomm.speed = 500;
				roombacomm.spinRight(180);
				roombacomm.speed = velocity;
				
			}else{
				
				velocity = 0;				
			}
		}
		
		if(velocity != prevSpeed){
			//publish roomba speed
			roombaPublish("speed", Integer.toString(velocity));
		}
		
		prevSpeed = velocity;
	}
	
	//Publish function to publish messages to server
	public void roombaPublish(String type, String thismessage){
		
		//set routing key
		String routingkey = "roomba-"+type;
		
		if(comPort.equals(RoyalRoombaManager.port1)){
			routingkey +="-1";
		}else{
			routingkey +="-2";
		}
		
		//publish
		RoyalRoombaManager.publish(routingkey, thismessage);
	}
}