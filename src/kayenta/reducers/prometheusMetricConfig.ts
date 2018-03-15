import { Action } from 'redux';
import { handleActions } from 'redux-actions';

import * as Actions from 'kayenta/actions';
import { IUpdateListPayload, updateListReducer } from '../layout/list';
import { IPrometheusCanaryMetricSetQueryConfig } from 'kayenta/metricStore/prometheus/domain/IPrometheusCanaryMetricSetQueryConfig';
import { IKayentaAction } from '../actions/creators';

const updateLabelBindingsReducer = updateListReducer();
const updateGroupByReducer = updateListReducer();

export const prometheusMetricConfigReducer = handleActions<IPrometheusCanaryMetricSetQueryConfig, Action & any>({
  [Actions.UPDATE_PROMETHEUS_METRIC_TYPE]: (state: IPrometheusCanaryMetricSetQueryConfig, action: Action & any) => ({
    ...state, query: { ...state.query, metricName: action.payload.metricName, type: 'prometheus' },
  }),
  [Actions.UPDATE_PROMETHEUS_LABEL_BINDINGS]:
    (state: IPrometheusCanaryMetricSetQueryConfig, action: IKayentaAction<IUpdateListPayload>) => ({
      ...state,
      query: {
        ...state.query,
        labelBindings: updateLabelBindingsReducer(state.query.labelBindings || [], action),
      },
    }),
  [Actions.UPDATE_PROMETHEUS_GROUP_BY_FIELDS]:
    (state: IPrometheusCanaryMetricSetQueryConfig, action: IKayentaAction<IUpdateListPayload>) => ({
      ...state,
      query: {
        ...state.query,
        groupByFields: updateGroupByReducer(state.query.groupByFields || [], action),
      },
    }),
}, null);
