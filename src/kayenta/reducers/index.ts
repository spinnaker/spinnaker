import { ICanaryConfig, ICanaryMetricConfig } from '../domain/ICanaryConfig';
import { ICanaryConfigSummary } from '../domain/ICanaryConfigSummary';
import { ConfigDetailLoadState } from '../edit/configDetailLoader';

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

      case 'rename_metric':
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
    case 'initialize':
      return action.state;

    case 'update_config_summaries':
      return Object.assign({}, state, { configSummaries: action.configSummaries });

    case 'load_config':
      return Object.assign({}, state, { configLoadState: ConfigDetailLoadState.Loading });

    case 'config_load_error':
      return Object.assign({}, state, { configLoadState: ConfigDetailLoadState.Error });

    case 'select_config':
      return Object.assign({}, state, {
        selectedConfig: action.config,
        configLoadState: ConfigDetailLoadState.Loaded,
        metricList: action.config.metrics,
        selectedMetric: null
      });

    case 'rename_metric':
      return Object.assign({}, state, {
        metricList: state.metricList.map((metric, index) => reduceMetric(metric, String(index), action))
      });

    default:
      return state;
  }
}
