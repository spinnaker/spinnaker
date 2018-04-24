/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.kayenta.judge.utils

import scala.collection.JavaConverters._

object MapUtils {
  def get(data: Any, path: String*): Option[Any] = {
    if (path.isEmpty) Some(data) else {
      data match {
        case jm: java.util.Map[_, _] =>
          val map = jm.asScala.toMap.asInstanceOf[Map[String, Any]]
          map.get(path.head).flatMap(v => get(v, path.tail: _*))
        case m: Map[_, _] =>
          val map = m.asInstanceOf[Map[String, Any]]
          map.get(path.head).flatMap(v => get(v, path.tail: _*))
        case jl: java.util.List[_] =>
          val result = jl.asScala.toSeq.flatMap(v => get(v, path: _*))
          if (result.isEmpty) None else Some(result)
        case vs: Seq[_] =>
          val result = vs.flatMap(v => get(v, path: _*))
          if (result.isEmpty) None else Some(result)
        case _ =>
          None
      }
    }
  }

  def getAsStringWithDefault(default: String, data: Any, path: String*): String = {
    get(data, path: _*).getOrElse(default).toString
  }
}
