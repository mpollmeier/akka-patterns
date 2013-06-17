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
import scala.concurrent.Promise
import scala.reflect.ClassTag

class WorkPullingPatternSpec extends AkkaSpec {
  val logger = mock[Logger]

  def newEpic[T](work: T) = new Epic[T] { override def iterator = Seq(work).iterator }
  def newEpic[T](work: Seq[T]) = new Epic[T] { override def iterator = work.iterator }

  def newMaster[T] = TestActorRef[Master[T]](Props(new Master { override val log = logger }))
  def newMasterWithWorker[T] = {
    val master = newMaster
    master.underlyingActor.workers += TestProbe().ref
    master
  }

  def newWorker(master: ActorRef): ActorRef = TestActorRef[Worker[String]](Props(new Worker[String](master) {
    def doWork(work: String): Future[_] = Future {
      println(s"working really hard for: [$work]")
    }
  }))

  describe("Master") {
    it("should log an error if it receives an epic but doesn't have any workers") {
      val master = newMaster
      val list = List(Work("some work"))
      master ! newEpic("some work")

      verify(logger).error(anyString)
    }

    it("registers workers") {
      val dummy = TestActorRef(new Actor { def receive = { case _ ⇒ } })
      val master = newMaster
      val worker: ActorRef = newWorker(dummy)
      master ! RegisterWorker(worker)

      master.underlyingActor.workers should contain(worker)
    }

    it("informs all workers that work is available when it receives some work") {
      val master = newMaster
      val workers = Seq(TestProbe(), TestProbe())
      workers foreach { worker ⇒ master ! RegisterWorker(worker.ref) }

      master ! newEpic("someWork")
      workers.foreach {
        _.expectMsg(WorkAvailable)
      }
    }

    it("responds with `CurrentlyBusy` if it's currently working on an epic") {
      val master = newMasterWithWorker[String]
      master ! newEpic("someWork")
      expectNoMsg(500 millis)
      master ! newEpic("some other epic")
      expectMsg(CurrentlyBusy)
    }

    it("should reset currentEpic if there's no more work") {
      val master = newMasterWithWorker[String]
      val epicWithoutActualWork = newEpic(Seq[String]())

      master ! epicWithoutActualWork
      master ! GimmeWork
      master.underlyingActor.currentEpic should be(None)
    }

    it("tells worker next piece of work when they ask for it") {
      val master = newMasterWithWorker[String]
      val someWork = "some work"
      master ! newEpic(someWork)

      master ! GimmeWork
      expectMsg(Work(someWork))
    }

    it("forgets about workers who died") {
      val master = newMaster
      val worker = TestProbe()
      master ! RegisterWorker(worker.ref)
      master.underlyingActor.workers.size should be(1)

      worker.ref ! PoisonPill
      master.underlyingActor.workers.size should be(0)
    }

  }

  describe("Worker") {

    it("registers with a master when created") {
      val master = newMaster
      val worker = newWorker(master)

      master.underlyingActor.workers should contain(worker)
    }

    it("asks for work when created (to ensure sure that a restarted actor will be busy straight away)") {
      val worker = newWorker(testActor)
      fishForMessage() {
        case GimmeWork ⇒ true
        case _         ⇒ false
      }
    }

    it("should request work from the master when there is work available") {
      ignoreMsg { case _ ⇒ true }
      val worker = newWorker(testActor)

      ignoreNoMsg
      worker ! WorkAvailable
      expectMsg(GimmeWork)
    }

    it("calls doWork when it receives work") {
      val master = newMaster
      val work = "some work"
      var executedWork: String = null

      val worker = TestActorRef[Worker[String]](Props(new Worker[String](master) {
        def doWork(work: String): Future[_] =
          Future { executedWork = work }
      }))

      worker ! Work("some work")
      executedWork should be(work)
    }

    it("should ask for more work once a piece of work is done") {
      ignoreMsg { case _ ⇒ true }
      val worker = newWorker(testActor)

      ignoreNoMsg
      worker ! Work("")
      expectMsg(GimmeWork)
    }

  }
}

