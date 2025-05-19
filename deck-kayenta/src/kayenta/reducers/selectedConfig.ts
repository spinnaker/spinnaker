import { chain, cloneDeep, flatMap, fromPairs, get, has, omit, pick, set, unset } from 'lodash';
import { Action, combineReducers } from 'redux';
import { combineActions, handleActions } from 'redux-actions';

import * as Actions from '../actions';
import { AsyncRequestState } from './asyncRequest';
import { CanarySettings } from '../canary.settings';
import { ICanaryConfig, ICanaryJudgeConfig, ICanaryMetricConfig, IGroupWeights } from '../domain/ICanaryConfig';
import { IJudge } from '../domain/IJudge';
import { ALL } from '../edit/groupTabs';
import { JudgeSelectRenderState } from '../edit/judgeSelect';
import { editingTemplate, IEditingTemplateState } from './editingTemplate';
import { group, IGroupState } from './group';
import { prometheusMetricConfigReducer } from './prometheusMetricConfig';
import { signalFxMetricConfigReducer } from './signalFxMetricConfig';
import { stackdriverMetricConfigReducer } from './stackdriverMetricConfig';
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
  metric: string;
  toGroup: string;
}

export interface ISelectedConfigState {
  config: ICanaryConfig;
  isInSyncWithServer: boolean;
  metricList: ICanaryMetricConfig[];
  editingMetric: ICanaryMetricConfig;
  judge: IJudgeState;
  group: IGroupState;
  load: ILoadState;
  save: ISaveState;
  destroy: IDestroyState;
  json: IJsonState;
  changeMetricGroup: IChangeMetricGroupState;
  validationErrors: IConfigValidationError[];
  editingTemplate: IEditingTemplateState;
  selectedStore: string;
}

const config = handleActions(
  {
    [Actions.SELECT_CONFIG]: (_state: ICanaryConfig, action: Action & any) => action.payload.config,
    [Actions.UPDATE_CONFIG_NAME]: (state: ICanaryConfig, action: Action & any) => ({
      ...state,
      name: action.payload.name,
    }),
    [Actions.UPDATE_CONFIG_DESCRIPTION]: (state: ICanaryConfig, action: Action & any) => ({
      ...state,
      description: action.payload.description,
    }),
    [combineActions(Actions.DELETE_CONFIG_SUCCESS, Actions.CLEAR_SELECTED_CONFIG)]: () => null,
    [Actions.DELETE_TEMPLATE]: (state: ICanaryConfig, action: Action & any) => {
      if (!action.payload.name) {
        return state;
      }

      return {
        ...state,
        templates: omit(state.templates, action.payload.name),
      };
    },
  },
  null,
);

const load = combineReducers({
  state: handleActions(
    {
      [Actions.LOAD_CONFIG_REQUEST]: () => AsyncRequestState.Requesting,
      [Actions.LOAD_CONFIG_FAILURE]: () => AsyncRequestState.Failed,
      [Actions.SELECT_CONFIG]: () => AsyncRequestState.Fulfilled,
    },
    AsyncRequestState.Requesting,
  ),
});

function idMetrics(metrics: ICanaryMetricConfig[] = []) {
  return metrics.map((metric, index) => ({ ...metric, id: '#' + index }));
}

const metricList = handleActions(
  {
    [Actions.SELECT_CONFIG]: (_state: ICanaryMetricConfig[], action: Action & any) =>
      idMetrics(action.payload.config.metrics),
    [Actions.ADD_METRIC]: (state: ICanaryMetricConfig[], action: Action & any) =>
      idMetrics(state.concat([action.payload.metric])),
    [Actions.REMOVE_METRIC]: (state: ICanaryMetricConfig[], action: Action & any) =>
      idMetrics(state.filter((metric) => metric.id !== action.payload.id)),
    [Actions.DELETE_TEMPLATE]: (state: ICanaryMetricConfig[], action: Action & any) =>
      state.map((m) => removeDeletedTemplateFromMetric(m, action.payload.name)),
  },
  [],
);

function removeDeletedTemplateFromMetric(metric: ICanaryMetricConfig, deletedTemplateName: string) {
  if (get(metric, 'query.customFilterTemplate') === deletedTemplateName) {
    return {
      ...metric,
      query: {
        ...metric.query,
        customFilterTemplate: null,
      },
    };
  }
  return metric;
}

