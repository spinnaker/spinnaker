import autoBindMethods from 'class-autobind-decorator';
import { get } from 'lodash';
import * as React from 'react';
import { connect } from 'react-redux';
import type { Action, Dispatch } from 'redux';
import { createSelector } from 'reselect';

import * as Creators from '../../actions/creators';
import type { ICanaryMetricConfig } from '../../domain';
import { MetricConfigMode } from '../../domain/IMetricConfigMode';
import type { ICanaryMetricValidationErrors, MetricValidatorFunction } from '../../edit/editMetricValidation';
import MetricConfigModeToggle from '../../edit/metricConfigModeToggle';
import MetricQueryTemplateEditor from '../../edit/metricQueryTemplateEditor';
import { templateProviderVariables } from '../../edit/templateProviderVariables';
import { clearTemplateState, useMetricConfigMode } from '../../edit/useMetricConfigMode';
import { DISABLE_EDIT_CONFIG, DisableableInput } from '../../layout/disableable';
import FormRow from '../../layout/formRow';
import type { IKeyValuePair, IUpdateKeyValueListPayload } from '../../layout/keyValueList';
import KeyValueList from '../../layout/keyValueList';
import type { ICanaryState } from '../../reducers';
import { editingMetricSelector } from '../../selectors';

import './metricConfigurer.less';

interface ISignalFxMetricConfigurerStateProps {
  editingMetric: ICanaryMetricConfig;
  validationErrors: ICanaryMetricValidationErrors;
}

interface ISignalFxMetricConfigurerDispatchProps {
  updateMetricName: (name: string) => void;
  updateAggregationMethod: (method: string) => void;
  updateQueryPairs: (payload: IUpdateKeyValueListPayload) => void;
  clearTemplateState: () => void;
}

type SignalFxMetricConfigurerProps = ISignalFxMetricConfigurerStateProps & ISignalFxMetricConfigurerDispatchProps;

export const queryFinder = (metric: ICanaryMetricConfig) => get(metric, 'query.metricName', '');
const getSignalFxMetric = queryFinder;
const getQueryPairs = (metric: ICanaryMetricConfig) => get(metric, 'query.queryPairs', []) as IKeyValuePair[];
const getAggregationMethod = (metric: ICanaryMetricConfig) => get(metric, 'query.aggregationMethod', '');

// Guided fields for a SignalFx metric, split out into its own function component so it can use
// the mode-toggle hook (SignalFxMetricConfigurer itself is a class component).
function SignalFxGuidedFields({
  editingMetric,
  validationErrors,
  onMetricNameChange,
  onAggregationMethodChange,
  updateQueryPairs,
}: {
  editingMetric: ICanaryMetricConfig;
  validationErrors: ICanaryMetricValidationErrors;
  onMetricNameChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  onAggregationMethodChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  updateQueryPairs: (payload: IUpdateKeyValueListPayload) => void;
}) {
  return (
    <>
      <FormRow label="SignalFx Metric" error={get(validationErrors, 'signalFxMetric.message', null)} inputOnly={true}>
        <DisableableInput
          type="text"
          value={getSignalFxMetric(editingMetric)}
          onChange={onMetricNameChange}
          disabledStateKeys={[DISABLE_EDIT_CONFIG]}
        />
      </FormRow>
      <FormRow
        label="Aggregation Method"
        inputOnly={true}
        helpId="canary.config.signalFx.aggregationMethod"
        error={get(validationErrors, 'aggregationMethod.message', null)}
      >
        <DisableableInput
          type="text"
          value={getAggregationMethod(editingMetric)}
          onChange={onAggregationMethodChange}
          disabledStateKeys={[DISABLE_EDIT_CONFIG]}
        />
      </FormRow>
      <FormRow
        label="Query Pairs"
        helpId="canary.config.signalFx.queryPairs"
        error={get(validationErrors, 'queryPairs.message', null)}
      >
        <KeyValueList
          className="signalfx-query-pairs"
          list={getQueryPairs(editingMetric)}
          actionCreator={updateQueryPairs}
        />
      </FormRow>
    </>
  );
}

@autoBindMethods
class SignalFxMetricConfigurer extends React.Component<SignalFxMetricConfigurerProps> {
  public onMetricNameChange(e: React.ChangeEvent<HTMLInputElement>) {
    this.props.updateMetricName(e.target.value);
  }

  public onAggregationMethodChange(e: React.ChangeEvent<HTMLInputElement>) {
    this.props.updateAggregationMethod(e.target.value);
  }

  public render() {
    const { editingMetric, updateQueryPairs, validationErrors, clearTemplateState: onClearTemplateState } = this.props;

    return (
      <SignalFxModeSection
        {...{
          editingMetric,
          validationErrors,
          updateQueryPairs,
          onClearTemplateState,
          onMetricNameChange: this.onMetricNameChange,
          onAggregationMethodChange: this.onAggregationMethodChange,
        }}
      />
    );
  }
}

