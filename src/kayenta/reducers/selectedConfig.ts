import { Action, combineReducers } from 'redux';
import { combineActions, handleActions } from 'redux-actions';
import { get, has } from 'lodash';

import * as Actions from '../actions';
import { DeleteConfigState } from '../edit/deleteModal';
import { SaveConfigState } from '../edit/save';
import { ConfigDetailLoadState } from '../edit/configDetailLoader';
import { IJudge } from '../domain/IJudge';
import {
  ICanaryClassifierThresholdsConfig,
  ICanaryConfig,
  ICanaryJudgeConfig,
  ICanaryMetricConfig
} from '../domain/ICanaryConfig';
import { CanarySettings } from '../canary.settings';
import { IGroupState, group } from './group';

interface ILoadState {
  state: ConfigDetailLoadState;
}

interface ISaveState {
  state: SaveConfigState;
  error: string;
}

// Mixing destroy/delete here because delete is a JS keyword.
interface IDestroyState {
  state: DeleteConfigState;
  error: string;
}

interface IJsonState {
  configJson: string;
  error: string;
}

export interface ISelectedConfigState {
  config: ICanaryConfig;
  metricList: ICanaryMetricConfig[];
  editingMetric: ICanaryMetricConfig;
  thresholds: ICanaryClassifierThresholdsConfig;
  judge: ICanaryJudgeConfig;
  group: IGroupState;
  load: ILoadState;
  save: ISaveState;
  destroy: IDestroyState;
  json: IJsonState;
}

const config = handleActions({
  [Actions.SELECT_CONFIG]: (_state: ICanaryConfig, action: Action & any) => action.config,
  [Actions.UPDATE_CONFIG_NAME]: (state: ICanaryConfig, action: Action & any) => ({ ...state, name: action.name }),
  [Actions.UPDATE_CONFIG_DESCRIPTION]: (state: ICanaryConfig, action: Action & any) => ({ ...state, description: action.description }),
  [Actions.DELETE_CONFIG_SUCCESS]: () => null,
}, null);

const load = combineReducers({
  state: handleActions({
    [Actions.LOAD_CONFIG_REQUEST]: () => ConfigDetailLoadState.Loading,
    [Actions.LOAD_CONFIG_FAILURE]: () => ConfigDetailLoadState.Error,
    [Actions.SELECT_CONFIG]: () => ConfigDetailLoadState.Loaded,
  }, ConfigDetailLoadState.Loading),
});

function idMetrics(metrics: ICanaryMetricConfig[] = []) {
  return metrics.map((metric, index) => Object.assign({}, metric, { id: '#' + index }));
}

const metricList = handleActions({
  [Actions.SELECT_CONFIG]: (_state: ICanaryMetricConfig[], action: Action & any) => idMetrics(action.config.metrics),
  [Actions.ADD_METRIC]: (state: ICanaryMetricConfig[], action: Action & any) => idMetrics(state.concat([action.metric])),
  [Actions.REMOVE_METRIC]: (state: ICanaryMetricConfig[], action: Action & any) => idMetrics(state.filter(metric => metric.id !== action.id)),
}, []);

const editingMetric = handleActions({
  [Actions.RENAME_METRIC]: (state: ICanaryMetricConfig, action: Action & any) => ({ ...state, name: action.name }),
  [Actions.UPDATE_STACKDRIVER_METRIC_TYPE]: (state: ICanaryMetricConfig, action: Action & any) => ({
    ...state, query: { ...state.query, metricType: action.metricType, type: 'stackdriver' },
  })
}, null);

const save = combineReducers<ISaveState>({
  state: handleActions({
    [Actions.SAVE_CONFIG_REQUEST]: () => SaveConfigState.Saving,
    [Actions.SAVE_CONFIG_SUCCESS]: () => SaveConfigState.Saved,
    [Actions.SAVE_CONFIG_FAILURE]: () => SaveConfigState.Error,
    [Actions.DISMISS_SAVE_CONFIG_ERROR]: () => SaveConfigState.Saved,
  }, SaveConfigState.Saved),
  error: handleActions({
    [Actions.SAVE_CONFIG_FAILURE]: (_state: string, action: Action & any) => get(action, 'error.data.message', null),
  }, null),
});

const destroy = combineReducers<IDestroyState>({
  state: handleActions({
    [Actions.DELETE_CONFIG_REQUEST]: () => DeleteConfigState.Deleting,
    [Actions.DELETE_CONFIG_SUCCESS]: () => DeleteConfigState.Completed,
    [Actions.DELETE_CONFIG_FAILURE]: () => DeleteConfigState.Error,
  }, DeleteConfigState.Completed),
  error: handleActions({
    [Actions.DELETE_CONFIG_FAILURE]: (_state: string, action: Action & any) => get(action, 'error.data.message', null),
  }, null),
});

const json = combineReducers<IJsonState>({
  configJson: handleActions({
    [Actions.SET_CONFIG_JSON]: (_state: IJsonState, action: Action & any) => action.payload,
    [combineActions(Actions.CONFIG_JSON_MODAL_CLOSE, Actions.SELECT_CONFIG)]: (): void => null,
  }, null),
  error: handleActions({
    [combineActions(Actions.CONFIG_JSON_MODAL_CLOSE, Actions.SELECT_CONFIG, Actions.SET_CONFIG_JSON)]: () => null,
    [Actions.SET_CONFIG_JSON]: (_state: IJsonState, action: Action & any) => {
      try {
        JSON.parse(action.payload);
        return null;
      } catch (e) {
        return e.message;
      }
    }
  }, null),
});

const judge = handleActions({
  [Actions.SELECT_JUDGE_NAME]: (state: IJudge, action: Action & any) => ({ ...state, name: action.judge.name }),
}, null);

const thresholds = handleActions({
  [Actions.SELECT_CONFIG]: (_state: ICanaryClassifierThresholdsConfig, action: Action & any) => {
    if (has(action.config, 'classifier.scoreThresholds')) {
      return action.config.classifier.scoreThresholds;
    } else {
      return {
        pass: null,
        marginal: null,
      };
    }
  },
  [Actions.UPDATE_SCORE_THRESHOLDS]: (_state: ICanaryClassifierThresholdsConfig, action: Action & any) => ({
    pass: action.pass, marginal: action.marginal
  })
}, null);

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
      if (state.config && state.config.judge) {
        return { ...state, judge: { ...state.config.judge }};
      } else {
        return { ...state, judge: { name: CanarySettings.judge, judgeConfigurations: {} }};
      }

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
