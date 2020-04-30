/*
 * Copyright 2012-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package laika.config

import java.util.Date

import laika.ast.Path
import laika.time.PlatformDateFormat

/** A type class that can encode a value of type T as a ConfigValue.
  * 
  * @author Jens Halm
  */
trait ConfigEncoder[-T] {
  def apply(value: T): ConfigValue
}

/** Companion containing default encoder implementations for simple values and Seq's.
  */
object ConfigEncoder {

  implicit val string: ConfigEncoder[String] = new ConfigEncoder[String] {
    def apply (value: String) = StringValue(value)
  }

  implicit val int: ConfigEncoder[Int] = new ConfigEncoder[Int] {
    def apply (value: Int) = LongValue(value.toLong)
  }

  implicit val double: ConfigEncoder[Double] = new ConfigEncoder[Double] {
    def apply (value: Double) = DoubleValue(value)
  }

  implicit val path: ConfigEncoder[Path] = new ConfigEncoder[Path] {
    def apply (value: Path) = StringValue(value.toString)
  }

  implicit val date: ConfigEncoder[Date] = new ConfigEncoder[Date] {
    def apply (value: Date) = StringValue(PlatformDateFormat.format(value, "yyyy-MM-dd'T'HH:mm:ss").getOrElse(value.toString))
  }
  
  implicit val configValue: ConfigEncoder[ConfigValue] = new ConfigEncoder[ConfigValue] {
    def apply (value: ConfigValue) = value
  }

  implicit def seq[T] (implicit elementEncoder: ConfigEncoder[T]): ConfigEncoder[Seq[T]] = new ConfigEncoder[Seq[T]] {
    def apply (value: Seq[T]) = ArrayValue(value.map(elementEncoder.apply))
  }
  
  def apply[T] (f: T => ConfigValue): ConfigEncoder[T] = new ConfigEncoder[T] {
    def apply (value: T) = f(value)
  }
  
  class ObjectBuilder (delegate: ConfigBuilder) {

    /** Returns a new builder instance adding the specified value to the existing set of values.
      */
    def withValue[T](key: String, value: T)(implicit encoder: ConfigEncoder[T]): ObjectBuilder =
      withValue(Key.parse(key), value)

    /** Returns a new builder instance adding the specified value to the existing set of values.
      */
    def withValue[T](key: Key, value: T)(implicit encoder: ConfigEncoder[T]): ObjectBuilder =
      new ObjectBuilder(delegate.withValue(key, value))

    /** Returns a new builder instance adding the specified value to the existing set of values if it is non-empty.
      */
    def withValue[T](key: String, value: Option[T])(implicit encoder: ConfigEncoder[T]): ObjectBuilder =
      value.fold(this)(withValue(Key.parse(key), _))

    /** Returns a new builder instance adding the specified value to the existing set of values if it is non-empty.
      */
    def withValue[T](key: Key, value: Option[T])(implicit encoder: ConfigEncoder[T]): ObjectBuilder =
      value.fold(this)(withValue(key, _))
    
    def build: ObjectValue = delegate.asObjectValue
    
  }
  
  object ObjectBuilder {
    val empty: ObjectBuilder = new ObjectBuilder(ConfigBuilder.empty)
  }
}
