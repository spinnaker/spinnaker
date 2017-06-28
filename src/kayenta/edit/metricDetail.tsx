import * as React from 'react';
import { ICanaryMetricConfig } from '../domain/ICanaryConfig';

interface IMetricDetailProps {
  metric: ICanaryMetricConfig;
}

/*
 * Configures all the available settings for a single metric.
 */
export default function MetricDetail({ metric }: IMetricDetailProps) {
  return (
    <div>
      <div>
        <label>Name: </label> {metric.name}
      </div>
      <div>
        <label>Service: </label> {metric.serviceName}
      </div>
    </div>
  );
}
