import { get } from 'lodash';
import * as React from 'react';
import { connect } from 'react-redux';
import type { Action, Dispatch } from 'redux';

import * as Creators from '../../actions/creators';
import type { ICanaryMetricConfig } from '../../domain';
import { MetricConfigMode } from '../../domain/IMetricConfigMode';
import MetricConfigModeToggle from '../../edit/metricConfigModeToggle';
import MetricQueryTemplateEditor from '../../edit/metricQueryTemplateEditor';
import { templateProviderVariables } from '../../edit/templateProviderVariables';
import { clearTemplateState, useMetricConfigMode } from '../../edit/useMetricConfigMode';
import FormRow from '../../layout/formRow';
import GraphiteMetricTypeSelector from './metricTypeSelector';
import type { ICanaryState } from '../../reducers';

interface IGraphiteMetricConfigurerDispatchProps {
  changeMetricName: (name: string) => void;
  clearTemplateState: () => void;
}

interface IGraphiteMetricConfigurerStateProps {
  editingMetric: ICanaryMetricConfig;
}

type GraphiteMetricConfigurerProps = IGraphiteMetricConfigurerStateProps & IGraphiteMetricConfigurerDispatchProps;

export const queryFinder = (metric: ICanaryMetricConfig) => get(metric, 'query.metricName', '');

/*
 * Component for configuring a Graphite metric.
 * */
function GraphiteMetricConfigurer({
  changeMetricName,
  clearTemplateState: onClearTemplateState,
  editingMetric,
}: GraphiteMetricConfigurerProps) {
  const hasTemplateData = Boolean(get(editingMetric, 'query.template'));
  const hasGuidedData = Boolean(queryFinder(editingMetric));
  const [mode, setMode] = useMetricConfigMode(editingMetric.id, hasTemplateData, hasGuidedData);

  const handleModeChange = (newMode: MetricConfigMode) => {
    setMode(newMode);
    onClearTemplateState();
  };

  return (
    <>
      <MetricConfigModeToggle mode={mode} onChange={handleModeChange} />
      {mode === MetricConfigMode.GUIDED && (
        <FormRow label="Graphite Metric" inputOnly={true}>
          <GraphiteMetricTypeSelector
            value={queryFinder(editingMetric)}
            onChange={(option: string[]) => {
              changeMetricName(option[0]);
            }}
          />
        </FormRow>
      )}
      {mode === MetricConfigMode.TEMPLATE && (
        <MetricQueryTemplateEditor providerVariableHints={templateProviderVariables.graphite} />
      )}
    </>
  );
}

function mapStateToProps(state: ICanaryState): IGraphiteMetricConfigurerStateProps {
  return {
    editingMetric: state.selectedConfig.editingMetric,
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IGraphiteMetricConfigurerDispatchProps {
  return {
    changeMetricName: (metricName: string): void => {
      dispatch(Creators.updateGraphiteMetricName({ metricName }));
    },
    clearTemplateState: () => clearTemplateState(dispatch as Dispatch<any>),
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(GraphiteMetricConfigurer);
