import { ICanaryState } from './index';

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
  ].reduce((s, validator) => createValidationReducer(validator)(s), state);
};
