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

import java.util.concurrent.Callable
import monix.streams.Observable
import monix.streams.observers.Subscriber
import scala.util.control.NonFatal

/** An observable that evaluates the given callable
  * and emits its result.
  */
private[streams] final class CallableAsObservable[A](cb: Callable[A])
  extends Observable[A] {

  def unsafeSubscribeFn(subscriber: Subscriber[A]): Unit = {
    try {
      subscriber.onNext(cb.call())
      // No need to do back-pressure
      subscriber.onComplete()
    } catch {
      case NonFatal(ex) =>
        try subscriber.onError(ex) catch {
          case NonFatal(err) =>
            val s = subscriber.scheduler
            s.reportFailure(ex)
            s.reportFailure(err)
        }
    }
  }
}