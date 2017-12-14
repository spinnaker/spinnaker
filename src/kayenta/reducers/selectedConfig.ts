import { Action, combineReducers } from 'redux';
import { combineActions, handleActions } from 'redux-actions';
import { get, set, has, omit, chain, pick, fromPairs, flatMap, cloneDeep } from 'lodash';

import * as Actions from '../actions';
import { IJudge } from '../domain/IJudge';
import {
  IGroupWeights,
  ICanaryClassifierThresholdsConfig,
  ICanaryConfig,
  ICanaryJudgeConfig,
  ICanaryMetricConfig
} from '../domain/ICanaryConfig';
import { CanarySettings } from '../canary.settings';
import { IGroupState, group } from './group';
import { JudgeSelectRenderState } from '../edit/judgeSelect';
import { UNGROUPED, ALL } from '../edit/groupTabs';
import { AsyncRequestState } from './asyncRequest';
import { IConfigValidationError } from './validators';

export interface ILoadState {
  state: AsyncRequestState;
}

export interface ISaveState {
  state: AsyncRequestState;
  error: string;
}

// Mixing destroy/delete here because delete is a JS keyword.
export interface IDestroyState {
  state: AsyncRequestState;
  error: string;
}

export interface IJsonState {
  configJson: string;
  error: string;
}

export interface IJudgeState {
  judgeConfig: ICanaryJudgeConfig;
  renderState: JudgeSelectRenderState;
}

export interface IChangeMetricGroupState {
  toGroup: string;
}

export interface ISelectedConfigState {
  config: ICanaryConfig;
  isInSyncWithServer: boolean;
  metricList: ICanaryMetricConfig[];
  editingMetric: ICanaryMetricConfig;
  thresholds: ICanaryClassifierThresholdsConfig;
  judge: IJudgeState;
  group: IGroupState;
  load: ILoadState;
  save: ISaveState;
  destroy: IDestroyState;
  json: IJsonState;
  changeMetricGroup: IChangeMetricGroupState;
  validationErrors: IConfigValidationError[];
}

const config = handleActions({
  [Actions.SELECT_CONFIG]: (_state: ICanaryConfig, action: Action & any) => action.payload.config,
  [Actions.UPDATE_CONFIG_NAME]: (state: ICanaryConfig, action: Action & any) => ({ ...state, name: action.payload.name }),
  [Actions.UPDATE_CONFIG_DESCRIPTION]: (state: ICanaryConfig, action: Action & any) => ({ ...state, description: action.payload.description }),
  [combineActions(Actions.DELETE_CONFIG_SUCCESS, Actions.CLEAR_SELECTED_CONFIG)]: () => null,
}, null);

const load = combineReducers({
  state: handleActions({
    [Actions.LOAD_CONFIG_REQUEST]: () => AsyncRequestState.Requesting,
    [Actions.LOAD_CONFIG_FAILURE]: () => AsyncRequestState.Failed,
    [Actions.SELECT_CONFIG]: () => AsyncRequestState.Fulfilled,
  }, AsyncRequestState.Requesting),
});

function idMetrics(metrics: ICanaryMetricConfig[] = []) {
  return metrics.map((metric, index) => Object.assign({}, metric, { id: '#' + index }));
}

const metricList = handleActions({
  [Actions.SELECT_CONFIG]: (_state: ICanaryMetricConfig[], action: Action & any) => idMetrics(action.payload.config.metrics),
  [Actions.ADD_METRIC]: (state: ICanaryMetricConfig[], action: Action & any) => idMetrics(state.concat([action.payload.metric])),
  [Actions.REMOVE_METRIC]: (state: ICanaryMetricConfig[], action: Action & any) => idMetrics(state.filter(metric => metric.id !== action.payload.id)),
}, []);

const editingMetric = handleActions({
  [Actions.RENAME_METRIC]: (state: ICanaryMetricConfig, action: Action & any) => ({
    ...state, name: action.payload.name
  }),
  [Actions.UPDATE_METRIC_DIRECTION]: (state: ICanaryMetricConfig, action: Action & any) => (
    set(cloneDeep(state), ['analysisConfigurations', 'canary', 'direction'], action.payload.direction)
  ),
  [Actions.UPDATE_STACKDRIVER_METRIC_TYPE]: (state: ICanaryMetricConfig, action: Action & any) => ({
    ...state, query: { ...state.query, metricType: action.metricType, type: 'stackdriver' },
  }),
  [Actions.UPDATE_ATLAS_QUERY]: (state: ICanaryMetricConfig, action: Action & any) => ({
    ...state, query: { ...state.query, q: action.query }
  })
}, null);

