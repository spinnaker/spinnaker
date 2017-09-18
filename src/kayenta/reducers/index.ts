import { combineReducers, Action } from 'redux';
import { combineActions, handleActions } from 'redux-actions';

import * as Actions from 'kayenta/actions/index';
import { IDataState, data } from './data';
import { app, IAppState } from './app';
import {
  ISelectedConfigState,
  selectedConfig
} from './selectedConfig';
import { JudgeSelectRenderState } from '../edit/judgeSelect';
import { IJudge } from '../domain/IJudge';
import { ICanaryJudgeConfig } from '../domain/ICanaryConfig';

export interface ICanaryState {
  data: IDataState;
  selectedConfig: ISelectedConfigState;
  app: IAppState;
}

const combined = combineReducers<ICanaryState>({
  data,
  app,
  selectedConfig,
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

export const rootReducer = (state: ICanaryState, action: Action & any): ICanaryState => {
  return [
    combined,
    judgeRenderStateReducer,
  ].reduce((s, reducer) => reducer(s, action), state);
};