const editingMetric = handleActions(
  {
    [Actions.RENAME_METRIC]: (state: ICanaryMetricConfig, action: Action & any) => ({
      ...state,
      name: action.payload.name,
    }),
    [Actions.UPDATE_METRIC_DIRECTION]: (state: ICanaryMetricConfig, action: Action & any) =>
      set(cloneDeep(state), ['analysisConfigurations', 'canary', 'direction'], action.payload.direction),
    [Actions.UPDATE_METRIC_NAN_STRATEGY]: (state: ICanaryMetricConfig, { payload }: Action & any) => {
      const newState = cloneDeep(state);

      payload.strategy === 'default'
        ? unset(newState, ['analysisConfigurations', 'canary', 'nanStrategy'])
        : set(newState, ['analysisConfigurations', 'canary', 'nanStrategy'], payload.strategy);

      return newState;
    },
    [Actions.UPDATE_METRIC_OUTLIER_STRATEGY]: (state: ICanaryMetricConfig, { payload }: Action & any) => {
      const newState = cloneDeep(state);

      payload.strategy === 'default'
        ? unset(newState, ['analysisConfigurations', 'canary', 'outliers', 'strategy'])
        : set(newState, ['analysisConfigurations', 'canary', 'outliers', 'strategy'], payload.strategy);

      return newState;
    },
    [Actions.UPDATE_METRIC_CRITICALITY]: (state: ICanaryMetricConfig, { payload }: Action & any) => {
      const newState = cloneDeep(state);

      payload.critical
        ? set(newState, ['analysisConfigurations', 'canary', 'critical'], payload.critical)
        : unset(newState, ['analysisConfigurations', 'canary', 'critical']);

      return newState;
    },
    [Actions.UPDATE_METRIC_DATA_REQUIRED]: (state: ICanaryMetricConfig, { payload }: Action & any) => {
      const newState = cloneDeep(state);
      payload.mustHaveData
        ? set(newState, ['analysisConfigurations', 'canary', 'mustHaveData'], payload.mustHaveData)
        : unset(newState, ['analysisConfigurations', 'canary', 'mustHaveData']);

      return newState;
    },
    [Actions.UPDATE_EFFECT_SIZE]: (state: ICanaryMetricConfig, { payload }: Action & any) => {
      const newState = cloneDeep(state);
      Object.keys(payload.value).length
        ? set(newState, ['analysisConfigurations', 'canary', 'effectSize'], payload.value)
        : unset(newState, ['analysisConfigurations', 'canary', 'effectSize']);
      return newState;
    },
    [Actions.UPDATE_METRIC_GROUP]: (state: ICanaryMetricConfig, action: Action & any) => ({
      ...state,
      groups: [action.payload.group],
    }),
    [Actions.UPDATE_ATLAS_QUERY]: (state: ICanaryMetricConfig, action: Action & any) => ({
      ...state,
      query: { ...state.query, q: action.query },
    }),
    [Actions.UPDATE_DATADOG_METRIC_NAME]: (state: ICanaryMetricConfig, action: Action & any) => ({
      ...state,
      query: { ...state.query, metricName: action.payload.metricName },
    }),
    [Actions.UPDATE_NEWRELIC_SELECT]: (state: ICanaryMetricConfig, action: Action & any) => ({
      ...state,
      query: { ...state.query, select: action.payload.select },
    }),
    [Actions.SELECT_TEMPLATE]: (state: ICanaryMetricConfig, action: Action & any) => ({
      ...state,
      query: { ...state.query, customFilterTemplate: action.payload.name },
    }),
    [Actions.UPDATE_METRIC_SCOPE_NAME]: (state: ICanaryMetricConfig, action: Action & any) => ({
      ...state,
      scopeName: action.payload.scopeName,
    }),
    [Actions.UPDATE_GRAPHITE_METRIC_NAME]: (state: ICanaryMetricConfig, action: Action & any) => ({
      ...state,
      query: { ...state.query, metricName: action.payload.metricName },
    }),
    [Actions.EDIT_INLINE_TEMPLATE]: (state: ICanaryMetricConfig, action: Action & any) => ({
      ...state,
      query: { ...state.query, customInlineTemplate: action.payload.value },
    }),
  },
  null,
);

const save = combineReducers<ISaveState>({
  state: handleActions(
    {
      [Actions.SAVE_CONFIG_REQUEST]: () => AsyncRequestState.Requesting,
      [combineActions(Actions.SAVE_CONFIG_SUCCESS, Actions.DISMISS_SAVE_CONFIG_ERROR)]: () =>
        AsyncRequestState.Fulfilled,
      [Actions.SAVE_CONFIG_FAILURE]: () => AsyncRequestState.Failed,
    },
    AsyncRequestState.Fulfilled,
  ),
  error: handleActions(
    {
      [Actions.SAVE_CONFIG_FAILURE]: (_state: string, action: Action & any) =>
        get(action, 'payload.error.data.message', null),
    },
    null,
  ),
});