const save = combineReducers<ISaveState>({
  state: handleActions({
    [Actions.SAVE_CONFIG_REQUEST]: () => AsyncRequestState.Requesting,
    [combineActions(Actions.SAVE_CONFIG_SUCCESS, Actions.DISMISS_SAVE_CONFIG_ERROR)]: () => AsyncRequestState.Fulfilled,
    [Actions.SAVE_CONFIG_FAILURE]: () => AsyncRequestState.Failed,
  }, AsyncRequestState.Fulfilled),
  error: handleActions({
    [Actions.SAVE_CONFIG_FAILURE]: (_state: string, action: Action & any) => get(action, 'payload.error.data.message', null),
  }, null),
});

const destroy = combineReducers<IDestroyState>({
  state: handleActions({
    [Actions.DELETE_CONFIG_REQUEST]: () => AsyncRequestState.Requesting,
    [Actions.DELETE_CONFIG_SUCCESS]: () => AsyncRequestState.Fulfilled,
    [Actions.DELETE_CONFIG_FAILURE]: () => AsyncRequestState.Failed,
  }, AsyncRequestState.Fulfilled),
  error: handleActions({
    [Actions.DELETE_CONFIG_FAILURE]: (_state: string, action: Action & any) => get(action, 'payload.error.data.message', null),
  }, null),
});

const json = combineReducers<IJsonState>({
  configJson: handleActions({
    [Actions.SET_CONFIG_JSON]: (_state: IJsonState, action: Action & any) => action.payload.json,
    [combineActions(Actions.CONFIG_JSON_MODAL_CLOSE, Actions.SELECT_CONFIG)]: (): void => null,
  }, null),
  error: handleActions({
    [combineActions(Actions.CONFIG_JSON_MODAL_CLOSE, Actions.SELECT_CONFIG, Actions.SET_CONFIG_JSON)]: () => null,
    [Actions.SET_CONFIG_JSON]: (_state: IJsonState, action: Action & any) => {
      try {
        JSON.parse(action.payload.json);
        return null;
      } catch (e) {
        return e.message;
      }
    }
  }, null),
});


const judge = combineReducers<IJudgeState>({
  judgeConfig: handleActions({
    [Actions.SELECT_JUDGE_NAME]: (state: IJudge, action: Action & any) => ({ ...state, name: action.payload.judge.name }),
  }, null),
  renderState: handleActions({}, JudgeSelectRenderState.None),
});

const thresholds = handleActions({
  [Actions.SELECT_CONFIG]: (_state: ICanaryClassifierThresholdsConfig, action: Action & any) => {
    if (has(action, 'payload.config.classifier.scoreThresholds')) {
      return action.payload.config.classifier.scoreThresholds;
    } else {
      return {
        pass: null,
        marginal: null,
      };
    }
  },
  [Actions.UPDATE_SCORE_THRESHOLDS]: (_state: ICanaryClassifierThresholdsConfig, action: Action & any) => ({
    pass: action.payload.pass, marginal: action.payload.marginal
  })
}, null);

const changeMetricGroup = combineReducers<IChangeMetricGroupState>({
  toGroup: handleActions({
    [Actions.CHANGE_METRIC_GROUP_SELECT]: (_state: string, action: Action & any) => action.payload.group,
  }, null),
});

const isInSyncWithServer = handleActions({}, null);

// This reducer needs to be able to access both metricList and editingMetric so it won't fit the combineReducers paradigm.
function editingMetricReducer(state: ISelectedConfigState = null, action: Action & any): ISelectedConfigState {
  switch (action.type) {
    case Actions.ADD_METRIC:
      return Object.assign({}, state, {
        editingMetric: state.metricList[state.metricList.length - 1]
      });

    case Actions.EDIT_METRIC_BEGIN:
      return Object.assign({}, state, {
        editingMetric: state.metricList.find(metric => metric.id === action.payload.id)
      });

    case Actions.EDIT_METRIC_CONFIRM:
      const editing: ICanaryMetricConfig = omit(state.editingMetric, 'isNew');
      return Object.assign({}, state, {
        metricList: state.metricList.map(metric => metric.id === editing.id ? editing : metric),
        editingMetric: null
      });

    case Actions.EDIT_METRIC_CANCEL:
      return Object.assign({}, state, {
        metricList: state.metricList.filter(metric => !metric.isNew),
        editingMetric: null
      });

    default:
      return state;
  }
}

