import { ICanaryConfig, ICanaryMetricConfig } from '../domain/ICanaryConfig';
import { ICanaryConfigSummary } from '../domain/ICanaryConfigSummary';

export interface ICanaryState {
  configSummaries: ICanaryConfigSummary[],
  selectedConfig: ICanaryConfig,
  metricList: ICanaryMetricConfig[],
  selectedMetric: ICanaryMetricConfig
}

export function rootReducer(state: ICanaryState, action: any): ICanaryState {
  switch (action.type) {
    case 'initialize':
      return action.state;
    case 'select_config':
      return Object.assign({}, state, {
        selectedConfig: action.config,
        metricList: action.config.metrics,
        selectedMetric: null
      });
    default:
      return state;
  }
}
