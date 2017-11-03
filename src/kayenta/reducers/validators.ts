import { ICanaryState } from './index';

const isConfigNameUnique = (state: ICanaryState): ICanaryState => {
  if (!state.selectedConfig.config) {
    return state;
  }

  const selectedConfig = state.selectedConfig.config;
  const configSummaries = state.data.configSummaries;
  const isUnique = configSummaries.every(s =>
    selectedConfig.name !== s.name || selectedConfig.id === s.id
  );

  const configValidationErrors = state.configValidationErrors.concat(
    isUnique
      ? []
      : [`Canary config '${selectedConfig.name}' already exists.`]
  );

  return {
    ...state,
    configValidationErrors,
  };
};

// See https://github.com/Netflix-Skunkworks/kayenta/blob/master/kayenta-web/src/main/java/com/netflix/kayenta/controllers/CanaryConfigController.java
const pattern = /^[a-zA-Z0-9\_\-]*$/;

const isConfigNameValid = (state: ICanaryState): ICanaryState => {
  if (!state.selectedConfig.config) {
    return state;
  }

  const isValid = pattern.test(state.selectedConfig.config.name);
  const configValidationErrors = state.configValidationErrors.concat(
    isValid
      ? []
      : ['Canary config names must contain only letters, numbers, dashes (-) and underscores (_).']
  );

  return {
    ...state,
    configValidationErrors,
  };
};

const isGroupWeightsValid = (state: ICanaryState): ICanaryState => {
  if (!state.selectedConfig.config) {
    return state;
  }

  const groupWeightsSum =
    Object.values(state.selectedConfig.group.groupWeights)
      .reduce((sum, weight) => sum + weight, 0);

  const configValidationErrors = state.configValidationErrors.concat(
    groupWeightsSum === 100
      ? []
      : ['Metric group weights must sum to 100.']
  );

  return {
    ...state,
    configValidationErrors,
  };
};


export const validationErrorsReducer = (state: ICanaryState): ICanaryState => {
  if (!state.configValidationErrors) {
    state = { ...state, configValidationErrors: [] };
  }

  return [
    isConfigNameUnique,
    isConfigNameValid,
    isGroupWeightsValid,
  ].reduce((s, reducer) => reducer(s), state);
};
