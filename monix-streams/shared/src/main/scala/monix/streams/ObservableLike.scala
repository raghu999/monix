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

package monix.streams

import monix.execution.cancelables.BooleanCancelable
import monix.streams.ObservableLike.Operator
import monix.streams.internal.operators2._
import monix.streams.observers.Subscriber
import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds

/** Defines the available operations for observable-like instances.
  *
  * @define concatMergeDifference The difference between the `concat` operation
  *         and `merge`is that `concat` cares about the ordering of sequences
  *         (e.g. all items emitted by the first observable in the sequence
  *         will come before the elements emitted by the second observable),
  *         whereas `merge` doesn't care about that (elements get
  *         emitted as they come). Because of back-pressure applied to
  *         observables, `concat` is safe to use in all contexts, whereas
  *         `merge` requires buffering.
  * @define concatDescription Concatenates the sequence
  *         of observables emitted by the source into one observable,
  *         without any transformation.
  *
  *         You can combine the items emitted by multiple observables
  *         so that they act like a single sequence by using this
  *         operator.
  *
  *         $concatMergeDifference
  * @define delayErrorsDescription This version is reserving onError
  *         notifications until all of the observables complete and only
  *         then passing the issued errors(s) downstream. Note that
  *         the streamed error is a
  *         [[monix.streams.exceptions.CompositeException CompositeException]],
  *         since multiple errors from multiple streams can happen.
  * @define concatReturn an observable that emits items that are the result of
  *         flattening the items emitted by the observables emitted by the source
  */
abstract class ObservableLike[+A, Self[+T]] { self: Self[A] =>
  /** Transforms the source using the given operator. */
  def lift[B](operator: Operator[A,B]): Self[B]

  /** Given a [[monix.streams.Pipe Pipe]], transform
    * the source observable with it.
    */
  def transform[I >: A, B](pipe: Pipe[I,B]): Self[B] =
    self.lift(new TransformOperator(pipe))

  /** Returns a new observable that applies the given function
    * to each item emitted by the source and emits the result.
    */
  def map[B](f: A => B): Self[B] =
    self.lift(new MapOperator(f))

  /** Only emits those items for which the given predicate holds.
    *
    * @param p a function that evaluates the items emitted by the source
    *        returning `true` if they pass the filter
    *
    * @return a new observable that emits only those items in the source
    *         for which the filter evaluates as `true`
    */
  def filter(p: A => Boolean): Self[A] =
    self.lift(new FilterOperator(p))

  /** Applies the given partial function to the source
    * for each element for which the given partial function is defined.
    *
    * @param pf the function that filters and maps the source
    *
    * @return an observable that emits the transformed items by the
    *         given partial function
    */
  def collect[B](pf: PartialFunction[A, B]): Self[B] =
    self.lift(new CollectOperator(pf))

  /** Applies a function that you supply to each item emitted by the
    * source observable, where that function returns observables,
    * and then concatenating those resulting sequences and
    * emitting the results of this concatenation.
    *
    * $concatMergeDifference
    */
  def concatMap[B](f: A => Observable[B]): Self[B] =
    self.lift(new ConcatMapOperator[A,B](f, delayErrors = false))

  /** Applies a function that you supply to each item emitted by the
    * source observable, where that function returns sequences that
    * [[CanObserve can be observed]], and then concatenating those
    * resulting sequences and emitting the results of this concatenation.
    *
    * This version uses the [[CanObserve]] type-class for concatenating
    * asynchronous sequences that can be converted to observables.
    *
    * $concatMergeDifference
    */
  def concatMapF[B, F[_] : CanObserve](f: A => F[B]): Self[B] =
    concatMap(a => CanObserve[F].observable[B](f(a)))

  /** $concatDescription
    *
    * @return $concatReturn
    */
  def concat[B](implicit ev: A <:< Observable[B]): Self[B] =
    concatMap[B](x => x)

  /** Applies a function that you supply to each item emitted by the
    * source observable, where that function returns sequences that
    * [[CanObserve can be observed]], and then concatenating those
    * resulting sequences and emitting the results of this concatenation.
    *
    * Alias for [[concatMap]].
    *
    * $concatMergeDifference
    */
  def flatMap[B](f: A => Observable[B]): Self[B] =
    self.concatMap(f)

  /** Applies a function that you supply to each item emitted by the
    * source observable, where that function returns sequences that
    * [[CanObserve can be observed]], and then concatenating those
    * resulting sequences and emitting the results of this concatenation.
    *
    * This version uses the [[CanObserve]] type-class for concatenating
    * asynchronous sequences that can be converted to observables.
    *
    * Alias for [[concatMapF]].
    *
    * $concatMergeDifference
    */
  def flatMapF[B, F[_] : CanObserve](f: A => F[B]): Self[B] =
    self.concatMap(a => CanObserve[F].observable(f(a)))

  /** $concatDescription
    *
    * Alias for [[concat]].
    *
    * @return $concatReturn
    */
  def flatten[B](implicit ev: A <:< Observable[B]): Self[B] =
    concat

