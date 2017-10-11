import { Action, combineReducers } from 'redux';
import { handleActions } from 'redux-actions';
import { without } from 'lodash';

import { Application } from '@spinnaker/core';

import * as Actions from '../actions';
import { ICanaryConfigSummary } from '../domain/ICanaryConfigSummary';
import { IJudge } from '../domain/IJudge';
import { ICanaryConfig } from '../domain/ICanaryConfig';
import { ICanaryJudgeResultSummary } from '../domain/ICanaryJudgeResultSummary';

export interface IDataState {
  application: Application;
  configSummaries: ICanaryConfigSummary[];
  configs: ICanaryConfig[];
  judges: IJudge[];
  resultSummaries: ICanaryJudgeResultSummary[];
}

export const application = handleActions({
  [Actions.INITIALIZE]: (_state: Application, action: Action & any) => action.state.data.application,
}, null);

export const configSummaries = handleActions({
  [Actions.INITIALIZE]: (_state: ICanaryConfigSummary, action: Action & any) => action.state.data.configSummaries,
  [Actions.UPDATE_CONFIG_SUMMARIES]: (_state: ICanaryConfigSummary, action: Action & any) => action.payload.configSummaries,
}, []);

const configs = handleActions({
  [Actions.LOAD_CONFIG_SUCCESS]: (state: ICanaryConfig[], action: Action & any): ICanaryConfig[] => {
    if (state.some(config => config.name === action.payload.config.name)) {
      return without(
        state,
        state.find(config => config.name === action.payload.config.name)
      ).concat([action.payload.config]);
    } else {
      return state.concat([action.payload.config]);
    }
  },
}, []);

const judges = handleActions({
  [Actions.INITIALIZE]: (_state: IJudge[], action: Action & any): IJudge[] => action.state.data.judges,
  [Actions.UPDATE_JUDGES]: (_state: IJudge[], action: Action & any): IJudge[] => action.payload.judges,
}, null);

const resultSummaries = handleActions({
  [Actions.INITIALIZE]: (_state: ICanaryJudgeResultSummary[], action: Action & any) => action.state.data.resultSummaries,
  [Actions.UPDATE_RESULT_SUMMARIES]: (_state: ICanaryJudgeResultSummary[], action: Action & any) => action.payload.resultSummaries,
}, []);

export const data = combineReducers<IDataState>({
  application,
  configSummaries,
  judges,
  configs,
  resultSummaries,
});
