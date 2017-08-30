import { Action, combineReducers } from 'redux';
import { get, has } from 'lodash';

import * as Actions from '../actions';
import { DeleteConfigState } from '../edit/deleteModal';
import { SaveConfigState } from '../edit/save';
import { ConfigDetailLoadState } from '../edit/configDetailLoader';
import { IJudge } from '../domain/IJudge';
import {
  ICanaryClassifierThresholdsConfig, ICanaryConfig,
  ICanaryMetricConfig
} from '../domain/ICanaryConfig';
import { CanarySettings } from '../canary.settings';

export interface ISelectedConfigState {
  config: ICanaryConfig;
  metricList: ICanaryMetricConfig[];
  editingMetric: ICanaryMetricConfig;
  thresholds: ICanaryClassifierThresholdsConfig;
  judge: IJudge;
  group: IGroupState;
  load: ILoadState;
  save: ISaveState;
  destroy: IDestroyState;
  json: IJsonState;
}

function config(state: ICanaryConfig = null, action: Action & any): ICanaryConfig {
  switch (action.type) {
    case Actions.INITIALIZE:
      return action.state.selectedConfig.config;

    case Actions.SELECT_CONFIG:
      return action.config;

    case Actions.UPDATE_CONFIG_NAME:
      return Object.assign({}, state, { name: action.name });

    case Actions.UPDATE_CONFIG_DESCRIPTION:
      return Object.assign({}, state, { description: action.description });

    case Actions.DELETE_CONFIG_SUCCESS:
      return null;

    default:
      return state;
  }
}

interface ILoadState {
  state: ConfigDetailLoadState;
}

function makeLoadState(state: ConfigDetailLoadState): ILoadState {
  return { state };
}

function load(state: ILoadState = { state: ConfigDetailLoadState.Loading }, action: Action & any): ILoadState {
  switch (action.type) {
    case Actions.LOAD_CONFIG_REQUEST:
      return makeLoadState(ConfigDetailLoadState.Loading);

    case Actions.LOAD_CONFIG_FAILURE:
      return makeLoadState(ConfigDetailLoadState.Error);

    case Actions.SELECT_CONFIG:
      return makeLoadState(ConfigDetailLoadState.Loaded);

    default:
      return state;
  }
}

function idMetrics(metrics: ICanaryMetricConfig[] = []) {
  return metrics.map((metric, index) => Object.assign({}, metric, { id: '#' + index }));
}

function metricList(state: ICanaryMetricConfig[] = [], action: Action & any): ICanaryMetricConfig[] {
  switch (action.type) {
    case Actions.INITIALIZE:
      return idMetrics(action.state.selectedConfig.metricList);

    case Actions.SELECT_CONFIG:
      return idMetrics(action.config.metrics);

    case Actions.ADD_METRIC:
      return idMetrics(state.concat([action.metric]));

    case Actions.REMOVE_METRIC:
      return idMetrics(state.filter(metric => metric.id !== action.id));

    default:
      return state;
  }
}

function editingMetric(state: ICanaryMetricConfig = null, action: Action & any): ICanaryMetricConfig {
  switch (action.type) {
    case Actions.RENAME_METRIC:
      return Object.assign({}, state, { name: action.name });

    case Actions.UPDATE_STACKDRIVER_METRIC_TYPE:
      return Object.assign({}, state, { query: Object.assign({}, state.query || {}, {
        metricType: action.metricType,
        type: 'stackdriver',
      })});

    default:
      return state;
  }
}

interface ISaveState {
  state: SaveConfigState;
  error: string;
}

function makeSaveState(state: SaveConfigState, error: string = null): ISaveState {
  return { state, error };
}

function save(state: ISaveState = { state: SaveConfigState.Saved, error: null }, action: Action & any): ISaveState {
  switch (action.type) {
    case Actions.SAVE_CONFIG_REQUEST:
      return makeSaveState(SaveConfigState.Saving);

    case Actions.SAVE_CONFIG_SUCCESS:
      return makeSaveState(SaveConfigState.Saved);

    case Actions.SAVE_CONFIG_FAILURE:
      return makeSaveState(SaveConfigState.Error, get(action, 'error.data.message', null));

    case Actions.DISMISS_SAVE_CONFIG_ERROR:
      return makeSaveState(SaveConfigState.Saved);

    default:
      return state;
  }
}

interface IDestroyState {
  state: DeleteConfigState;
  error: string;
}

function makeDestroyState(state: DeleteConfigState, error: string = null): IDestroyState {
  return { state, error };
}

// Mixing destroy/delete here because delete is a JS keyword.
function destroy(state: IDestroyState = { state: DeleteConfigState.Completed, error: null }, action: Action & any): IDestroyState {
  switch (action.type) {
    case Actions.DELETE_CONFIG_REQUEST:
      return makeDestroyState(DeleteConfigState.Deleting);

    case Actions.DELETE_CONFIG_SUCCESS:
      return makeDestroyState(DeleteConfigState.Completed);

    case Actions.DELETE_CONFIG_FAILURE:
      return makeDestroyState(DeleteConfigState.Error, get(action, 'error.data.message', null));

    default:
      return state;
  }
}