const destroy = combineReducers<IDestroyState>({
  state: handleActions(
    {
      [Actions.DELETE_CONFIG_REQUEST]: () => AsyncRequestState.Requesting,
      [Actions.DELETE_CONFIG_SUCCESS]: () => AsyncRequestState.Fulfilled,
      [Actions.DELETE_CONFIG_FAILURE]: () => AsyncRequestState.Failed,
    },
    AsyncRequestState.Fulfilled,
  ),
  error: handleActions(
    {
      [Actions.DELETE_CONFIG_FAILURE]: (_state: string, action: Action & any) =>
        get(action, 'payload.error.data.message', null),
    },
    null,
  ),
});

const json = combineReducers<IJsonState>({
  configJson: handleActions(
    {
      [Actions.SET_CONFIG_JSON]: (_state: IJsonState, action: Action & any) => action.payload.json,
      [combineActions(Actions.CONFIG_JSON_MODAL_CLOSE, Actions.SELECT_CONFIG)]: (): void => null,
    },
    null,
  ),
  error: handleActions(
    {
      [combineActions(Actions.CONFIG_JSON_MODAL_CLOSE, Actions.SELECT_CONFIG, Actions.SET_CONFIG_JSON)]: () => null,
      [Actions.SET_CONFIG_JSON]: (_state: IJsonState, action: Action & any) => {
        try {
          const parsed: ICanaryConfig = JSON.parse(action.payload.json);
          parsed.metrics.forEach((m, index) => {
            if (!m.groups || !m.groups.length) {
              throw new Error(`metric #${index + 1}: 'groups' is a required field`);
            }
          });
          return null;
        } catch (e) {
          return e.message;
        }
      },
    },
    null,
  ),
});

const judge = combineReducers<IJudgeState>({
  judgeConfig: handleActions(
    {
      [Actions.SELECT_JUDGE_NAME]: (state: IJudge, action: Action & any) => ({
        ...state,
        name: action.payload.judge.name,
      }),
    },
    null,
  ),
  renderState: handleActions({}, JudgeSelectRenderState.None),
});

const changeMetricGroup = combineReducers<IChangeMetricGroupState>({
  toGroup: handleActions(
    {
      [Actions.CHANGE_METRIC_GROUP_SELECT]: (_state: string, action: Action & any) => action.payload.group,
      [Actions.CHANGE_METRIC_GROUP]: () => null,
    },
    null,
  ),
  metric: handleActions(
    {
      [Actions.CHANGE_METRIC_GROUP]: (_state: string, action: Action & any) => action.payload.id,
      [Actions.CHANGE_METRIC_GROUP_CONFIRM]: () => null,
    },
    null,
  ),
});

const isInSyncWithServer = handleActions({}, null);

// This reducer needs to be able to access both metricList and editingMetric so it won't fit the combineReducers paradigm.
function editingMetricReducer(state: ISelectedConfigState = null, action: Action & any): ISelectedConfigState {
  switch (action.type) {
    case Actions.ADD_METRIC:
      return {
        ...state,
        editingMetric: state.metricList[state.metricList.length - 1],
      };

    case Actions.EDIT_METRIC_BEGIN:
      return {
        ...state,
        editingMetric: state.metricList.find((metric) => metric.id === action.payload.id),
      };

    case Actions.EDIT_METRIC_CONFIRM: {
      const editing: ICanaryMetricConfig = omit(state.editingMetric, 'isNew');
      return {
        ...state,
        metricList: state.metricList.map((metric) => (metric.id === editing.id ? editing : metric)),
        editingMetric: null,
      };
    }

    case Actions.EDIT_METRIC_CANCEL:
      return {
        ...state,
        metricList: state.metricList.filter((metric) => !metric.isNew),
        editingMetric: null,
      };

    default:
      return state;
  }
}

function selectedJudgeReducer(state: ISelectedConfigState = null, action: Action & any): ISelectedConfigState {
  switch (action.type) {
    case Actions.SELECT_CONFIG:
      if (has(action, 'payload.config.judge')) {
        return { ...state, judge: { ...state.judge, judgeConfig: { ...action.payload.config.judge } } };
      } else {
        return {
          ...state,
          judge: {
            ...state.judge,
            judgeConfig: { name: CanarySettings.defaultJudge || 'NetflixACAJudge-v1.0', judgeConfigurations: {} },
          },
        };
      }

    default:
      return state;
  }
}

