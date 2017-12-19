import { Action } from 'redux';
import { handleActions } from 'redux-actions';

import * as Actions from 'kayenta/actions';
import { IUpdateListPayload, updateListReducer } from '../layout/list';
import { IStackdriverCanaryMetricSetQueryConfig } from 'kayenta/metricStore/stackdriver/domain/IStackdriverCanaryMetricSetQueryConfig';
import { IKayentaAction } from '../actions/creators';

const updateGroupByReducer = updateListReducer();

export const stackdriverMetricConfigReducer = handleActions<IStackdriverCanaryMetricSetQueryConfig, Action & any>({
  [Actions.UPDATE_STACKDRIVER_METRIC_TYPE]: (state: IStackdriverCanaryMetricSetQueryConfig, action: Action & any) => ({
    ...state, query: { ...state.query, metricType: action.metricType, type: 'stackdriver' },
  }),
  [Actions.UPDATE_STACKDRIVER_GROUP_BY_FIELDS]:
    (state: IStackdriverCanaryMetricSetQueryConfig, action: IKayentaAction<IUpdateListPayload>) => ({
    ...state,
    query: {
      ...state.query,
      groupByFields: updateGroupByReducer(state.query.groupByFields || [], action),
    },
  }),
}, null);
