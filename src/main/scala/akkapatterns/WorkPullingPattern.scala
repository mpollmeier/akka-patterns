package akkapatterns

import akka.actor.Actor
import scala.collection.mutable
import akka.actor.ActorRef
import WorkPullingPattern._
import scala.collection.IterableLike
import scala.reflect.ClassTag
import org.slf4j.LoggerFactory
import akka.actor.Terminated
import scala.concurrent.Future

object WorkPullingPattern {
  sealed trait Message
  trait Epic[T] extends Iterable[T] //used by master to create work (in a streaming way)
  case object GimmeWork extends Message
  case object CurrentlyBusy extends Message
  case object WorkAvailable extends Message
  case class RegisterWorker(worker: ActorRef) extends Message
  case class Work[T](work: T) extends Message
}

class Master[T] extends Actor {
  val log = LoggerFactory.getLogger(getClass)
  val workers = mutable.Set.empty[ActorRef]
  var currentEpic: Option[Epic[T]] = None

  def receive = {
    case epic: Epic[T] ⇒
      if (currentEpic.isDefined)
        sender ! CurrentlyBusy
      else if (workers.isEmpty)
        log.error("Got work but there are no workers registered.")
      else {
        currentEpic = Some(epic)
        workers foreach { _ ! WorkAvailable }
      }

    case RegisterWorker(worker) ⇒
      log.info(s"worker $worker registered")
      context.watch(worker)
      workers += worker

    case Terminated(worker) ⇒
      log.info(s"worker $worker died - taking off the set of workers")
      workers.remove(worker)

    case GimmeWork ⇒ currentEpic match {
      case None ⇒
        log.info("workers asked for work but we've no more work to do")
      case Some(epic) ⇒
        val iter = epic.iterator
        if (iter.hasNext)
          sender ! Work(iter.next)
        else {
          log.info(s"done with current epic $epic")
          currentEpic = None
        }
    }
  }
}

abstract class Worker[T: ClassTag](val master: ActorRef) extends Actor {
  implicit val ec = context.dispatcher

  override def preStart {
    master ! RegisterWorker(self)
    master ! GimmeWork
  }

  def receive = {
    case WorkAvailable ⇒
      master ! GimmeWork
    case Work(work: T) ⇒
      // haven't found a nice way to get rid of that warning
      // looks like we can't suppress the erasure warning: http://stackoverflow.com/questions/3506370/is-there-an-equivalent-to-suppresswarnings-in-scala
      doWork(work) onComplete { case _ ⇒ master ! GimmeWork }
  }

  def doWork(work: T): Future[_]
}
