import { ICanaryConfig, ICanaryMetricConfig } from '../domain/ICanaryConfig';
import { ICanaryConfigSummary } from '../domain/ICanaryConfigSummary';
import { ConfigDetailLoadState } from '../edit/configDetailLoader';
import {
  CONFIG_LOAD_ERROR, INITIALIZE, LOAD_CONFIG,
  ADD_METRIC, RENAME_METRIC, SELECT_CONFIG, UPDATE_CONFIG_SUMMARIES
} from '../actions/index';

export interface ICanaryState {
  configSummaries: ICanaryConfigSummary[];
  selectedConfig: ICanaryConfig;
  configLoadState: ConfigDetailLoadState;
  metricList: ICanaryMetricConfig[];
  selectedMetric: ICanaryMetricConfig;
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

    default:
      return state;
  }
}
