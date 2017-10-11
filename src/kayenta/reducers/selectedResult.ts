import { combineReducers, Action } from 'redux'
import { handleActions } from 'redux-actions';

import * as Actions from 'kayenta/actions/index';
import { AsyncRequestState } from './asyncRequest';
import { ICanaryJudgeResult } from '../domain/ICanaryJudgeResult';

export interface ISelectedResultState {
  result: ICanaryJudgeResult;
  load: AsyncRequestState;
  selectedGroup: string;
  selectedMetric: string;
}

const result = handleActions({
  [Actions.LOAD_RESULT_SUCCESS]: (_state: ICanaryJudgeResult, action: Action & any) => action.payload.result,
}, null);

const load = handleActions({
  [Actions.LOAD_RESULT_REQUEST]: () => AsyncRequestState.Requesting,
  [Actions.LOAD_RESULT_SUCCESS]: () => AsyncRequestState.Fulfilled,
  [Actions.LOAD_RESULT_FAILURE]: () => AsyncRequestState.Failed,
}, AsyncRequestState.Requesting);

const selectedGroup = handleActions({
  [Actions.SELECT_RESULT_METRIC_GROUP]: (_state: string, action: Action & any) => action.payload.group,
}, null);

const selectedMetric = handleActions({
  [Actions.SELECT_RESULT_METRIC]: (_state: string, action: Action & any) => action.payload.metric,
  [Actions.SELECT_RESULT_METRIC_GROUP]: () => null,
}, null);

export const selectedResult = combineReducers<ISelectedResultState>({
  result,
  load,
  selectedGroup,
  selectedMetric,
});
