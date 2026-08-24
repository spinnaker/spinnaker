import { get } from 'lodash';
import * as React from 'react';
import { connect } from 'react-redux';
import type { Option } from 'react-select';
import type { Action, Dispatch } from 'redux';
import { createSelector } from 'reselect';

import * as Creators from '../../actions/creators';
import type { ICanaryMetricConfig } from '../../domain/ICanaryConfig';
import { MetricConfigMode } from '../../domain/IMetricConfigMode';
import type { IPrometheusCanaryMetricSetQueryConfig } from './domain/IPrometheusCanaryMetricSetQueryConfig';
import type { ICanaryMetricValidationErrors } from '../../edit/editMetricValidation';
import MetricConfigModeToggle from '../../edit/metricConfigModeToggle';
import MetricQueryTemplateEditor from '../../edit/metricQueryTemplateEditor';
import { templateProviderVariables } from '../../edit/templateProviderVariables';
import { clearTemplateState, useMetricConfigMode } from '../../edit/useMetricConfigMode';
import { DISABLE_EDIT_CONFIG, DisableableReactSelect } from '../../layout/disableable';
import FormRow from '../../layout/formRow';
import type { IUpdateListPayload } from '../../layout/list';
import { List } from '../../layout/list';
import PrometheusMetricTypeSelector from './metricTypeSelector';
import type { ICanaryState } from '../../reducers';
import { editingMetricSelector } from '../../selectors';

interface IPrometheusMetricConfigurerStateProps {
  editingMetric: ICanaryMetricConfig;
  validationErrors: ICanaryMetricValidationErrors;
}

interface IPrometheusMetricConfigurerDispatchProps {
  updateLabelBindings: (payload: IUpdateListPayload) => void;
  updateGroupBy: (payload: IUpdateListPayload) => void;
  updatePrometheusMetricQueryField: <T extends keyof IPrometheusCanaryMetricSetQueryConfig>(
    field: keyof IPrometheusCanaryMetricSetQueryConfig,
    value: Option<IPrometheusCanaryMetricSetQueryConfig[T]>,
  ) => void;
  clearTemplateState: () => void;
}

const RESOURCE_TYPES = ['gce_instance', 'aws_ec2_instance'];

const toReactSelectOptions = (values: string[]): Array<Option<string>> =>
  values.map((value) => ({ value, label: value }));

/*
 * Component for configuring a Prometheus metric.
 *
 * Prometheus used to have its own bespoke Default/PromQL toggle (backed by a client-only
 * "PromQL:" string prefix on the inline template, used to distinguish PromQL-mode templates from
 * plain custom filters). That toggle is now just an instance of the generic Guided/Template mode
 * toggle shared by every provider, and the "PromQL:" prefix trick was retired entirely -- nothing
 * server-side (PrometheusMetricsService.java / PrometheusCanaryMetricSetQueryConfig.java) ever
 * parsed for that prefix, so it was purely client-side dead weight once the template editor
 * became available unconditionally rather than being gated behind selecting "PromQL".
 * */
function PrometheusMetricConfigurer({
  editingMetric,
  updateLabelBindings,
  updateGroupBy,
  updatePrometheusMetricQueryField,
  validationErrors,
  clearTemplateState: onClearTemplateState,
}: IPrometheusMetricConfigurerStateProps & IPrometheusMetricConfigurerDispatchProps) {
  const hasTemplateData = Boolean(get(editingMetric, 'query.template'));
  const hasGuidedData = Boolean(get(editingMetric, 'query.metricName'));
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
          <FormRow label="Resource Type" inputOnly={true}>
            <DisableableReactSelect
              value={get(editingMetric, 'query.resourceType')}
              options={toReactSelectOptions(RESOURCE_TYPES)}
              onChange={(option: Option<string>) => updatePrometheusMetricQueryField('resourceType', option)}
              disabledStateKeys={[DISABLE_EDIT_CONFIG]}
            />
          </FormRow>
          <FormRow label="Metric Name" inputOnly={true} error={get(validationErrors, 'metricName.message', null)}>
            <PrometheusMetricTypeSelector
              value={get(editingMetric, 'query.metricName', '')}
              onChange={(option: Option<string>) => updatePrometheusMetricQueryField('metricName', option)}
            />
          </FormRow>
          <FormRow label="Label Bindings">
            <List list={editingMetric.query.labelBindings || []} actionCreator={updateLabelBindings} />
          </FormRow>
          <FormRow label="Group By">
            <List list={editingMetric.query.groupByFields || []} actionCreator={updateGroupBy} />
          </FormRow>
        </>
      )}
      {mode === MetricConfigMode.TEMPLATE && (
        <MetricQueryTemplateEditor providerVariableHints={templateProviderVariables.prometheus} />
      )}
    </>
  );
}

function mapStateToProps(state: ICanaryState): IPrometheusMetricConfigurerStateProps {
  return {
    editingMetric: state.selectedConfig.editingMetric,
    validationErrors: prometheusMetricValidationSelector(state),
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IPrometheusMetricConfigurerDispatchProps {
  return {
    updateLabelBindings: (payload) => dispatch(Creators.updatePrometheusLabelBindings(payload)),
    updateGroupBy: (payload) => dispatch(Creators.updatePrometheusGroupBy(payload)),
    updatePrometheusMetricQueryField: (field, option) =>
      dispatch(Creators.updatePrometheusMetricQueryField({ field, value: option && option.value })),
    clearTemplateState: () => clearTemplateState(dispatch as Dispatch<any>),
  };
}

const prometheusMetricValidationSelector = createSelector(editingMetricSelector, validateMetric);

/**
 * Validates Prometheus specific fields on the edit metric modal
 */
function validateMetric(editingMetric: ICanaryMetricConfig): ICanaryMetricValidationErrors {
  const errors: ICanaryMetricValidationErrors = {
    metricName: null,
  };
  return [validatePrometheusMetricName].reduce(
    (reducedErrors, validator) => validator(reducedErrors, editingMetric),
    errors,
  );
}

/**
 * Validates that the user has supplied a Prometheus metric.
 */
function validatePrometheusMetricName(
  errors: ICanaryMetricValidationErrors,
  editingMetric: ICanaryMetricConfig,
): ICanaryMetricValidationErrors {
  const nextErrors = { ...errors };
  const metricName = get(editingMetric, 'query.metricName', '');
  if (!metricName) {
    nextErrors.metricName = { message: 'The Prometheus metric is required.' };
    return nextErrors;
  }
  return nextErrors;
}

export default connect(mapStateToProps, mapDispatchToProps)(PrometheusMetricConfigurer);
