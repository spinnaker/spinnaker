package com.netflix.kayenta.judge

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
