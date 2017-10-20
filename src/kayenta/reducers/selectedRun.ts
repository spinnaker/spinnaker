import { combineReducers, Action, Reducer } from 'redux'
import { handleActions } from 'redux-actions';

import * as Actions from 'kayenta/actions/index';
import { AsyncRequestState } from './asyncRequest';
import { IExecution } from '@spinnaker/core';

export interface ISelectedRunState {
  run: IExecution;
  load: AsyncRequestState;
  selectedGroup: string;
  selectedMetric: string;
}

const run = handleActions({
  [Actions.LOAD_RUN_SUCCESS]: (_state: IExecution, action: Action & any) => action.payload.run,
}, null);

const load = handleActions({
  [Actions.LOAD_RUN_REQUEST]: () => AsyncRequestState.Requesting,
  [Actions.LOAD_RUN_SUCCESS]: () => AsyncRequestState.Fulfilled,
  [Actions.LOAD_RUN_FAILURE]: () => AsyncRequestState.Failed,
}, AsyncRequestState.Requesting);

const selectedGroup = handleActions({
  [Actions.SELECT_RESULT_METRIC_GROUP]: (_state: string, action: Action & any) => action.payload.group,
}, null);

const selectedMetric = handleActions({
  [Actions.SELECT_RESULT_METRIC]: (_state: string, action: Action & any) => action.payload.metric,
  [Actions.SELECT_RESULT_METRIC_GROUP]: () => null,
}, null);

export const selectedRun: Reducer<ISelectedRunState> = combineReducers<ISelectedRunState>({
  run,
  load,
  selectedGroup,
  selectedMetric,
});
