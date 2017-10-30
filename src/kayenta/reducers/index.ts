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

export interface ICanaryState {
  app: IAppState;
  data: IDataState;
  selectedConfig: ISelectedConfigState;
  selectedRun: ISelectedRunState;
}

const combined = combineReducers<ICanaryState>({
  app,
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
          return isEqual(editedConfig, originalConfig);
        }
      })(),
    },
  }
};

const selectedMetricReducer = (state: ICanaryState, action: Action & any) => {
  if (action.type !== Actions.SELECT_REPORT_METRIC) {
    return state;
  }

  const { payload: { metric } } = action;

  const results = metricResultsSelector(state)[metric];
  if (!state.selectedRun.metricSetPair.pair || state.selectedRun.metricSetPair.pair.id !== results.id) {
    // If we don't have the metric set pair loaded when we select the metric,
    // schedule loading it now.
    // TODO: cache metric set pairs.
    action.asyncDispatch(Creators.loadMetricSetPairRequest({
      pairId: results.id,
    }));
  }

  return {
    ...state,
    selectedRun: {
      ...state.selectedRun,
      selectedMetric: metric,
    },
  };
};

export const rootReducer = (state: ICanaryState, action: Action & any): ICanaryState => {
  return [
    combined,
    judgeRenderStateReducer,
    isInSyncWithServerReducer,
    selectedMetricReducer,
  ].reduce((s, reducer) => reducer(s, action), state);
};
