import { omit } from 'lodash';
import type { Action } from 'redux';
import { handleActions } from 'redux-actions';

import * as Actions from '../actions';
import type { IKayentaAction } from '../actions/creators';
import type { ICanaryMetricConfig } from '../domain';
import type { IUpdateListPayload } from '../layout/list';
import { updateListReducer } from '../layout/list';
import type { IStackdriverCanaryMetricSetQueryConfig } from '../metricStore/stackdriver/domain/IStackdriverCanaryMetricSetQueryConfig';

const updateGroupByReducer = updateListReducer();

type IStackdriverMetricConfig = ICanaryMetricConfig<IStackdriverCanaryMetricSetQueryConfig>;

export const stackdriverMetricConfigReducer = handleActions<IStackdriverMetricConfig, Action & any>(
  {
    [Actions.UPDATE_STACKDRIVER_GROUP_BY_FIELDS]: (
      state: IStackdriverMetricConfig,
      action: IKayentaAction<IUpdateListPayload>,
    ) => ({
      ...state,
      query: {
        ...state.query,
        groupByFields: updateGroupByReducer(state.query.groupByFields || [], action),
      },
    }),
    [Actions.UPDATE_STACKDRIVER_METRIC_QUERY_FIELD]: (state: IStackdriverMetricConfig, action: Action & any) => {
      if (!action.payload.value) {
        return {
          ...state,
          query: omit(state.query, action.payload.field) as IStackdriverCanaryMetricSetQueryConfig,
        };
      }

      return {
        ...state,
        query: { ...state.query, [action.payload.field]: action.payload.value, type: 'stackdriver' },
      };
    },
  },
  null,
);