  /** Applies a function that you supply to each item emitted by the
    * source observable, where that function returns sequences
    * and then concatenating those resulting sequences and emitting the
    * results of this concatenation.
    *
    * $delayErrorsDescription
    *
    * @param f a function that, when applied to an item emitted by
    *        the source, returns an observable
    *
    * @return $concatReturn
    */
  def concatMapDelayError[B](f: A => Observable[B]): Self[B] =
    self.lift(new ConcatMapOperator[A,B](f, delayErrors = true))

  /** Applies a function that you supply to each item emitted by the
    * source observable, where that function returns sequences that
    * [[CanObserve can be observed]], and then concatenating those
    * resulting sequences and emitting the results of this concatenation.
    *
    * $delayErrorsDescription
    *
    * This version of [[concatMapDelayError]] uses the [[CanObserve]]
    * type-class for concatenating asynchronous sequences that can be
    * converted to observables.
    *
    * @param f a function that, when applied to an item emitted by
    *        the source, returns an observable
    *
    * @return $concatReturn
    */
  def concatMapDelayErrorF[B, F[_] : CanObserve](f: A => F[B]): Self[B] =
    concatMapDelayError(a => CanObserve[F].observable(f(a)))

  /** $concatDescription
    *
    * $delayErrorsDescription
    *
    * @return $concatReturn
    */
  def concatDelayError[B](implicit ev: A <:< Observable[B]): Self[B] =
    concatMapDelayError(x => x)

  /** Applies a function that you supply to each item emitted by the
    * source observable, where that function returns sequences
    * and then concatenating those resulting sequences and emitting the
    * results of this concatenation.
    *
    * It's an alias for [[concatMapDelayError]].
    *
    * @param f a function that, when applied to an item emitted by
    *        the source Observable, returns an Observable
    *
    * @return an Observable that emits the result of applying the
    *         transformation function to each item emitted by the
    *         source Observable and concatenating the results of the
    *         Observables obtained from this transformation.
    */
  def flatMapDelayError[B](f: A => Observable[B]): Self[B] =
    concatMapDelayError(f)

  /** Returns an observable that mirrors the behavior of the source,
    * unless the source is terminated with an `onError`, in which
    * case the streaming of events fallbacks to an observable
    * emitting a single element generated by the backup function.
    *
    * The created Observable mirrors the behavior of the source
    * in case the source does not end with an error or if the
    * thrown `Throwable` is not matched.
    *
    * @param pf - a partial function that matches errors with a
    *           backup element that is emitted when the source
    *           throws an error.
    */
  def onErrorRecover[B >: A](pf: PartialFunction[Throwable, B]): Self[B] =
    onErrorRecoverWith { case elem if pf.isDefinedAt(elem) => Observable.now(pf(elem)) }

  /** Returns an Observable that mirrors the behavior of the source,
    * unless the source is terminated with an `onError`, in which case
    * the streaming of events continues with the specified backup
    * sequence generated by the given partial function.
    *
    * The created Observable mirrors the behavior of the source in
    * case the source does not end with an error or if the thrown
    * `Throwable` is not matched.
    *
    * @param pf is a partial function that matches errors with a
    *        backup throwable that is subscribed when the source
    *        throws an error.
    */
  def onErrorRecoverWith[B >: A](pf: PartialFunction[Throwable, Observable[B]]): Self[B] =
    self.lift(new OnErrorRecoverWithOperator(pf))

  /** Selects the first `n` elements (from the start).
    *
    * @param  n the number of elements to take
    *
    * @return a new Observable that emits only the first
    *         `n` elements from the source
    */
  def take(n: Long): Self[A] =
    self.lift(new TakeLeftOperator(n))

  /** Creates a new observable that only emits the last `n` elements
    * emitted by the source.
    *
    * In case the source triggers an error, then the underlying
    * buffer gets dropped and the error gets emitted immediately.
    */
  def takeRight(n: Int): Self[A] =
    self.lift(new TakeRightOperator(n))

  /** Creates a new Observable that emits the events of the source, only
    * for the specified `timestamp`, after which it completes.
    *
    * @param timespan the window of time during which the new Observable
    *        is allowed to emit the events of the source
    */
  def take(timespan: FiniteDuration): Self[A] =
    self.lift(new TakeLeftByTimespanOperator(timespan))

  /** Takes longest prefix of elements that satisfy the given predicate
    * and returns a new Observable that emits those elements.
    */
  def takeWhile(p: A => Boolean): Self[A] =
    self.lift(new TakeByPredicateOperator(p))

  /** Takes longest prefix of elements that satisfy the given predicate
    * and returns a new Observable that emits those elements.
    */
  def takeWhileNotCanceled(c: BooleanCancelable): Self[A] =
    self.lift(new TakeWhileNotCanceledOperator(c))

