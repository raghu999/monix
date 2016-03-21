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

package monix.reactive.observers

import monix.execution.Ack
import monix.execution.Ack.{Cancel, Continue}
import scala.concurrent.{Future, Promise}
import scala.util.Try
import scala.util.control.NonFatal


/** A safe subscriber safe guards subscriber implementations, such that:
  *
  *  - the `onComplete` and `onError` signals are back-pressured
  *  - errors triggered by downstream observers are caught and logged,
  *    while the upstream gets an `Ack.Cancel`, to stop sending events
  *  - once an `onError` or `onComplete` was emitted, the observer no longer accepts
  *    `onNext` events, ensuring that the grammar is respected
  *  - if downstream signals a `Cancel`, the observer no longer accepts any events,
  *    ensuring that the grammar is respected
  */
final class SafeSubscriber[-T] private (subscriber: Subscriber[T])
  extends Subscriber[T] {

  implicit val scheduler = subscriber.scheduler
  private[this] var isDone = false
  private[this] var ack: Future[Ack] = Continue

  def onNext(elem: T): Future[Ack] = {
    if (!isDone) {
      ack = try {
        flattenAndCatchFailures(subscriber.onNext(elem))
      } catch {
        case NonFatal(ex) =>
          onError(ex)
          Cancel
      }

      ack
    } else {
      Cancel
    }
  }

  def onError(ex: Throwable): Unit =
    ack.syncOnContinue(signalError(ex))

  def onComplete(): Unit =
    ack.syncOnContinue {
      if (!isDone) {
        isDone = true

        try subscriber.onComplete() catch {
          case NonFatal(err) =>
            scheduler.reportFailure(err)
        }
      }
    }

  private def flattenAndCatchFailures(ack: Future[Ack]): Future[Ack] = {
    // Fast path.
    if (ack eq Continue) Continue
    else if (ack.isCompleted)
      handleFailure(ack.value.get)
    else {
      // Protecting against asynchronous errors
      val p = Promise[Ack]()
      ack.onComplete { result => p.success(handleFailure(result)) }
      p.future
    }
  }

  private def signalError(ex: Throwable): Unit =
    if (!isDone) {
      isDone = true

      try subscriber.onError(ex) catch {
        case NonFatal(err) =>
          scheduler.reportFailure(err)
      }
    }

  private def handleFailure(value: Try[Ack]): Ack =
    try {
      val ack = value.get
      if (ack eq Cancel) isDone = true
      ack
    } catch {
      case NonFatal(ex) =>
        signalError(value.failed.get)
        Cancel
    }
}

object SafeSubscriber {
  /**
    * Wraps an Observer instance into a SafeObserver.
    */
  def apply[T](subscriber: Subscriber[T]): SafeSubscriber[T] =
    subscriber match {
      case ref: SafeSubscriber[_] => ref.asInstanceOf[SafeSubscriber[T]]
      case _ => new SafeSubscriber[T](subscriber)
    }
}