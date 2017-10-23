import * as React from 'react';
import { connect } from 'react-redux';
import { ICanaryMetricConfig } from '../domain/ICanaryConfig';
import metricStoreConfigService from '../metricStore/metricStoreConfig.service';
import { ICanaryState } from '../reducers/index';

interface IMetricConfigurerDelegatorStateProps {
  editingMetric: ICanaryMetricConfig;
}

/*
* Should find and render the appropriate metric configurer for a given metric store.
* */
function MetricConfigurerDelegator({ editingMetric }: IMetricConfigurerDelegatorStateProps) {
  const config = metricStoreConfigService.getDelegate(editingMetric.serviceName);
  if (config && config.metricConfigurer) {
    const MetricConfigurer = config.metricConfigurer;
    return <MetricConfigurer/>;
  } else {
    return (
      <p>
        Metric configuration has not been implemented for {editingMetric.serviceName}.
      </p>
    );
  }
}

function mapStateToProps(state: ICanaryState): IMetricConfigurerDelegatorStateProps {
  return {
    editingMetric: state.selectedConfig.editingMetric,
  };
}

export default connect(mapStateToProps)(MetricConfigurerDelegator);