  /** Drops the first `n` elements (from the start).
    *
    * @param n the number of elements to drop
    *
    * @return a new Observable that drops the first ''n'' elements
    *         emitted by the source
    */
  def drop(n: Int): Self[A] =
    self.lift(new DropLeftOperator(n))

  /** Creates a new observable that drops the events of the source, only
    * for the specified `timestamp` window.
    *
    * @param timespan the window of time during which the new observable
    *        must drop events emitted by the source
    */
  def dropByTimespan(timespan: FiniteDuration): Self[A] =
    self.lift(new DropByTimespanOperator(timespan))

  /** Drops the longest prefix of elements that satisfy the given
    * predicate and returns a new observable that emits the rest.
    */
  def dropWhile(p: A => Boolean): Self[A] =
    self.lift(new DropByPredicateOperator(p))

  /** Drops the longest prefix of elements that satisfy the given
    * function and returns a new observable that emits the rest. In
    * comparison with [[dropWhile]], this version accepts a function
    * that takes an additional parameter: the zero-based index of the
    * element.
    */
  def dropWhileWithIndex(p: (A, Int) => Boolean): Self[A] =
    self.lift(new DropByPredicateWithIndexOperator(p))

  /** Discard items emitted by the source until a second
    * observable emits an item or completes.
    *
    * If the `trigger` observable completes in error, then the
    * resulting observable will also end in error when it notices
    * it (next time an element is emitted).
    *
    * @param trigger the observable that has to emit an item before the
    *        source begin to be mirrored by the resulting observable
    */
  def dropUntil[F[_] : CanObserve](trigger: F[_]): Self[A] =
    self.lift(new DropUntilOperator(trigger))

  /** Periodically gather items emitted by an observable into bundles
    * and emit these bundles rather than emitting the items one at a
    * time. This version of `buffer` is emitting items once the
    * internal buffer has reached the given count.
    *
    * If the source observable completes, then the current buffer gets
    * signaled downstream. If the source triggers an error then the
    * current buffer is being dropped and the error gets propagated
    * immediately.
    *
    * @param count the maximum size of each buffer before it should
    *        be emitted
    */
  def buffer(count: Int): Self[Seq[A]] =
    bufferSkipped(count, count)

  /** Returns an observable that emits buffers of items it collects from
    * the source observable. The resulting observable emits buffers
    * every `skip` items, each containing `count` items.
    *
    * If the source observable completes, then the current buffer gets
    * signaled downstream. If the source triggers an error then the
    * current buffer is being dropped and the error gets propagated
    * immediately.
    *
    * For `count` and `skip` there are 3 possibilities:
    *
    *  1. in case `skip == count`, then there are no items dropped and
    *      no overlap, the call being equivalent to `buffer(count)`
    *  2. in case `skip < count`, then overlap between buffers
    *     happens, with the number of elements being repeated being
    *     `count - skip`
    *  3. in case `skip > count`, then `skip - count` elements start
    *     getting dropped between windows
    *
    * @param count the maximum size of each buffer before it should
    *        be emitted
    * @param skip how many items emitted by the source observable should
    *        be skipped before starting a new buffer. Note that when
    *        skip and count are equal, this is the same operation as
    *        `buffer(count)`
    */
  def bufferSkipped(count: Int, skip: Int): Self[Seq[A]] =
    self.lift(new BufferOperator(count, skip))

  /** Periodically gather items emitted by an observable into bundles
    * and emit these bundles rather than emitting the items one at a
    * time.
    *
    * This version of `buffer` emits a new bundle of items
    * periodically, every timespan amount of time, containing all
    * items emitted by the source Observable since the previous bundle
    * emission.
    *
    * If the source observable completes, then the current buffer gets
    * signaled downstream. If the source triggers an error then the
    * current buffer is being dropped and the error gets propagated
    * immediately.
    *
    * @param timespan the interval of time at which it should emit
    *        the buffered bundle
    */
  def bufferTimed(timespan: FiniteDuration) =
    bufferTimedOrCounted(timespan, 0)

  /** Periodically gather items emitted by an observable into bundles
    * and emit these bundles rather than emitting the items one at a
    * time.
    *
    * The resulting observable emits connected, non-overlapping
    * buffers, each of a fixed duration specified by the `timespan`
    * argument or a maximum size specified by the `maxSize` argument
    * (whichever is reached first).
    *
    * If the source observable completes, then the current buffer gets
    * signaled downstream. If the source triggers an error then the
    * current buffer is being dropped and the error gets propagated
    * immediately.
    *
    * @param timespan the interval of time at which it should emit
    *        the buffered bundle
    * @param maxSize is the maximum bundle size
    */
  def bufferTimedOrCounted(timespan: FiniteDuration, maxSize: Int): Self[Seq[A]] =
    self.lift(new BufferTimedOperator(timespan, maxSize))
}

object ObservableLike {
  /** An `Operator` is a function for transforming observers,
    * that can be used for lifting observables.
    */
  type Operator[-I,+O] = Subscriber[O] => Subscriber[I]
}