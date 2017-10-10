import { combineReducers, Action } from 'redux'
import { handleActions } from 'redux-actions';

import * as Actions from 'kayenta/actions/index';
import { AsyncRequestState } from './asyncRequest';
import { ICanaryJudgeResult } from '../domain/ICanaryJudgeResult';

export interface ISelectedReportState {
  report: ICanaryJudgeResult;
  load: AsyncRequestState;
  selectedGroup: string;
}

const report = handleActions({
  [Actions.LOAD_REPORT_SUCCESS]: (_state: ICanaryJudgeResult, action: Action & any) => action.payload.report,
}, null);

const load = handleActions({
  [Actions.LOAD_REPORT_REQUEST]: () => AsyncRequestState.Requesting,
  [Actions.LOAD_REPORT_SUCCESS]: () => AsyncRequestState.Fulfilled,
  [Actions.LOAD_REPORT_FAILURE]: () => AsyncRequestState.Failed,
}, AsyncRequestState.Requesting);

const selectedGroup = handleActions({
  [Actions.SELECT_REPORT_GROUP]: (_state: string, action: Action & any) => action.payload.group,
}, null);

export const selectedReport = combineReducers<ISelectedReportState>({
  report,
  load,
  selectedGroup,
});
