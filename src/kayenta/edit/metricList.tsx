import * as React from 'react';
import { connect } from 'react-redux';
import { ICanaryMetricConfig } from '../domain/ICanaryConfig';
import MetricDetail from './metricDetail';

interface IMetricListProps {
  metrics: ICanaryMetricConfig[];
  selectedMetric: ICanaryMetricConfig;
}

/*
 * Configures an entire list of metrics.
 */
function MetricList({ metrics }: IMetricListProps) {
  return (
    <section>
      <h2>Metrics</h2>
      <ul>
        {metrics.map((metric, index) => (
          // TODO: put id on metric? name can change by edit, index can change by remove operation
          // unless remove leaves a null entry instead of deleting the index.
          <li key={index}>
            <MetricDetail metric={metric}/>
          </li>
        ))}
      </ul>
    </section>
  );
}

function mapStateToProps(state: any): IMetricListProps {
  return {
    metrics: state.metricList,
    selectedMetric: null
  };
}

export default connect(mapStateToProps)(MetricList);
