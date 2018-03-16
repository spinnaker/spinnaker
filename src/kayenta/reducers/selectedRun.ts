import { combineReducers, Action, Reducer } from 'redux'
import { handleActions } from 'redux-actions';

import * as Actions from 'kayenta/actions';
import { AsyncRequestState } from './asyncRequest';
import { IMetricSetPair, ICanaryExecutionStatusResult } from 'kayenta/domain';
import { GraphType } from 'kayenta/report/detail/graph/metricSetPairGraph.service';

interface IMetricSetPairState {
  pair: IMetricSetPair;
  load: AsyncRequestState;
}

export interface ISelectedRunState {
  run: ICanaryExecutionStatusResult;
  load: AsyncRequestState;
  selectedGroup: string;
  selectedMetric: string;
  metricSetPair: IMetricSetPairState;
  graphType: GraphType;
}

const run = handleActions({
  [Actions.LOAD_RUN_SUCCESS]: (_state: ICanaryExecutionStatusResult, action: Action & any) => action.payload.run,
}, null);

const load = handleActions({
  [Actions.LOAD_RUN_REQUEST]: () => AsyncRequestState.Requesting,
  [Actions.LOAD_RUN_SUCCESS]: () => AsyncRequestState.Fulfilled,
  [Actions.LOAD_RUN_FAILURE]: () => AsyncRequestState.Failed,
}, AsyncRequestState.Requesting);

const selectedGroup = handleActions({
  [Actions.SELECT_REPORT_METRIC_GROUP]: (_state: string, action: Action & any) => action.payload.group,
}, null);

const selectedMetric = handleActions({
  [Actions.SELECT_REPORT_METRIC_GROUP]: () => null,
}, null);

const metricSetPair = combineReducers<IMetricSetPairState>({
  pair: handleActions({
    [Actions.LOAD_METRIC_SET_PAIR_REQUEST]: () => null,
    [Actions.LOAD_METRIC_SET_PAIR_SUCCESS]: (_state: IMetricSetPair, action: Action & any) => action.payload.metricSetPair,
  }, null),
  load: handleActions({
    [Actions.LOAD_METRIC_SET_PAIR_REQUEST]: () => AsyncRequestState.Requesting,
    [Actions.LOAD_METRIC_SET_PAIR_SUCCESS]: () => AsyncRequestState.Fulfilled,
    [Actions.LOAD_METRIC_SET_PAIR_FAILURE]: () => AsyncRequestState.Failed,
  }, AsyncRequestState.Fulfilled),
});

const graphType = handleActions<GraphType>({
  [Actions.SELECT_GRAPH_TYPE]: (_state: GraphType, action: Action & any) => action.payload.type,
}, GraphType.AmplitudeVsTime);

export const selectedRun: Reducer<ISelectedRunState> = combineReducers<ISelectedRunState>({
  run,
  load,
  selectedGroup,
  selectedMetric,
  metricSetPair,
  graphType,
});
