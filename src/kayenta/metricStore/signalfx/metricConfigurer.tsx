import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { get } from 'lodash';
import autoBindMethods from 'class-autobind-decorator';

import FormRow from 'kayenta/layout/formRow';
import { ICanaryState } from 'kayenta/reducers';
import * as Creators from 'kayenta/actions/creators';
import { DisableableInput, DISABLE_EDIT_CONFIG } from 'kayenta/layout/disableable';
import { ICanaryMetricConfig } from 'kayenta/domain';
import KeyValueList, { IKeyValuePair, IUpdateKeyValueListPayload } from '../../layout/keyValueList';

import './metricConfigurer.less';
import { ICanaryMetricValidationErrors, MetricValidatorFunction } from '../../edit/editMetricValidation';
import { createSelector } from 'reselect';
import { editingMetricSelector } from '../../selectors';

interface ISignalFxMetricConfigurerStateProps {
  editingMetric: ICanaryMetricConfig;
  validationErrors: ICanaryMetricValidationErrors;
}

interface ISignalFxMetricConfigurerDispatchProps {
  updateMetricName: (name: string) => void;
  updateAggregationMethod: (method: string) => void;
  updateQueryPairs: (payload: IUpdateKeyValueListPayload) => void;
}

type SignalFxMetricConfigurerProps = ISignalFxMetricConfigurerStateProps & ISignalFxMetricConfigurerDispatchProps;

export const queryFinder = (metric: ICanaryMetricConfig) => get(metric, 'query.metricName', '');
const getSignalFxMetric = queryFinder;
const getQueryPairs = (metric: ICanaryMetricConfig) => get(metric, 'query.queryPairs', []) as IKeyValuePair[];
const getAggregationMethod = (metric: ICanaryMetricConfig) => get(metric, 'query.aggregationMethod', '');

@autoBindMethods
class SignalFxMetricConfigurer extends React.Component<SignalFxMetricConfigurerProps> {
  public onMetricNameChange(e: React.ChangeEvent<HTMLInputElement>) {
    this.props.updateMetricName(e.target.value);
  }

  public onAggregationMethodChange(e: React.ChangeEvent<HTMLInputElement>) {
    this.props.updateAggregationMethod(e.target.value);
  }

  public render() {
    const { editingMetric, updateQueryPairs, validationErrors } = this.props;

    return (
      <section>
        <FormRow label="SignalFx Metric" error={get(validationErrors, 'signalFxMetric.message', null)}>
          <DisableableInput
            type="text"
            value={getSignalFxMetric(editingMetric)}
            onChange={this.onMetricNameChange}
            disabledStateKeys={[DISABLE_EDIT_CONFIG]}
          />
        </FormRow>
        <FormRow
          label="Aggregation Method"
          helpId="canary.config.signalFx.aggregationMethod"
          error={get(validationErrors, 'aggregationMethod.message', null)}
        >
          <DisableableInput
            type="text"
            value={getAggregationMethod(editingMetric)}
            onChange={this.onAggregationMethodChange}
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
      </section>
    );
  }
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

  queryPairs.forEach(qp => {
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
    updateQueryPairs: payload => dispatch(Creators.updateSignalFxQueryPairs(payload)),
  };
}

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(SignalFxMetricConfigurer);
