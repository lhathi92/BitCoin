import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.ActorRef
import scala.util.control.Breaks
import scala.collection.mutable.ArrayBuffer
import com.typesafe.config.ConfigFactory
import akka.actor.AddressFromURIString
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.net.InetAddress
import java.security.MessageDigest
import akka.routing.RoundRobinRouter

object BitCoinRemote extends App{
  override def main(args: Array[String]): Unit={
  
  val hostIP = InetAddress.getLocalHost.getHostAddress()
	println(hostIP)
	val RemoteIP:String=args(0)
	println("Remote="+RemoteIP)
	
  val config = ConfigFactory.parseString("""
    akka {
       actor {
           provider = "akka.remote.RemoteActorRefProvider"
             }
       remote {
           enabled-transports = ["akka.remote.netty.tcp"]
       netty.tcp {
           hostname = """ + hostIP + """
           port = 0
                 }
             }
        }
   """) 
   
    val system = ActorSystem("Bitcoins", ConfigFactory.load(config))
    val duration = Duration(90000, SECONDS)
    val future = system.actorSelection("akka.tcp://bitCoinServer@"+RemoteIP+":5150/user/bitcoin").resolveOne(duration)
    val bitcoinremote = system.actorOf(Props(new BitCoinRemote(future, system)), "bitcoinremote")
  }
}
object BitCoin {
  
  trait MessageType
  case object Calculate
  case class SlaveWork(end : Int, inc : Int, start: Long)
  case class resultMap(sPoints : Map[String,String]) extends MessageType
  case class StartWork(startTimer: Long, masterRef: ActorRef, k:Int)
  case class FinalCoins(listCoins: Map[String,String]) extends MessageType
  case object DoneWork extends MessageType
  
}
class BitCoinRemote (future: scala.concurrent.Future[akka.actor.ActorRef], remoteSystem: ActorSystem) extends Actor{
 import BitCoin._ 
 var server: ActorRef = _
    
    future.onComplete {
      case Success(value) => 
        value ! "Connection Done"
        server = value
        println("Connection done")
      case Failure(e) => e.printStackTrace
    }
     
    
  def receive = {
    
    case StartWork(startTimer, serverMaster,k) =>
      println(serverMaster)
     val master = remoteSystem.actorOf(Props(new Master(k,4, self, startTimer, serverMaster)), "Master")
     master ! Calculate 
     
    case DoneWork =>
      context.stop(self)
      context.system.shutdown
      System.exit(0)
   }
  
 }
class Master(k: Int, nrOfActors: Int, bitCoin: ActorRef, startTimer: Long, serverMaster: ActorRef) extends Actor{
  println("Master created")
  var nrOfResults: Int = _
  
  var noWorkerDone: Int = 0
  var listPoints:Map[String,String] = Map()
  val startNewTimer: Long = System.currentTimeMillis
 // val startTimer: Long = System.currentTimeMillis
  val slave = context.actorOf(
  Props[Slave].withRouter(RoundRobinRouter(nrOfActors)), name = "slave")
  
  def receive = {
    
   case BitCoin.Calculate =>    
     println("Bitcoin mining started at remote...")
     for(i <- 1 to nrOfActors)
         {
    	 // val slave = context.actorOf(Props[Slave])
    	  // println("nrOfActors"+i)
    	  //println("time1="+(System.currentTimeMillis()-startTimer))
    	   slave ! BitCoin.SlaveWork(k,i+5, startNewTimer)
         }
   case BitCoin.resultMap(sPoints)  =>
    // println("returning to server")
      		sPoints foreach {case (key, value) => listPoints+= key -> value 
	     }
      		//nrOfResults += 1
      		//if ((System.currentTimeMillis()-startTimer)<10000)
      		//{ 
      		//  println("callBack")
      		 // sender ! BitCoin.SlaveWork(k,k+7, startTimer)
      		//}
      		
     serverMaster ! BitCoin.resultMap(listPoints)
      	
    case BitCoin.DoneWork  => 
     
        noWorkerDone+= 1
	     if(noWorkerDone == nrOfActors){
	      // bitCoin ! BitCoin.FinalCoins(listPoints)
	    // bitCoin ! BitCoin.FinalCoins(listPoints)
	     bitCoin ! BitCoin.DoneWork
	       context.stop(self)
	       context.system.shutdown()
	     }
      		
  }
}
class Slave extends Actor {
  println("Slave created")
  var counter=0
  private val sha = MessageDigest.getInstance("SHA-256")
  var sPoints:Map[String,String] = Map()
  
  def searchCoins(k:Int, j:Int, start: Long)=
   {
     //println("entered Search coins"+k+" "+start)
     while(System.currentTimeMillis() - start <=30000)
     { 
    	var a=1
    	var b=1
    	var zeros=""
    	for(b<-1 to k)
    	{
    		zeros=zeros+"0"
    	}
    	for(a<-1 to 500)
    	{ 
    		var data=randomAlphaNumericString(5+j)
    		var crypthash= hex_digest(data)
    		if(crypthash.startsWith(zeros)==true)
    		{
    			sPoints += (data-> crypthash)
    			//println("data="+data+" crypthash="+crypthash)
    			counter=counter+1;
    		}
    		
    	if(sPoints.size == 5)
         {
            sender ! BitCoin.resultMap(sPoints)
            sPoints = scala.collection.immutable.Map.empty
         }
    	}
    	
     }
     
    sender ! BitCoin.resultMap(sPoints)
   }
    
 def randomAlphaNumericString(line: Int): String = {
  
    val chars = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
    randomStringFromCharList(line, chars)
} 
   
  def randomStringFromCharList(line: Int, chars: Seq[Char]): String = {
    val sb = new StringBuilder
    for (i <- 1 to line) {
      val randomNum = util.Random.nextInt(chars.length)
      sb.append(chars(randomNum))
    }
   // final.append(sb).toString
   "l.hathi" + sb.toString
  } 
    
    def hex_digest(s: String): String = {
    sha.digest(s.getBytes)
    .foldLeft("")((s: String, b: Byte) => s +
                  Character.forDigit((b & 0xf0) >> 4, 16) +
                  Character.forDigit(b & 0x0f, 16))
   }
  
  
  def receive = {
    case BitCoin.SlaveWork(k, j, start) =>
      searchCoins(k,j, start)
      sender ! BitCoin.DoneWork
        context.stop(self)
  }
}