function selectedJudgeReducer(state: ISelectedConfigState = null, action: Action & any): ISelectedConfigState {
  switch (action.type) {
    case Actions.SELECT_CONFIG:
      if (has(action, 'payload.config.judge')) {
        return { ...state, judge: { ...state.judge, judgeConfig: { ...action.payload.config.judge } }};
      } else {
        return { ...state, judge: { ...state.judge, judgeConfig: { name: CanarySettings.defaultJudge, judgeConfigurations: {} }}};
      }

    default:
      return state;
  }
}

export function editGroupConfirmReducer(state: ISelectedConfigState = null, action: Action & any): ISelectedConfigState {
  if (action.type !== Actions.EDIT_GROUP_CONFIRM) {
    return state;
  }

  const { payload: { group, edit } } = action;
  const allGroups = flatMap(state.metricList, metric => metric.groups);
  if (!edit || edit === UNGROUPED || edit === ALL || allGroups.includes(edit)) {
    return state;
  }

  const metricUpdator = (c: ICanaryMetricConfig): ICanaryMetricConfig => ({
    ...c,
    groups: (c.groups || []).includes(group) ? [edit].concat((c.groups || []).filter(g => g !== group)) : c.groups,
  });

  const weightsUpdator = (weights: IGroupWeights): IGroupWeights => {
    const weight = weights[group];
    weights = omit(weights, group);
    return {
      ...weights,
      [edit]: weight,
    };
  };

  const listUpdator = (groupList: string[]): string[] => [edit].concat((groupList || []).filter(g => g !== group));
  return {
    ...state,
    metricList: state.metricList.map(metricUpdator),
    group: {
      ...state.group,
      selected: edit,
      groupWeights: weightsUpdator(state.group.groupWeights),
      list: listUpdator(state.group.list),
    },
  };
}

export function changeMetricGroupConfirmReducer(state: ISelectedConfigState, action: Action & any): ISelectedConfigState {
  if (action.type !== Actions.CHANGE_METRIC_GROUP_CONFIRM) {
    return state;
  }

  const { changeMetricGroup: { toGroup } } = state;
  const { payload: { metricId } } = action;

  const metricUpdator = (m: ICanaryMetricConfig): ICanaryMetricConfig => ({
    ...m,
    groups: m.id === metricId
      ? (toGroup === UNGROUPED ? [] : [toGroup])
      : m.groups,
  });

  return {
    ...state,
    metricList: state.metricList.map(metricUpdator),
  };
}

export function updateGroupWeightsReducer(state: ISelectedConfigState, action: Action & any): ISelectedConfigState {
  if (![Actions.SELECT_CONFIG,
        Actions.CHANGE_METRIC_GROUP_CONFIRM,
        Actions.ADD_METRIC,
        Actions.REMOVE_METRIC].includes(action.type)) {
    return state;
  }

  const groups = chain(state.metricList)
    .flatMap(metric => metric.groups)
    .uniq()
    .value();

  // Prune weights for groups that no longer exist.
  let groupWeights: IGroupWeights = pick(state.group.groupWeights, groups);

  // Initialize weights for new groups.
  groupWeights = {
    ...fromPairs(groups.map(g => [g, 0])),
    ...groupWeights,
  };

  return {
    ...state,
    group: {
      ...state.group,
      groupWeights,
    }
  };
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
  changeMetricGroup,
  isInSyncWithServer,
  validationErrors: () => null,
});

// First combine all simple reducers, then apply more complex ones as needed.
export const selectedConfig = (state: ISelectedConfigState, action: Action & any): ISelectedConfigState => {
  return [
    combined,
    editingMetricReducer,
    selectedJudgeReducer,
    editGroupConfirmReducer,
    changeMetricGroupConfirmReducer,
    updateGroupWeightsReducer,
  ].reduce((s, reducer) => reducer(s, action), state);
};
