import { get } from 'lodash';

import { ICanaryConfig, ICanaryMetricConfig } from '../domain/ICanaryConfig';
import { ICanaryConfigSummary } from '../domain/ICanaryConfigSummary';
import { ConfigDetailLoadState } from '../edit/configDetailLoader';
import {
  ADD_METRIC, SELECT_CONFIG, UPDATE_CONFIG_SUMMARIES,
  CONFIG_LOAD_ERROR, DISMISS_SAVE_CONFIG_ERROR, INITIALIZE, LOAD_CONFIG,
  RENAME_METRIC, SAVE_CONFIG_ERROR, SAVE_CONFIG_SAVING, SAVE_CONFIG_SAVED,
} from '../actions/index';
import { SaveConfigState } from '../edit/save';

export interface ICanaryState {
  configSummaries: ICanaryConfigSummary[];
  selectedConfig: ICanaryConfig;
  configLoadState: ConfigDetailLoadState;
  metricList: ICanaryMetricConfig[];
  selectedMetric: ICanaryMetricConfig;
  saveConfigState: SaveConfigState;
  saveConfigErrorMessage: string;
}

function reduceMetric(metric: ICanaryMetricConfig, id: string, action: any): ICanaryMetricConfig {
  if (id === action.id) {
    switch (action.type) {

      case RENAME_METRIC:
        return Object.assign({}, metric, { name: action.name });

      default:
        return metric;

    }
  } else {
    return metric;
  }
}

export function rootReducer(state: ICanaryState, action: any): ICanaryState {
  switch (action.type) {
    case INITIALIZE:
      return action.state;

    case UPDATE_CONFIG_SUMMARIES:
      return Object.assign({}, state, { configSummaries: action.configSummaries });

    case LOAD_CONFIG:
      return Object.assign({}, state, { configLoadState: ConfigDetailLoadState.Loading });

    case CONFIG_LOAD_ERROR:
      return Object.assign({}, state, { configLoadState: ConfigDetailLoadState.Error });

    case SELECT_CONFIG:
      return Object.assign({}, state, {
        selectedConfig: action.config,
        configLoadState: ConfigDetailLoadState.Loaded,
        metricList: action.config.metrics,
        selectedMetric: null
      });

    case ADD_METRIC:
      return Object.assign({}, state, {
        metricList: state.metricList.concat([action.metric]),
      });

    case RENAME_METRIC:
      return Object.assign({}, state, {
        metricList: state.metricList.map((metric, index) => reduceMetric(metric, String(index), action))
      });

    case SAVE_CONFIG_SAVING:
      return Object.assign({}, state, {
        saveConfigState: SaveConfigState.Saving,
        saveConfigErrorMessage: null,
      });

    case SAVE_CONFIG_SAVED:
      return Object.assign({}, state, {
        saveConfigState: SaveConfigState.Saved
      });

    case SAVE_CONFIG_ERROR:
      return Object.assign({}, state, {
        saveConfigState: SaveConfigState.Error,
        saveConfigErrorMessage: get(action, 'error.data.message', null),
      });

    case DISMISS_SAVE_CONFIG_ERROR:
      return Object.assign({}, state, {
        saveConfigState: SaveConfigState.Saved,
        saveConfigErrorMessage: null
      });

    default:
      return state;
  }
}
