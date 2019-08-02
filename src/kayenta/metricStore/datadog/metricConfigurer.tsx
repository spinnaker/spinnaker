import * as React from 'react';
import { get } from 'lodash';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { Option } from 'react-select';
import FormRow from 'kayenta/layout/formRow';
import RadioChoice from 'kayenta/layout/radioChoice';
import { ICanaryState } from 'kayenta/reducers';
import * as Creators from 'kayenta/actions/creators';
import { ICanaryMetricConfig } from 'kayenta/domain';
import DatadogMetricTypeSelector from './metricTypeSelector';

interface IDatadogMetricConfigurerStateProps {
  editingMetric: ICanaryMetricConfig;
}

interface IDatadogMetricConfigurerDispatchProps {
  changeMetricName: (agg: string, name: string) => void;
}

type DatadogMetricConfigurerProps = IDatadogMetricConfigurerStateProps & IDatadogMetricConfigurerDispatchProps;

export const queryFinder = (metric: ICanaryMetricConfig) => get(metric, 'query.metricName', ':');

export const nameFinder = (metric: ICanaryMetricConfig) => queryFinder(metric).split(':', 2)[1];
export const aggFinder = (metric: ICanaryMetricConfig) => queryFinder(metric).split(':', 2)[0];

/*
* Component for configuring a Datadog metric.
* */
function DatadogMetricConfigurer({ changeMetricName, editingMetric }: DatadogMetricConfigurerProps) {
  return (
    <>
      <FormRow label="Datadog Metric">
        <DatadogMetricTypeSelector
          value={nameFinder(editingMetric)}
          onChange={(option: Option<string>) => changeMetricName(aggFinder(editingMetric), get(option, 'value'))}
        />
      </FormRow>
      <FormRow label="Metric Aggregation">
        <RadioChoice
          value="avg"
          label="Average"
          name="aggregator"
          current={aggFinder(editingMetric)}
          action={() => changeMetricName('avg', nameFinder(editingMetric))}
        />
        <RadioChoice
          value="sum"
          label="Sum"
          name="aggregator"
          current={aggFinder(editingMetric)}
          action={() => changeMetricName('sum', nameFinder(editingMetric))}
        />
        <RadioChoice
          value="max"
          label="Max"
          name="aggregator"
          current={aggFinder(editingMetric)}
          action={() => changeMetricName('max', nameFinder(editingMetric))}
        />
        <RadioChoice
          value="min"
          label="Min"
          name="aggregator"
          current={aggFinder(editingMetric)}
          action={() => changeMetricName('min', nameFinder(editingMetric))}
        />
      </FormRow>
    </>
  );
}

function mapStateToProps(state: ICanaryState): IDatadogMetricConfigurerStateProps {
  return {
    editingMetric: state.selectedConfig.editingMetric,
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IDatadogMetricConfigurerDispatchProps {
  return {
    changeMetricName: (agg: string, name: string): void => {
      const metricName = agg + ':' + name;
      dispatch(Creators.updateDatadogMetricName({ metricName }));
    },
  };
}

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(DatadogMetricConfigurer);
