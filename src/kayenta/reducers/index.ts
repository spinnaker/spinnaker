import { get } from 'lodash';

import { combineReducers, Action } from 'redux';
import { ICanaryConfig, ICanaryMetricConfig } from '../domain/ICanaryConfig';
import { ICanaryConfigSummary } from '../domain/ICanaryConfigSummary';
import { ConfigDetailLoadState } from '../edit/configDetailLoader';
import {
  ADD_METRIC, SELECT_CONFIG, UPDATE_CONFIG_SUMMARIES,
  CONFIG_LOAD_ERROR, DISMISS_SAVE_CONFIG_ERROR, INITIALIZE, LOAD_CONFIG,
  RENAME_METRIC, SAVE_CONFIG_ERROR, SAVE_CONFIG_SAVING, SAVE_CONFIG_SAVED,
  DELETE_CONFIG_MODAL_OPEN,
  DELETE_CONFIG_MODAL_CLOSE, DELETE_CONFIG_DELETING, DELETE_CONFIG_COMPLETED,
  DELETE_CONFIG_ERROR, ADD_GROUP, SELECT_GROUP
} from '../actions/index';
import { SaveConfigState } from '../edit/save';
import { DeleteConfigState } from '../edit/deleteModal';

export interface ICanaryState {
  configSummaries: ICanaryConfigSummary[];
  selectedConfig: ICanaryConfig;
  configLoadState: ConfigDetailLoadState;
  metricList: ICanaryMetricConfig[];
  groupList: string[],
  selectedGroup: string,
  saveConfigState: SaveConfigState;
  saveConfigErrorMessage: string;
  deleteConfigModalOpen: boolean;
  deleteConfigState: DeleteConfigState,
  deleteConfigErrorMessage: string;
}

function configSummaries(state: ICanaryConfigSummary[] = [], action: Action & any): ICanaryConfigSummary[] {
  switch (action.type) {
    case INITIALIZE:
      return action.state.configSummaries;

    case UPDATE_CONFIG_SUMMARIES:
      return action.configSummaries;

    default:
      return state;
  }
}

function selectedConfig(state: ICanaryConfig = null, action: Action & any): ICanaryConfig {
  switch (action.type) {
    case INITIALIZE:
      return action.state.selectedConfig;

    case SELECT_CONFIG:
      return action.config;

    default:
      return state;
  }
}

function configLoadState(state: ConfigDetailLoadState = ConfigDetailLoadState.Loading, action: Action & any): ConfigDetailLoadState {
  switch (action.type) {
    case LOAD_CONFIG:
      return ConfigDetailLoadState.Loading;

    case CONFIG_LOAD_ERROR:
      return ConfigDetailLoadState.Error;

    case SELECT_CONFIG:
      return ConfigDetailLoadState.Loaded;

    default:
      return state;
  }
}

function reduceMetric(metric: ICanaryMetricConfig, action: Action & any): ICanaryMetricConfig {
  if (metric.id === action.id) {
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

function idMetrics(metrics: ICanaryMetricConfig[]) {
  return metrics.map((metric, index) => Object.assign({}, metric, { id: '#' + index }));
}

function metricList(state: ICanaryMetricConfig[] = [], action: Action & any): ICanaryMetricConfig[] {
  switch (action.type) {
    case INITIALIZE:
      return idMetrics(action.state.metricList);

    case SELECT_CONFIG:
      return idMetrics(action.config.metrics);

    case ADD_METRIC:
      return idMetrics(state.concat([action.metric]));

    case RENAME_METRIC:
      return state.map(metric => reduceMetric(metric, action));

    default:
      return state;
  }
}

function saveConfigState(state: SaveConfigState = SaveConfigState.Saved, action: Action & any): SaveConfigState {
  switch (action.type) {
    case SAVE_CONFIG_SAVING:
      return SaveConfigState.Saving;

    case SAVE_CONFIG_SAVED:
      return SaveConfigState.Saved;

    case SAVE_CONFIG_ERROR:
      return SaveConfigState.Error;

    case DISMISS_SAVE_CONFIG_ERROR:
      return SaveConfigState.Saved;

    default:
      return state;
  }
}

function saveConfigErrorMessage(state: string = null, action: Action & any): string {
  switch (action.type) {
    case SAVE_CONFIG_SAVING:
      return null;

    case SAVE_CONFIG_ERROR:
      return get(action, 'error.data.message', null);

    case DISMISS_SAVE_CONFIG_ERROR:
      return null;

    default:
      return state;
  }
}

function deleteConfigModalOpen(state = false, action: Action & any): boolean {
  switch (action.type) {
    case DELETE_CONFIG_MODAL_OPEN:
      return true;

    case DELETE_CONFIG_MODAL_CLOSE:
      return false;

    case DELETE_CONFIG_COMPLETED:
      return false;

    default:
      return state;
  }
}

function deleteConfigState(state = DeleteConfigState.Completed, action: Action & any): DeleteConfigState {
  switch (action.type) {
    case DELETE_CONFIG_DELETING:
      return DeleteConfigState.Deleting;

    case DELETE_CONFIG_COMPLETED:
      return DeleteConfigState.Completed;

    case DELETE_CONFIG_ERROR:
      return DeleteConfigState.Error;

    default:
      return state;
  }
}

function deleteConfigErrorMessage(state: string = null, action: Action & any): string {
  switch (action.type) {
    case DELETE_CONFIG_DELETING:
      return null;

    case DELETE_CONFIG_COMPLETED:
      return null;

    case DELETE_CONFIG_ERROR:
      return get(action, 'error.data.message', null);

    default:
      return state;
  }
}

function groupList(state: string[] = [], action: Action & any): string[] {
  function groupsFromMetrics(metrics: ICanaryMetricConfig[] = []) {
    return metrics.reduce((groups, metric) => {
      return groups.concat(metric.groups.filter((group: string) => !groups.includes(group)))
    }, []).sort();
  }

  switch (action.type) {
    case INITIALIZE:
      return groupsFromMetrics(action.state.metricList);

    case SELECT_CONFIG:
      return groupsFromMetrics(action.config.metrics);

    case ADD_METRIC: {
      const groups = action.metric.groups;
      return state.concat(groups.filter((group: string) => !state.includes(group)));
    }

    case ADD_GROUP:
      let n = 1;
      let name = null;
      do {
        name = 'Group ' + n;
        n++;
      } while (state.includes(name));
      return state.concat([name]);

    default:
      return state;
  }
}

function selectedGroup(state: string = null, action: Action & any): string {
  switch (action.type) {
    case SELECT_GROUP:
      return action.name;

    default:
      return state;
  }
}

export const rootReducer = combineReducers<ICanaryState>({
  configSummaries,
  selectedConfig,
  configLoadState,
  metricList,
  saveConfigState,
  saveConfigErrorMessage,
  deleteConfigModalOpen,
  deleteConfigState,
  deleteConfigErrorMessage,
  groupList,
  selectedGroup
});
