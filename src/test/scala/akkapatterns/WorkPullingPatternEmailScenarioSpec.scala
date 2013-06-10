package akkapatterns

import org.mockito.Matchers._
import WorkPullingPattern._
import akka.testkit.TestActorRef
import akka.actor.Props
import akka.testkit.TestProbe
import scala.concurrent.duration._
import akka.actor.ActorRef
import akka.actor.Actor
import org.slf4j.Logger
import akka.actor.PoisonPill
import basic.AkkaSpec
import scala.concurrent.Future
import akka.pattern.ask

/**
 * sample usage for work pulling pattern as described at
 *  http://www.michaelpollmeier.com/akka-work-pulling-pattern/
 */
class WorkPullingPatternEmailScenarioSpec extends AkkaSpec {
  type Work = String

  describe("email scenario") {
    val masterName = "emailCoordinator"
    val master = system.actorOf(Props[Master[Work]], masterName)

    case class Email(context: Work)
    case object SendResult
    trait MailServer { def send(email: Email) }
    val mailserver = mock[MailServer]

    val emailCreator = system.actorOf(Props(new Actor {
      def receive = {
        case context: Work ⇒ sender ! Email(context)
      }
    }))

    val emailSender = system.actorOf(Props(new Actor {
      def receive = {
        case email: Email ⇒
          mailserver.send(email)
          sender ! SendResult
      }
    }))

    // our worker implementation
    val emailCoordinator = system.actorOf(Props(new Worker[Work](master) {
      def doWork(context: Work): Future[_] =
        for {
          email ← emailCreator ? context
          result ← emailSender ? email
        } yield result
    }))

    it("sends out all the email") {
      val emailContexts = List("context1", "context2", "context3")
      val epic = new Epic[Work] { override val iterator = emailContexts.iterator }
      master ! epic

      val expectedEmails = emailContexts map { Email(_) }
      Thread.sleep(100)
      expectedEmails foreach { verify(mailserver).send(_) }
    }
  }
}

