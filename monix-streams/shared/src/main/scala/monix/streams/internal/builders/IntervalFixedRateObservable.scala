/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.streams.internal.builders

import java.util.concurrent.TimeUnit

import monix.execution.Ack
import monix.execution.Ack.{Cancel, Continue}
import monix.streams.Observable
import monix.streams.observers.Subscriber

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

private[streams] final class IntervalFixedRateObservable
  (initialDelay: FiniteDuration, period: FiniteDuration)
  extends Observable[Long]{

  override def unsafeSubscribeFn(subscriber: Subscriber[Long]): Unit = {
    import subscriber.{scheduler => s}
    val o = subscriber

    val runnable = new Runnable { self =>
      private[this] val periodMillis = period.toMillis
      private[this] var counter = 0L
      private[this] var startedAt = 0L

      def scheduleNext(): Unit = {
        counter += 1
        val delay = {
          val durationMillis = s.currentTimeMillis() - startedAt
          val d = periodMillis - durationMillis
          if (d >= 0L) d else 0L
        }

        s.scheduleOnce(delay, TimeUnit.MILLISECONDS, self)
      }

      def asyncScheduleNext(r: Future[Ack]): Unit =
        r.onComplete {
          case Success(ack) =>
            if (ack == Continue) scheduleNext()
          case Failure(ex) =>
            s.reportFailure(ex)
        }

      def run(): Unit = {
        startedAt = s.currentTimeMillis()
        val ack = o.onNext(counter)

        if (ack == Continue)
          scheduleNext()
        else if (ack != Cancel)
          asyncScheduleNext(ack)
      }
    }

    if (initialDelay.length <= 0)
      runnable.run()
    else
      s.scheduleOnce(initialDelay.length, initialDelay.unit, runnable)
  }
}