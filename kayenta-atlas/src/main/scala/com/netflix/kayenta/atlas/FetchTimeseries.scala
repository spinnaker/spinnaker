/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.kayenta.atlas

import com.fasterxml.jackson.annotation.JsonProperty

case class TimeseriesData(`type`: String, values: List[Double])

case class FetchTimeseries(
  @JsonProperty("type") `type`: String,
  id: String,
  query: String,
  label: String,
  start: Long,
  step: Long,
  end: Long,
  tags: Map[String, String],
  data: TimeseriesData) extends Ordered[FetchTimeseries] {

  override def compare(that: FetchTimeseries): Int = {
    import scala.math.Ordered.orderingToOrdered
    (id, start) compare (that.id, that.start)
  }
}

object FetchTimeseries {
  def mergeByTime(data: List[FetchTimeseries]): FetchTimeseries = {
    if (data.length <= 1) {
      data.head
    } else {
      var current = data.head
      data.takeRight(data.length - 1).foreach { item =>
        val offset = (item.start - current.end) / current.step
        val newValues = current.data.values ++ List.fill(offset.toInt)(Double.NaN) ++ item.data.values
        current = current.copy(end = item.end, data = TimeseriesData("array", newValues))
      }
      current
    }
  }

  def merge(data: List[FetchTimeseries]): Map[String, FetchTimeseries] = {
    // group by id, sorted by start time
    val mapData = data.groupBy(ts => ts.id).map {
      case (id, tsList) =>
        id -> mergeByTime(tsList.sorted)
    }
    mapData
  }
}
