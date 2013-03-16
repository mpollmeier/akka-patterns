package akkapatterns

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.dispatch.ExecutionContexts
import java.util.concurrent.Executors
import scala.concurrent.Await
import org.scalatest.FunSuite
import basic.MovioSpec

/** @see http://doc.akka.io/docs/akka/snapshot/scala/futures.html */
class FutureCompositionSpec extends MovioSpec {
  describe("Future composition") {
    it("runs in parallel to other futures") {
      def sleep50m = {
        Thread.sleep(50)
        1
      }
      val f1 = Future { sleep50m }
      val f2 = Future { sleep50m }

      val futureResult = for {
        r1 ← f1
        r2 ← f2
      } yield r1 + r2

      Await.result(futureResult, 60 millis) should be(2)
    }

    it("can use another future's result") {
      val futureResult = for {
        f1 ← Future { 2 }
        f2 ← Future { f1 * 2 }
      } yield f2

      Await.result(futureResult, 50 millis) should be(4)
    }

    it("zips multiple results") {
      val f1 = Future { 1 }
      val f2 = Future { 2 }
      val futureResult = f1.zip(f2).map {
        case (i, j) ⇒ i + j
      }

      Await.result(futureResult, 50 millis) should be(3)
    }

    it("traverses a collection and applies another function in the future") {
      def square(i: Int) = Future { i * i }

      val futureResult = for {
        numbers ← Future { Seq(1, 2) }
        squares ← Future.traverse(numbers)(square)
      } yield squares

      Await.result(futureResult, 50 millis) should be(Seq(1, 4))
    }
  }

}

