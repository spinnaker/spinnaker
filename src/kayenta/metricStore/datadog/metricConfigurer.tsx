import * as React from 'react';
import { get } from 'lodash';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { Option } from 'react-select';
import FormRow from 'kayenta/layout/formRow';
import { ICanaryState } from 'kayenta/reducers';
import * as Creators from 'kayenta/actions/creators';
import { ICanaryMetricConfig } from 'kayenta/domain';
import DatadogMetricTypeSelector from './metricTypeSelector';

interface IDatadogMetricConfigurerStateProps {
  editingMetric: ICanaryMetricConfig;
}

interface IDatadogMetricConfigurerDispatchProps {
  changeMetricName: (name: string) => void;
}

type DatadogMetricConfigurerProps = IDatadogMetricConfigurerStateProps & IDatadogMetricConfigurerDispatchProps;

export const queryFinder = (metric: ICanaryMetricConfig) => get(metric, 'query.metricName', '');

/*
* Component for configuring a Datadog metric.
* */
function DatadogMetricConfigurer({ changeMetricName, editingMetric }: DatadogMetricConfigurerProps) {
  return (
    <FormRow label="Datadog Metric">
      <DatadogMetricTypeSelector
        value={queryFinder(editingMetric)}
        onChange={(option: Option<string>) => changeMetricName(get(option, 'value'))}
      />
    </FormRow>
  );
}

function mapStateToProps(state: ICanaryState): IDatadogMetricConfigurerStateProps {
  return {
    editingMetric: state.selectedConfig.editingMetric,
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IDatadogMetricConfigurerDispatchProps {
  return {
    changeMetricName: (metricName: string): void => {
      dispatch(Creators.updateDatadogMetricName({ metricName }));
    },
  };
}

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(DatadogMetricConfigurer);