export function editGroupConfirmReducer(
  state: ISelectedConfigState = null,
  action: Action & any,
): ISelectedConfigState {
  if (action.type !== Actions.EDIT_GROUP_CONFIRM) {
    return state;
  }

  const { payload } = action;
  const allGroups = flatMap(state.metricList, (metric) => metric.groups);
  if (!payload.edit || payload.edit === ALL || allGroups.includes(payload.edit)) {
    return state;
  }

  const metricUpdater = (c: ICanaryMetricConfig): ICanaryMetricConfig => ({
    ...c,
    groups: (c.groups || []).includes(payload.group)
      ? [payload.edit].concat((c.groups || []).filter((g) => g !== payload.group))
      : c.groups,
  });

  const weightsUpdater = (weights: IGroupWeights): IGroupWeights => {
    const weight = weights[payload.group];
    weights = omit(weights, payload.group);
    return {
      ...weights,
      [payload.edit]: weight,
    };
  };

  const listUpdater = (groupList: string[]): string[] =>
    [payload.edit].concat((groupList || []).filter((g) => g !== payload.group));
  return {
    ...state,
    metricList: state.metricList.map(metricUpdater),
    group: {
      ...state.group,
      selected: payload.edit,
      groupWeights: weightsUpdater(state.group.groupWeights),
      list: listUpdater(state.group.list),
    },
  };
}

export function changeMetricGroupConfirmReducer(
  state: ISelectedConfigState,
  action: Action & any,
): ISelectedConfigState {
  if (action.type !== Actions.CHANGE_METRIC_GROUP_CONFIRM) {
    return state;
  }

  const {
    changeMetricGroup: { toGroup },
  } = state;
  const {
    payload: { metricId },
  } = action;
  if (!metricId) {
    return state;
  }

  const metricUpdater = (m: ICanaryMetricConfig): ICanaryMetricConfig => ({
    ...m,
    groups: m.id === metricId ? [toGroup] : m.groups,
  });

  return {
    ...state,
    metricList: state.metricList.map(metricUpdater),
  };
}

export function updateGroupWeightsReducer(state: ISelectedConfigState, action: Action & any): ISelectedConfigState {
  if (
    ![Actions.SELECT_CONFIG, Actions.CHANGE_METRIC_GROUP_CONFIRM, Actions.ADD_METRIC, Actions.REMOVE_METRIC].includes(
      action.type,
    )
  ) {
    return state;
  }

  const groups = chain(state.metricList)
    .flatMap((metric) => metric.groups)
    .uniq()
    .value();

  // Prune weights for groups that no longer exist.
  let groupWeights: IGroupWeights = pick(state.group.groupWeights, groups);

  // Initialize weights for new groups.
  groupWeights = {
    ...fromPairs(groups.map((g) => [g, 0])),
    ...groupWeights,
  };

  return {
    ...state,
    group: {
      ...state.group,
      groupWeights,
    },
  };
}

const editingTemplateConfirmReducer = (state: ISelectedConfigState, action: Action & any): ISelectedConfigState => {
  if (action.type !== Actions.EDIT_TEMPLATE_CONFIRM) {
    return state;
  }

  const { name, editedName, editedValue, isNew } = state.editingTemplate;
  const templates = {
    ...(name ? omit(state.config.templates, name) : state.config.templates),
    [editedName]: editedValue,
  };

  const metricUpdater = (metric: ICanaryMetricConfig) => {
    if (get(metric, 'query.customFilterTemplate') !== name) {
      return metric;
    }
    return {
      ...metric,
      query: {
        ...metric.query,
        customFilterTemplate: editedName,
      },
    };
  };

  // Select new template for editingMetric
  const editingMetricUpdater = (metric: ICanaryMetricConfig) => {
    if (isNew) {
      return {
        ...metric,
        query: {
          ...metric.query,
          customFilterTemplate: editedName,
        },
      };
    }
    return metricUpdater(metric);
  };

  return {
    ...state,
    editingTemplate: {
      name: null,
      editedName: null,
      editedValue: null,
      isNew: false,
    },
    config: {
      ...state.config,
      templates,
    },
    metricList: (state.metricList || []).map(metricUpdater),
    editingMetric: editingMetricUpdater(state.editingMetric),
  };
};

const combined = combineReducers<ISelectedConfigState>({
  config,
  load,
  save,
  destroy,
  json,
  judge,
  metricList,
  editingMetric: (metric, action) =>
    [editingMetric, prometheusMetricConfigReducer, signalFxMetricConfigReducer, stackdriverMetricConfigReducer].reduce(
      (s, reducer) => reducer(s, action),
      metric,
    ),
  group,
  changeMetricGroup,
  isInSyncWithServer,
  validationErrors: () => null,
  editingTemplate,
  selectedStore: handleActions<string>({}, null),
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
    editingTemplateConfirmReducer,
  ].reduce((s, reducer) => reducer(s, action), state);
};
