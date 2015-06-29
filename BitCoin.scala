
import java.net.InetAddress
import java.security.MessageDigest
import com.typesafe.config.ConfigFactory
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.routing.RoundRobinRouter

object BitMain extends App{
  
  override def main(args: Array[String]): Unit ={
    
   val k: Int = args(0).toInt
   println(k)
   val nrOfActors=4
   val hostIP = InetAddress.getLocalHost.getHostAddress()
   val config = ConfigFactory.parseString("""
	    akka {
			actor {
				provider = "akka.remote.RemoteActorRefProvider"
			}
			
			remote {
				enabled-transports = ["akka.remote.netty.tcp"]
				
				netty.tcp {
					hostname = """ + hostIP + """
					port = 5150
				}
			}
		}
	""")
	
	val bitCoinServer = ActorSystem("bitCoinServer", ConfigFactory.load(config))
	val bitcoin = bitCoinServer.actorOf(Props(new BitCoin(bitCoinServer,k)), "bitcoin")
	
  }  
}

object BitCoin{
  
  trait MessageType
  case object Calculate
  case class SlaveWork(end : Int, inc : Int, start: Long)
  case class resultMap(sPoints : Map[String,String]) extends MessageType
  case class StartWork(startTimer: Long, masterRef: ActorRef,k:Int)
  case class FinalCoins(listCoins: Map[String,String]) extends MessageType
  case object DoneWork extends MessageType
  
}

 class BitCoin(bitCoinServer: ActorSystem,k:Int) extends Actor{
    import BitCoin._
  
    val startTimer: Long = System.currentTimeMillis
    
    val master = bitCoinServer.actorOf(Props(new Master(k, 4, self, startTimer)), "Master")
    master ! Calculate 
   
    var noOfCoins: Int = _
    def receive = {
      
    case "Connection Done" =>
      println("Connection Established")
      println(sender)
      sender ! StartWork(startTimer, master,k)
      
    case FinalCoins(listCoins) =>
      	
      println("BITCOINS :")
	 
      listCoins.keys.foreach{ i =>  
      
        print( "Input= " + i )
        println("   BitCoin= " + listCoins(i) )
        noOfCoins+=1}
      	var duration: Long = (System.currentTimeMillis() - startTimer)
        println("\nTime taken by the CPU : "+duration+" milliseconds")
        println("No. of bitcoins:" + noOfCoins)
	    context.stop(self)
	    System.exit(0)
   }
 
  }

class Master(k: Int, nrOfActors: Int, bitCoin: ActorRef, startTimer: Long) extends Actor {
  println("Master created")
  var nrOfResults: Int = _
  var noWorkerDone: Int = 0
   
  var listPoints:Map[String,String] = Map()
  val startNewTimer: Long = System.currentTimeMillis
  val slave = context.actorOf(
  Props[Slave].withRouter(RoundRobinRouter(nrOfActors)), name = "slave")
   
  def receive = {

    case BitCoin.Calculate =>    
     println("Bitcoin mining started...")
     for(i <- 1 to nrOfActors)
         {
    	 // val slave = context.actorOf(Props[Slave])
    	   slave ! BitCoin.SlaveWork(k,i+5, startTimer)
         }
    
    case BitCoin.resultMap(sPoints)  =>
      		 sPoints foreach {case (key, value) => listPoints+= key -> value 
	     }
      		//if ((System.currentTimeMillis()-startNewTimer)<300000)
      		//{ 
      		//sender ! BitCoin.SlaveWork(k,k+7,startNewTimer)
      		// }
      	
    case BitCoin.DoneWork  =>
        noWorkerDone+= 1
	     if(noWorkerDone == nrOfActors){
	       println("final result")
	       bitCoin ! BitCoin.FinalCoins(listPoints)
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
     
  while(System.currentTimeMillis()-start <= 30000)
     { 
     var a=1
     var b=1
     var zeros=""
     
     for(b<-1 to k)
     {
       zeros=zeros+"0"
     }
     
     for(a<-1 to 500000)
     { 
     var data=randomAlphaNumericString(5+j)
     var crypthash= hex_digest(data)
     counter=counter+1
     if(crypthash.startsWith(zeros)==true)
       {
        sPoints += (data-> crypthash)
        
        //counter=counter+1;
       }
    }
  }
     println("counter="+counter)
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
  
   "manalisharma" + sb.toString
  } 
    
    def hex_digest(s: String): String = {

    sha.digest(s.getBytes)
    .foldLeft("")((s: String, b: Byte) => s +
                  Character.forDigit((b & 0xf0) >> 4, 16) +
                  Character.forDigit(b & 0x0f, 16))
   }
  
  
  def receive = {
    case BitCoin.SlaveWork(k, j, start) =>
      searchCoins(k, j, start)
        sender ! BitCoin.DoneWork
        context.stop(self)
  }
}