interface IGroupState {
  list: string[];
  selected: string;
}

function makeGroupState(list: string[], selected: string = null): IGroupState {
  return { list, selected };
}

function group(state: IGroupState = { selected: null, list: [] }, action: Action & any): IGroupState {
  function groupsFromMetrics(metrics: ICanaryMetricConfig[] = []) {
    return metrics.reduce((groups, metric) => {
      return groups.concat(metric.groups.filter((group: string) => !groups.includes(group)))
    }, []).sort();
  }

  switch (action.type) {
    case Actions.INITIALIZE:
      return makeGroupState(groupsFromMetrics(action.state.selectedConfig.metricList), action.state.selectedConfig.group.selected);

    case Actions.SELECT_GROUP:
      return makeGroupState(state.list, action.name);

    case Actions.SELECT_CONFIG:
      return makeGroupState(groupsFromMetrics(action.config.metrics), state.selected);

    case Actions.ADD_METRIC: {
      const groups = action.metric.groups;
      return makeGroupState(state.list.concat(groups.filter((group: string) => !state.list.includes(group))), state.selected);
    }

    case Actions.ADD_GROUP:
      let n = 1;
      let name = null;
      do {
        name = 'Group ' + n;
        n++;
      } while (state.list.includes(name));
      return makeGroupState(state.list.concat([name]), state.selected);

    default:
      return state;
  }
}

interface IJsonState {
  state?: string;
  error?: string;
}

function makeJsonState(state: string = null, error: string = null): IJsonState {
  return { state, error };
}

function json(state: IJsonState = {}, action: Action & any): IJsonState {
  switch (action.type) {
    case Actions.SET_CONFIG_JSON:
      return makeJsonState(action.configJson);

    case Actions.EDIT_CONFIG_JSON_MODAL_CLOSE:
      return makeJsonState();

    case Actions.CONFIG_JSON_DESERIALIZATION_ERROR:
      return makeJsonState(state.state, action.error);

    case Actions.SELECT_CONFIG:
      return makeJsonState();

    default:
      return state;
  }
}

function judge(state: IJudge = null, action: Action & any): IJudge {
  switch (action.type) {
    case Actions.SELECT_JUDGE:
      return action.judge;

    default:
      return state;
  }
}

function thresholds(state: ICanaryClassifierThresholdsConfig = null, action: Action & any): ICanaryClassifierThresholdsConfig {
  switch (action.type) {
    case Actions.SELECT_CONFIG:
      if (has(action.config, 'classifier.scoreThresholds')) {
        return action.config.classifier.scoreThresholds;
      } else {
        return {
          pass: null,
          marginal: null,
        };
      }

    case Actions.UPDATE_SCORE_THRESHOLDS:
      return {
        pass: action.pass,
        marginal: action.marginal,
      };

    default:
      return state;
  }
}

// This reducer needs to be able to access both metricList and editingMetric so it won't fit the combineReducers paradigm.
function editingMetricReducer(state: ISelectedConfigState = null, action: Action & any): ISelectedConfigState {
  switch (action.type) {
    case Actions.EDIT_METRIC_BEGIN:
      return Object.assign({}, state, {
        editingMetric: state.metricList.find(metric => metric.id === action.id)
      });

    case Actions.EDIT_METRIC_CONFIRM:
      return Object.assign({}, state, {
        metricList: state.metricList.map(metric => metric.id === state.editingMetric.id ? state.editingMetric : metric),
        editingMetric: null
      });

    case Actions.EDIT_METRIC_CANCEL:
      return Object.assign({}, state, {
        editingMetric: null
      });

    default:
      return state;
  }
}

function selectedJudgeReducer(state: ISelectedConfigState = null, action: Action & any): ISelectedConfigState {
  switch (action.type) {
    case Actions.SELECT_CONFIG:
      if (has(state.config, 'metrics[0].analysisConfigurations.canary.judge')) {
        state.judge = { name: get(state.config, 'metrics[0].analysisConfigurations.canary.judge') };
      } else {
        state.judge = { name: CanarySettings.judge };
      }
      return state;

    default:
      return state;
  }
}

const combined = combineReducers<ISelectedConfigState>({
  config,
  load,
  save,
  destroy,
  json,
  judge,
  metricList,
  editingMetric,
  group,
  thresholds,
});

// First combine all simple reducers, then apply more complex ones as needed.
export const selectedConfig = (state: ISelectedConfigState, action: Action & any): ISelectedConfigState => {
  return [
    combined,
    editingMetricReducer,
    selectedJudgeReducer,
  ].reduce((s, reducer) => reducer(s, action), state);
};
