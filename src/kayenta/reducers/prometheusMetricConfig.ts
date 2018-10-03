import { Action } from 'redux';
import { handleActions } from 'redux-actions';

import * as Actions from 'kayenta/actions';
import { IKayentaAction } from 'kayenta/actions/creators';
import { IUpdateListPayload, updateListReducer } from 'kayenta/layout/list';
import { IPrometheusCanaryMetricSetQueryConfig } from 'kayenta/metricStore/prometheus/domain/IPrometheusCanaryMetricSetQueryConfig';
import { ICanaryMetricConfig } from 'kayenta/domain';

const updateLabelBindingsReducer = updateListReducer();
const updateGroupByReducer = updateListReducer();

type IPrometheusMetricConfig = ICanaryMetricConfig<IPrometheusCanaryMetricSetQueryConfig>;

export const prometheusMetricConfigReducer = handleActions<IPrometheusMetricConfig, Action & any>(
  {
    [Actions.UPDATE_PROMETHEUS_METRIC_TYPE]: (state: ICanaryMetricConfig, action: Action & any) => ({
      ...state,
      query: { ...state.query, metricName: action.payload.metricName, type: 'prometheus' },
    }),
    [Actions.UPDATE_PROMETHEUS_LABEL_BINDINGS]: (
      state: IPrometheusMetricConfig,
      action: IKayentaAction<IUpdateListPayload>,
    ) => ({
      ...state,
      query: {
        ...state.query,
        labelBindings: updateLabelBindingsReducer(state.query.labelBindings || [], action),
      },
    }),
    [Actions.UPDATE_PROMETHEUS_GROUP_BY_FIELDS]: (
      state: IPrometheusMetricConfig,
      action: IKayentaAction<IUpdateListPayload>,
    ) => ({
      ...state,
      query: {
        ...state.query,
        groupByFields: updateGroupByReducer(state.query.groupByFields || [], action),
      },
    }),
  },
  null,
);
