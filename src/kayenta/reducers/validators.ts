import { chain } from 'lodash';
import { ICanaryState } from './index';
import { KayentaAccountType } from 'kayenta/domain';

export interface IConfigValidationError {
  message: string;
}

type IConfigValidator = (state: ICanaryState) => IConfigValidationError;

const createValidationReducer = (validator: IConfigValidator) => {
  return (state: ICanaryState) => {
    if (!state.selectedConfig.config) {
      return state;
    }

    const error = validator(state);
    if (!error) {
      return state;
    }

    return {
      ...state,
      selectedConfig: {
        ...state.selectedConfig,
        validationErrors: state.selectedConfig.validationErrors.concat(
          error ? [error] : []
        ),
      },
    };
  };
};

const isConfigNameUnique: IConfigValidator = state => {
  const selectedConfig = state.selectedConfig.config;
  const configSummaries = state.data.configSummaries;
  const isUnique = configSummaries.every(s =>
    selectedConfig.name !== s.name || selectedConfig.id === s.id
  );

  return isUnique
    ? null
    : { message: `Canary config '${selectedConfig.name}' already exists.` };
};

// See https://github.com/Netflix-Skunkworks/kayenta/blob/master/kayenta-web/src/main/java/com/netflix/kayenta/controllers/CanaryConfigController.java
const pattern = /^[a-zA-Z0-9\_\-]*$/;
const isConfigNameValid: IConfigValidator = state => {
  const isValid = pattern.test(state.selectedConfig.config.name);
  return isValid
      ? null
      : { message: 'Canary config names must contain only letters,' +
                   ' numbers, dashes (-) and underscores (_).' };
};

const isGroupWeightsSumValid: IConfigValidator = state => {
  const weights = Object.values(state.selectedConfig.group.groupWeights);
  const sumOfWeights = weights.reduce((sum, weight) => sum + weight, 0);
  const groupWeightsSumIsValid = weights.length === 0 || sumOfWeights === 100;

  return groupWeightsSumIsValid
    ? null
    : { message: 'Metric group weights must sum to 100.' };
};

const isEveryGroupWeightValid: IConfigValidator = state => {
  const everyGroupWeightIsValid =
    Object.values(state.selectedConfig.group.groupWeights)
      .every(weight => weight >= 0);

  return everyGroupWeightIsValid
    ? null
    : { message: 'A group weight must be greater than or equal to 0.' };
};

const isEveryQueriedMetricStoreAvailable: IConfigValidator = state => {
  const available = chain(state.data.kayentaAccounts.data)
    .filter(account => account.supportedTypes.includes(KayentaAccountType.MetricsStore))
    .map(account => account.metricsStoreType || account.type)
    .uniq()
    .valueOf();

  const queried = chain(state.selectedConfig.metricList)
    .map(metric => metric.query.type)
    .uniq()
    .valueOf();

  const unavailableQueried = queried.filter(store => !available.includes(store));
  let message: string;
  if (unavailableQueried.length === 1) {
    message = `This config contains metrics configured to query a metric
               store (${unavailableQueried[0]})
               that does not have any configured accounts.`;
  } else if (unavailableQueried.length > 1) {
    message = `This config contains metrics configured to query metric
               stores (${unavailableQueried.join()})
               that do not have any configured accounts.`;
  }
  return unavailableQueried.length ? { message } : null;
};

const areMultipleMetricStoresQueried: IConfigValidator = state => {
  const queried = chain(state.selectedConfig.metricList)
    .map(metric => metric.query.type)
    .uniq()
    .valueOf();

  return queried.length > 1
    ? { message: 'All metrics must be from the same metric store.' }
    : null;
};

export const validationErrorsReducer = (state: ICanaryState): ICanaryState => {
  if (!state.selectedConfig) {
    return state;
  }

  if (!state.selectedConfig.validationErrors) {
    state = { ...state, selectedConfig: { ...state.selectedConfig, validationErrors: [] } };
  }

  return [
    isConfigNameUnique,
    isConfigNameValid,
    isGroupWeightsSumValid,
    isEveryGroupWeightValid,
    isEveryQueriedMetricStoreAvailable,
    areMultipleMetricStoresQueried,
  ].reduce((s, validator) => createValidationReducer(validator)(s), state);
};
