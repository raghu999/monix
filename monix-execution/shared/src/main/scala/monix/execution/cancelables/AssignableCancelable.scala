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

package monix.execution.cancelables
import monix.execution.Cancelable

/** Represents a class of cancelables that can hold
  * an internal reference to another cancelable (and thus
  * has to support the assignment operator).
  *
  * Examples are the [[MultiAssignmentCancelable]] and the
  * [[SingleAssignmentCancelable]].
  *
  * On assignment, if this cancelable is already
  * canceled, then no assignment should happen and the update
  * reference should be canceled as well.
  */
trait AssignableCancelable extends BooleanCancelable {
  /** Updates the internal reference of this assignable cancelable
    * to the given value.
    *
    * If this cancelable is already canceled, then `value` is
    * going to be canceled on assignment as well.
    *
    * @return `this`
    */
  def `:=`(value: Cancelable): this.type
}

object AssignableCancelable {
  /** Interface for [[AssignableCancelable]] types that can be
    * assigned multiple times.
    */
  trait Multi extends AssignableCancelable {
    /** An ordered update is an update with an order attached and if
      * the currently stored reference has on order that is greater
      * than the update, then the update is ignored.
      */
    def orderedUpdate(value: Cancelable, order: Long): this.type
  }

  /** Builds a [[MultiAssignmentCancelable]] */
  def multi(initial: Cancelable = Cancelable.empty): AssignableCancelable =
    MultiAssignmentCancelable(initial)

  /** Builds a [[SingleAssignmentCancelable]] */
  def single(): AssignableCancelable =
    SingleAssignmentCancelable()
}
