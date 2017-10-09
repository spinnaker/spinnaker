import { combineReducers, Action } from 'redux'
import { handleActions } from 'redux-actions';

import * as Actions from 'kayenta/actions/index';
import { AsyncRequestState } from './asyncRequest';
import { ICanaryJudgeResult } from '../domain/ICanaryJudgeResult';

export interface ISelectedReportState {
  report: ICanaryJudgeResult;
  load: AsyncRequestState;
}

const report = handleActions({
  [Actions.LOAD_REPORT_SUCCESS]: (_state: ICanaryJudgeResult, action: Action & any) => action.payload.report,
}, null);

const load = handleActions({
  [Actions.LOAD_REPORT_REQUEST]: () => AsyncRequestState.Requesting,
  [Actions.LOAD_REPORT_SUCCESS]: () => AsyncRequestState.Fulfilled,
  [Actions.LOAD_REPORT_FAILURE]: () => AsyncRequestState.Failed,
}, AsyncRequestState.Requesting);

export const selectedReport = combineReducers<ISelectedReportState>({
  report,
  load,
});
