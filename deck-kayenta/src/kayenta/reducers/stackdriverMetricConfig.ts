import * as Actions from 'kayenta/actions';
import type { IKayentaAction } from 'kayenta/actions/creators';
import type { ICanaryMetricConfig } from 'kayenta/domain';
import type { IUpdateListPayload } from 'kayenta/layout/list';
import { updateListReducer } from 'kayenta/layout/list';
import type { IStackdriverCanaryMetricSetQueryConfig } from 'kayenta/metricStore/stackdriver/domain/IStackdriverCanaryMetricSetQueryConfig';
import { omit } from 'lodash';
import type { Action } from 'redux';
import { handleActions } from 'redux-actions';

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
          query: omit(state.query, action.payload.field),
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
