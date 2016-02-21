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

package monix.streams.internal.operators

import monix.streams.Observable
import scala.concurrent.duration.Duration

object CombineLatest4Suite extends BaseOperatorSuite {
  def createObservable(sc: Int) = Some {
    val sourceCount = 10
    val o1 = Observable.now(1)
    val o2 = Observable.now(2)
    val o3 = Observable.now(3)
    val o4 = Observable.range(0, sourceCount)
    val o = Observable.combineLatest4(o1,o2,o3,o4)(_+_+_+_)

    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def count(sourceCount: Int) = sourceCount
  def sum(sourceCount: Int) = sourceCount * (sourceCount + 1) / 2 +
    (5 * sourceCount)

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val o1 = Observable.now(1)
    val o2 = Observable.now(2)
    val o3 = Observable.now(3)
    val flawed = createObservableEndingInError(Observable.range(0, sourceCount), ex)
    val o = Observable.combineLatest4(o1,o2,o3, flawed)(_+_+_+_)

    Sample(o, count(sourceCount-1), sum(sourceCount-1), waitFirst, waitNext)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
  def waitFirst = Duration.Zero
  def waitNext = Duration.Zero
}