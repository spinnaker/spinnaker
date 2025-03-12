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

package com.netflix.kayenta.judge.preprocessing

import com.netflix.kayenta.judge.Metric

case class ValidationResult(valid: Boolean, reason: Option[String]=None)

object Validators {

  /**
    * Validate if the input data array is empty
    * @param metric
    * @return
    */
  def checkEmptyArray(metric: Metric): ValidationResult = {
    if(metric.values.isEmpty){
      val reason = s"Empty data array for ${metric.label}"
      ValidationResult(valid=false, reason=Some(reason))
    }else{
      ValidationResult(valid=true)
    }
  }

  /**
    * Validate if the input data array is all NaN values
    * @param metric
    * @return
    */
  def checkNaNArray(metric: Metric): ValidationResult = {
    if(metric.values.forall(_.isNaN)){
      val reason = s"No data for ${metric.label}"
      ValidationResult(valid=false, reason=Some(reason))
    }else{
      ValidationResult(valid=true)
    }
  }

}
