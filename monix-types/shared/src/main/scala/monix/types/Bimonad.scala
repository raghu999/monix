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

package monix.types

/** A shim for the `Bimonad` type-class,
  * to be supplied by libraries such as Cats or Scalaz.
  */
trait Bimonad[F[_]] extends Monad[F] with Comonad[F]

object Bimonad {
  @inline def apply[F[_]](implicit F: Bimonad[F]): Bimonad[F] = F
}