function SignalFxModeSection(props: {
  editingMetric: ICanaryMetricConfig;
  validationErrors: ICanaryMetricValidationErrors;
  updateQueryPairs: (payload: IUpdateKeyValueListPayload) => void;
  onClearTemplateState: () => void;
  onMetricNameChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  onAggregationMethodChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
}) {
  const {
    editingMetric,
    validationErrors,
    updateQueryPairs,
    onClearTemplateState,
    onMetricNameChange,
    onAggregationMethodChange,
  } = props;
  const hasTemplateData = Boolean(get(editingMetric, 'query.template'));
  const hasGuidedData = Boolean(
    getSignalFxMetric(editingMetric) || getAggregationMethod(editingMetric) || getQueryPairs(editingMetric).length,
  );
  const [mode, setMode] = useMetricConfigMode(editingMetric.id, hasTemplateData, hasGuidedData);

  const handleModeChange = (newMode: MetricConfigMode) => {
    setMode(newMode);
    onClearTemplateState();
  };

  return (
    <section>
      <MetricConfigModeToggle mode={mode} onChange={handleModeChange} />
      {mode === MetricConfigMode.GUIDED && (
        <SignalFxGuidedFields
          editingMetric={editingMetric}
          validationErrors={validationErrors}
          onMetricNameChange={onMetricNameChange}
          onAggregationMethodChange={onAggregationMethodChange}
          updateQueryPairs={updateQueryPairs}
        />
      )}
      {mode === MetricConfigMode.TEMPLATE && (
        <MetricQueryTemplateEditor providerVariableHints={templateProviderVariables.signalfx} />
      )}
    </section>
  );
}

/**
 * Validates SignalFx specific fields on the edit metric modal
 */
export function validateMetric(editingMetric: ICanaryMetricConfig): ICanaryMetricValidationErrors {
  const errors: ICanaryMetricValidationErrors = {};

  const validators: MetricValidatorFunction[] = [...getSignalFxValidators(editingMetric)];

  return validators.reduce((reducedErrors, validator) => validator(reducedErrors, editingMetric), errors);
}

/**
 * returns the list of validators for the SignalFx edit metric form.
 */
export function getSignalFxValidators(editingMetric: ICanaryMetricConfig): MetricValidatorFunction[] {
  if (!editingMetric || !editingMetric.query) {
    return [];
  }
  return [validateSignalFxMetricName, validateAggregationMethod, validateQueryPairs];
}

/**
 * Validates that the user has supplied a SignalFx metric.
 */
function validateSignalFxMetricName(
  errors: ICanaryMetricValidationErrors,
  editingMetric: ICanaryMetricConfig,
): ICanaryMetricValidationErrors {
  const nextErrors = { ...errors };

  const signalFxMetric = getSignalFxMetric(editingMetric);

  if (!signalFxMetric) {
    nextErrors.signalFxMetric = { message: 'The SignalFx metric is required.' };
  }

  return nextErrors;
}

/**
 * Validates that the user has supplied an aggregation method.
 */
function validateAggregationMethod(
  errors: ICanaryMetricValidationErrors,
  editingMetric: ICanaryMetricConfig,
): ICanaryMetricValidationErrors {
  const nextErrors = { ...errors };

  const aggregationMethod = getAggregationMethod(editingMetric);

  if (!aggregationMethod) {
    nextErrors.aggregationMethod = { message: 'The SignalFx SignalFlow stream aggregation method is required.' };
  }

  return nextErrors;
}

/**
 * Validates that if the user has supplied query pairs that all key value combos contain values.
 */
function validateQueryPairs(
  errors: ICanaryMetricValidationErrors,
  editingMetric: ICanaryMetricConfig,
): ICanaryMetricValidationErrors {
  const nextErrors = { ...errors };

  const queryPairs: IKeyValuePair[] = getQueryPairs(editingMetric);

  queryPairs.forEach((qp) => {
    if (!qp.key || !qp.value) {
      nextErrors.queryPairs = { message: 'All query pairs must contain a non-blank key and value.' };
    }
  });

  return nextErrors;
}

const sfxEditingMetricValidationErrorsSelector = createSelector(editingMetricSelector, validateMetric);

function mapStateToProps(state: ICanaryState): ISignalFxMetricConfigurerStateProps {
  return {
    editingMetric: state.selectedConfig.editingMetric,
    validationErrors: sfxEditingMetricValidationErrorsSelector(state),
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): ISignalFxMetricConfigurerDispatchProps {
  return {
    updateMetricName: (metricName: string): void => {
      dispatch(Creators.updateSignalFxMetricName({ metricName }));
    },
    updateAggregationMethod: (aggregationMethod: string): void => {
      dispatch(Creators.updateSignalFxAggregationMethod({ aggregationMethod }));
    },
    updateQueryPairs: (payload) => dispatch(Creators.updateSignalFxQueryPairs(payload)),
    clearTemplateState: () => clearTemplateState(dispatch as Dispatch<any>),
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(SignalFxMetricConfigurer);
