import type { Action } from 'redux';
import { handleActions } from 'redux-actions';

import * as Actions from '../actions';
import type { IKayentaAction } from '../actions/creators';
import type { ICanaryMetricConfig } from '../domain';
import type { IUpdateKeyValueListPayload } from '../layout/keyValueList';
import { updateListReducer } from '../layout/keyValueList';
import type { ISignalFxCanaryMetricSetQueryConfig } from '../metricStore/signalfx/domain/ISignalFxCanaryMetricSetQueryConfig';

const updateQueryPairsReducer = updateListReducer();

type ISignalFxMetricConfig = ICanaryMetricConfig<ISignalFxCanaryMetricSetQueryConfig>;

export const signalFxMetricConfigReducer = handleActions(
  {
    [Actions.UPDATE_SIGNAL_FX_QUERY_PAIRS]: (
      state: ISignalFxMetricConfig,
      action: IKayentaAction<IUpdateKeyValueListPayload>,
    ) => ({
      ...state,
      query: {
        ...state.query,
        queryPairs: updateQueryPairsReducer(state.query.queryPairs || [], action),
      },
    }),
    [Actions.UPDATE_SIGNAL_FX_METRIC_NAME]: (state: ISignalFxMetricConfig, action: Action & any) => ({
      ...state,
      query: { ...state.query, metricName: action.payload.metricName },
    }),
    [Actions.UPDATE_SIGNAL_FX_AGGREGATION_METHOD]: (state: ISignalFxMetricConfig, action: Action & any) => ({
      ...state,
      query: { ...state.query, aggregationMethod: action.payload.aggregationMethod },
    }),
  },
  null,
);
