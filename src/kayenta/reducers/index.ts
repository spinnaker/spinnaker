import { combineReducers, Action } from 'redux';
import { combineActions, handleActions } from 'redux-actions';
import { isEqual } from 'lodash';

import * as Actions from 'kayenta/actions/index';
import * as Creators from 'kayenta/actions/creators';
import { IDataState, data } from './data';
import { app, IAppState } from './app';
import {
  ISelectedConfigState,
  selectedConfig
} from './selectedConfig';
import { JudgeSelectRenderState } from '../edit/judgeSelect';
import { IJudge } from '../domain/IJudge';
import { ICanaryJudgeConfig } from '../domain/ICanaryConfig';
import { mapStateToConfig } from '../service/canaryConfig.service';
import { ISelectedRunState, selectedRun } from './selectedRun';
import { metricResultsSelector } from '../selectors/index';
import { validationErrorsReducer } from './validators';
import { AsyncRequestState } from './asyncRequest';
import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';

export interface ICanaryState {
  app: IAppState;
  data: IDataState;
  selectedConfig: ISelectedConfigState;
  selectedRun: ISelectedRunState;
}

const combined = combineReducers<ICanaryState>({
  app,
  configValidationErrors: () => null,
  data,
  selectedConfig,
  selectedRun,
});

const judgeRenderStateReducer = handleActions({
  [combineActions(Actions.SELECT_CONFIG, Actions.UPDATE_JUDGES)]: (state: ICanaryState) => {
    // At this point, we've already passed through the rest of the reducers, so
    // the judge has been normalized on the state.
    const {
      data: { judges = [] },
      selectedConfig: { judge: { judgeConfig } },
    } = state;

    if (!judgeConfig) {
      return state;
    } else {
      return {
        ...state,
        selectedConfig: {
          ...state.selectedConfig,
          judge: {
            ...state.selectedConfig.judge,
            renderState: getRenderState(judges, judgeConfig),
          }
        }
      }
    }
  }
}, null);

const getRenderState = (judges: IJudge[], judgeConfig: ICanaryJudgeConfig): JudgeSelectRenderState => {
  if (judges.some(judge => judge.name === judgeConfig.name)) {
    if (judges.length === 1) {
      return JudgeSelectRenderState.None;
    } else {
      return JudgeSelectRenderState.Multiple;
    }
  } else {
    return JudgeSelectRenderState.Single;
  }
};

const isInSyncWithServerReducer = (state: ICanaryState): ICanaryState => {
  return {
    ...state,
    selectedConfig: {
      ...state.selectedConfig,
      isInSyncWithServer: (() => {
        const editedConfig = mapStateToConfig(state);
        if (!editedConfig) {
          return true;
        } else {
          const originalConfig = state.data.configs.find(c => c.id === editedConfig.id);
          // If we're saving the config right now, don't warn that
          // the config hasn't been saved.
          if (!originalConfig
              && editedConfig.isNew
              && state.selectedConfig.save.state === AsyncRequestState.Requesting) {
            return true;
          }
          return isEqual(editedConfig, originalConfig);
        }
      })(),
    },
  }
};

const resolveSelectedMetricId = (state: ICanaryState, action: Action & any): string => {
  switch (action.type) {
    case Actions.SELECT_REPORT_METRIC:
      return action.payload.metricId;

    // On report load, pick the first metric.
    case Actions.LOAD_RUN_SUCCESS:
      return metricResultsSelector(state).length
        ? metricResultsSelector(state)[0].id
        : null;

    // On group select, pick the first metric in the group.
    case Actions.SELECT_REPORT_METRIC_GROUP:
      const results = metricResultsSelector(state);
      if (!results.length) {
        return null;
      }

      const group = action.payload.group;
      let filter: (r: ICanaryAnalysisResult) => boolean;
      if (!group) {
        filter = () => true;
      } else {
        filter = r => r.groups.includes(group);
      }

      return results.find(filter)
        ? results.find(filter).id
        : null;

    default:
      return null;
  }
};

const selectedMetricReducer = (state: ICanaryState, action: Action & any) => {
  if (![Actions.SELECT_REPORT_METRIC,
        Actions.SELECT_REPORT_METRIC_GROUP,
        Actions.LOAD_RUN_SUCCESS].includes(action.type)) {
    return state;
  }

  const id = resolveSelectedMetricId(state, action);
  if (!id) {
    return state;
  }

  // Load metric set pair.
  action.asyncDispatch(Creators.loadMetricSetPairRequest({
    pairId: id,
  }));

  return {
    ...state,
    selectedRun: {
      ...state.selectedRun,
      selectedMetric: id,
      metricSetPair: {
        ...state.selectedRun.metricSetPair,
        load: AsyncRequestState.Requesting,
      },
    },
  };
};

export const rootReducer = (state: ICanaryState, action: Action & any): ICanaryState => {
  return [
    combined,
    judgeRenderStateReducer,
    isInSyncWithServerReducer,
    selectedMetricReducer,
    validationErrorsReducer,
  ].reduce((s, reducer) => reducer(s, action), state);
};
