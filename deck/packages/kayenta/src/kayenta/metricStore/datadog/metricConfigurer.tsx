import { get } from 'lodash';
import * as React from 'react';
import { connect } from 'react-redux';
import type { Option } from 'react-select';
import type { Action, Dispatch } from 'redux';

import * as Creators from '../../actions/creators';
import type { ICanaryMetricConfig } from '../../domain';
import { MetricConfigMode } from '../../domain/IMetricConfigMode';
import MetricConfigModeToggle from '../../edit/metricConfigModeToggle';
import MetricQueryTemplateEditor from '../../edit/metricQueryTemplateEditor';
import { templateProviderVariables } from '../../edit/templateProviderVariables';
import { clearTemplateState, useMetricConfigMode } from '../../edit/useMetricConfigMode';
import FormRow from '../../layout/formRow';
import RadioChoice from '../../layout/radioChoice';
import DatadogMetricTypeSelector from './metricTypeSelector';
import type { ICanaryState } from '../../reducers';

interface IDatadogMetricConfigurerStateProps {
  editingMetric: ICanaryMetricConfig;
}

interface IDatadogMetricConfigurerDispatchProps {
  changeMetricName: (agg: string, name: string) => void;
  clearTemplateState: () => void;
}

type DatadogMetricConfigurerProps = IDatadogMetricConfigurerStateProps & IDatadogMetricConfigurerDispatchProps;

export const queryFinder = (metric: ICanaryMetricConfig) => get(metric, 'query.metricName', ':');

export const nameFinder = (metric: ICanaryMetricConfig) => queryFinder(metric).split(':', 2)[1];
export const aggFinder = (metric: ICanaryMetricConfig) => queryFinder(metric).split(':', 2)[0];

/*
 * Component for configuring a Datadog metric.
 * */
function DatadogMetricConfigurer({
  changeMetricName,
  clearTemplateState: onClearTemplateState,
  editingMetric,
}: DatadogMetricConfigurerProps) {
  const hasTemplateData = Boolean(get(editingMetric, 'query.template'));
  const hasGuidedData = Boolean(nameFinder(editingMetric));
  const [mode, setMode] = useMetricConfigMode(editingMetric.id, hasTemplateData, hasGuidedData);

  const handleModeChange = (newMode: MetricConfigMode) => {
    setMode(newMode);
    onClearTemplateState();
  };

  return (
    <>
      <MetricConfigModeToggle mode={mode} onChange={handleModeChange} />
      {mode === MetricConfigMode.GUIDED && (
        <>
          <FormRow label="Datadog Metric" inputOnly={true}>
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
      )}
      {mode === MetricConfigMode.TEMPLATE && (
        <MetricQueryTemplateEditor providerVariableHints={templateProviderVariables.datadog} />
      )}
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
    clearTemplateState: () => clearTemplateState(dispatch as Dispatch<any>),
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(DatadogMetricConfigurer);
