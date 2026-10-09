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
import { DISABLE_EDIT_CONFIG, DisableableInput } from '../../layout/disableable';
import FormRow from '../../layout/formRow';
import type { ICanaryState } from '../../reducers';

interface INewRelicMetricConfigurerStateProps {
  editingMetric: ICanaryMetricConfig;
}

interface INewRelicMetricConfigurerDispatchProps {
  changeSelect: (event: any) => void;
  clearTemplateState: () => void;
}

type INewRelicMetricConfigurerProps = INewRelicMetricConfigurerStateProps & INewRelicMetricConfigurerDispatchProps;

export const queryFinder = (metric: ICanaryMetricConfig) => get(metric, 'query.select', '');

/*
 * Component for configuring a New Relic metric
 */
function NewRelicMetricConfigurer({
  changeSelect,
  clearTemplateState: onClearTemplateState,
  editingMetric,
}: INewRelicMetricConfigurerProps) {
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
        <FormRow label="NRQL Select" inputOnly={true}>
          <DisableableInput
            type="text"
            value={queryFinder(editingMetric)}
            onChange={changeSelect}
            disabledStateKeys={[DISABLE_EDIT_CONFIG]}
          />
          <span className="body-small color-text-caption" style={{ marginTop: '5px' }}>
            Enter the NRQL query only up to, but not including, the WHERE clause
          </span>
        </FormRow>
      )}
      {mode === MetricConfigMode.TEMPLATE && (
        <MetricQueryTemplateEditor providerVariableHints={templateProviderVariables.newrelic} />
      )}
    </>
  );
}

function mapStateToProps(state: ICanaryState): INewRelicMetricConfigurerStateProps {
  return {
    editingMetric: state.selectedConfig.editingMetric,
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): INewRelicMetricConfigurerDispatchProps {
  return {
    changeSelect: (event: any) => {
      dispatch(Creators.updateNewRelicSelect({ select: event.target.value }));
    },
    clearTemplateState: () => clearTemplateState(dispatch as Dispatch<any>),
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(NewRelicMetricConfigurer);
