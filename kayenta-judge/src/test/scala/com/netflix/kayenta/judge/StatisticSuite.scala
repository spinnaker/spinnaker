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

package com.netflix.kayenta.judge

import com.netflix.kayenta.judge.stats.{DescriptiveStatistics, MetricStatistics}
import org.scalatest.FunSuite


class StatisticSuite extends FunSuite{

  test("Summary Statistics"){
    val metric = Metric("test", Array[Double](1.0), "test")
    val result = DescriptiveStatistics.summary(metric)
    val truth = MetricStatistics(1, 1, 1, 1, 1)
    assert(result === truth)
  }

  test("Summary Statistics No Data"){
    val metric = Metric("testNoData", Array[Double](), "test")
    val result = DescriptiveStatistics.summary(metric)
    val truth = MetricStatistics(0, 0, 0, 0, 0)
    assert(result === truth)
  }

}
