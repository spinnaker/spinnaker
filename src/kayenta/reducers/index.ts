import { get } from 'lodash';
import { combineReducers, Action } from 'redux';
import { Application } from '@spinnaker/core';
import { ICanaryConfig, ICanaryMetricConfig } from '../domain/ICanaryConfig';
import { ICanaryConfigSummary } from '../domain/ICanaryConfigSummary';
import { ConfigDetailLoadState } from '../edit/configDetailLoader';
import {
  ADD_METRIC, SELECT_CONFIG, UPDATE_CONFIG_SUMMARIES,
  CONFIG_LOAD_ERROR, DISMISS_SAVE_CONFIG_ERROR, INITIALIZE, LOAD_CONFIG,
  RENAME_METRIC, SAVE_CONFIG_ERROR, SAVE_CONFIG_SAVING, SAVE_CONFIG_SAVED,
  EDIT_METRIC_BEGIN, EDIT_METRIC_CONFIRM, EDIT_METRIC_CANCEL,
  DELETE_CONFIG_MODAL_OPEN,
  DELETE_CONFIG_MODAL_CLOSE, DELETE_CONFIG_DELETING, DELETE_CONFIG_COMPLETED,
  DELETE_CONFIG_ERROR, ADD_GROUP, SELECT_GROUP, UPDATE_CONFIG_NAME,
  UPDATE_CONFIG_DESCRIPTION, EDIT_CONFIG_JSON_MODAL_OPEN,
  EDIT_CONFIG_JSON_MODAL_CLOSE,
  SET_CONFIG_JSON,
  CONFIG_JSON_DESERIALIZATION_ERROR, REMOVE_METRIC
} from '../actions/index';
import { SaveConfigState } from '../edit/save';
import { DeleteConfigState } from '../edit/deleteModal';

export interface ICanaryState {
  application: Application;
  configSummaries: ICanaryConfigSummary[];
  selectedConfig: ICanaryConfig;
  configLoadState: ConfigDetailLoadState;
  metricList: ICanaryMetricConfig[];
  editingMetric: ICanaryMetricConfig;
  groupList: string[],
  selectedGroup: string,
  saveConfigState: SaveConfigState;
  saveConfigErrorMessage: string;
  deleteConfigModalOpen: boolean;
  deleteConfigState: DeleteConfigState,
  deleteConfigErrorMessage: string;
  editConfigJsonModalOpen: boolean;
  configJson: string;
  configJsonDeserializationError: string;
}

function application(state: Application = null, action: Action & any): Application {
  switch (action.type) {
    case INITIALIZE:
      return action.state.application;

    default:
      return state;
  }
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

    case UPDATE_CONFIG_NAME:
      return Object.assign({}, state, { name: action.name });

    case UPDATE_CONFIG_DESCRIPTION:
      return Object.assign({}, state, { description: action.description });

    case DELETE_CONFIG_COMPLETED:
      return null;

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

    case REMOVE_METRIC:
      return idMetrics(state.filter(metric => metric.id !== action.id));

    default:
      return state;
  }
}

function editingMetric(state: ICanaryMetricConfig = null, action: Action & any): ICanaryMetricConfig {
  switch (action.type) {
    case RENAME_METRIC:
      return Object.assign({}, state, { name: action.name });

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

function editConfigJsonModalOpen(state = false, action: Action & any): boolean {
  switch (action.type) {
    case EDIT_CONFIG_JSON_MODAL_OPEN:
      return true;

    case EDIT_CONFIG_JSON_MODAL_CLOSE:
    case SELECT_CONFIG:
      return false;

    default:
      return state;
  }
}

function configJson(state: string = null, action: Action & any): string {
  switch (action.type) {
    case SET_CONFIG_JSON:
      return action.configJson;

    case EDIT_CONFIG_JSON_MODAL_CLOSE:
      return null;

    default:
      return state;
  }
}

function configJsonDeserializationError(state: string = null, action: Action & any): string {
  switch (action.type) {
    case CONFIG_JSON_DESERIALIZATION_ERROR:
      return action.error;

    case EDIT_CONFIG_JSON_MODAL_CLOSE:
    case SELECT_CONFIG:
      return null;

    default:
      return state;
  }
}

const combinedReducer = combineReducers<ICanaryState>({
  application,
  configSummaries,
  selectedConfig,
  configLoadState,
  metricList,
  editingMetric,
  saveConfigState,
  saveConfigErrorMessage,
  deleteConfigModalOpen,
  deleteConfigState,
  deleteConfigErrorMessage,
  groupList,
  selectedGroup,
  editConfigJsonModalOpen,
  configJson,
  configJsonDeserializationError,
});

// This reducer needs to be able to access both metricList and editingMetric so it won't fit the combineReducers paradigm.
function editingMetricReducer(state: ICanaryState = null, action: Action & any): ICanaryState {
  switch (action.type) {
    case EDIT_METRIC_BEGIN:
      return Object.assign({}, state, {
        editingMetric: state.metricList.find(metric => metric.id === action.id)
      });

    case EDIT_METRIC_CONFIRM:
      return Object.assign({}, state, {
        metricList: state.metricList.map(metric => metric.id === state.editingMetric.id ? state.editingMetric : metric),
        editingMetric: null
      });

    case EDIT_METRIC_CANCEL:
      return Object.assign({}, state, {
        editingMetric: null
      });

    default:
      return state;
  }
}

// First combine all simple reducers, then apply more complex ones as needed.
export const rootReducer = function(state: ICanaryState, action: Action & any) {
  const combined = combinedReducer(state, action);
  return editingMetricReducer(combined, action);
};
