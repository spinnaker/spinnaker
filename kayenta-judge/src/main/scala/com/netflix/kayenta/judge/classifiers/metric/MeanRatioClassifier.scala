package com.netflix.kayenta.judge.classifiers.metric

import com.netflix.kayenta.judge.Metric

class MeanRatioClassifier() extends BaseMetricClassifier{

  override def classify(control: Metric, experiment: Metric): MetricClassification = {
    MetricClassification(Pass, None, 1.0)
  }

}
