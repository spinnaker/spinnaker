import {
  ICanaryConfig,
  ICanaryConfigSummary,
  ICanaryExecutionStatusResult,
  ICanaryMetricConfig,
  ICanaryMetricEffectSizeConfig,
  IJudge,
  IKayentaAccount,
  IMetricSetPair,
  IMetricsServiceMetadata,
  MetricClassificationLabel,
} from 'kayenta/domain';
import { ConfigJsonModalTabState } from 'kayenta/edit/configJsonModal';
import { IUpdateListPayload } from 'kayenta/layout/list';
import { GraphType } from 'kayenta/report/detail/graph/metricSetPairGraph.service';
import { Action } from 'redux';
import { createAction } from 'redux-actions';

import * as Actions from './index';
import { IUpdateKeyValueListPayload } from '../layout/keyValueList';
import { IPrometheusCanaryMetricSetQueryConfig } from '../metricStore/prometheus/domain/IPrometheusCanaryMetricSetQueryConfig';
import { IStackdriverCanaryMetricSetQueryConfig } from '../metricStore/stackdriver/domain/IStackdriverCanaryMetricSetQueryConfig';

export interface IKayentaAction<T> extends Action {
  payload: T;
}

export const toggleMetricClassificationFilter = createAction<{ classification: MetricClassificationLabel }>(
  Actions.TOGGLE_METRIC_CLASSIFICATION_FILTER,
);
export const setExecutionsCount = createAction<{ count: number }>(Actions.SET_EXECUTIONS_COUNT);
export const openDeleteConfigModal = createAction(Actions.DELETE_CONFIG_MODAL_OPEN);
export const closeDeleteConfigModal = createAction(Actions.DELETE_CONFIG_MODAL_CLOSE);
export const deleteConfig = createAction(Actions.DELETE_CONFIG_REQUEST);
export const closeConfigJsonModal = createAction(Actions.CONFIG_JSON_MODAL_CLOSE);
export const openConfigJsonModal = createAction(Actions.CONFIG_JSON_MODAL_OPEN);
export const editMetricCancel = createAction(Actions.EDIT_METRIC_CANCEL);
export const editMetricConfirm = createAction(Actions.EDIT_METRIC_CONFIRM);
export const updateMetricDirection = createAction<{ id: string; direction: string }>(Actions.UPDATE_METRIC_DIRECTION);
export const updateMetricNanStrategy = createAction<{ id: string; strategy: string }>(
  Actions.UPDATE_METRIC_NAN_STRATEGY,
);
export const updateMetricOutlierStrategy = createAction<{ id: string; strategy: string }>(
  Actions.UPDATE_METRIC_OUTLIER_STRATEGY,
);
export const updateMetricCriticality = createAction<{ id: string; critical: boolean }>(
  Actions.UPDATE_METRIC_CRITICALITY,
);
export const updateMetricDataRequired = createAction<{ id: string; mustHaveData: boolean }>(
  Actions.UPDATE_METRIC_DATA_REQUIRED,
);
export const updateEffectSize = createAction<{ id: string; value: ICanaryMetricEffectSizeConfig }>(
  Actions.UPDATE_EFFECT_SIZE,
);
export const updateMetricGroup = createAction<{ id: string; group: string }>(Actions.UPDATE_METRIC_GROUP);
export const addGroup = createAction(Actions.ADD_GROUP);
export const saveConfig = createAction(Actions.SAVE_CONFIG_REQUEST);
export const dismissSaveConfigError = createAction(Actions.DISMISS_SAVE_CONFIG_ERROR);
export const setConfigJsonModalTabState = createAction<{ state: ConfigJsonModalTabState }>(
  Actions.SET_CONFIG_JSON_MODAL_TAB_STATE,
);
export const setConfigJson = createAction<{ json: string }>(Actions.SET_CONFIG_JSON);
export const deleteConfigSuccess = createAction(Actions.DELETE_CONFIG_SUCCESS);
export const loadConfigRequest = createAction<{ id: string }>(Actions.LOAD_CONFIG_REQUEST);
export const saveConfigSuccess = createAction<{ id: string }>(Actions.SAVE_CONFIG_SUCCESS);
export const selectConfig = createAction<{ config: ICanaryConfig }>(Actions.SELECT_CONFIG);
export const clearSelectedConfig = createAction(Actions.CLEAR_SELECTED_CONFIG);
export const renameMetric = createAction<{ id: string; name: string }>(Actions.RENAME_METRIC);
export const selectGroup = createAction<{ name: string }>(Actions.SELECT_GROUP);
export const updateGroupWeight = createAction<{ group: string; weight: number }>(Actions.UPDATE_GROUP_WEIGHT);
export const selectJudgeName = createAction<{ judge: { name: string } }>(Actions.SELECT_JUDGE_NAME);
export const editMetricBegin = createAction<{ id: string }>(Actions.EDIT_METRIC_BEGIN);
export const removeMetric = createAction<{ id: string }>(Actions.REMOVE_METRIC);
export const addMetric = createAction<{ metric: ICanaryMetricConfig }>(Actions.ADD_METRIC);
export const updateConfigName = createAction<{ name: string }>(Actions.UPDATE_CONFIG_NAME);
export const updateConfigDescription = createAction<{ description: string }>(Actions.UPDATE_CONFIG_DESCRIPTION);
export const saveConfigFailure = createAction<{ error: Error }>(Actions.SAVE_CONFIG_FAILURE);
export const deleteConfigFailure = createAction<{ error: Error }>(Actions.DELETE_CONFIG_FAILURE);
export const updateConfigSummaries = createAction<{ configSummaries: ICanaryConfigSummary[] }>(
  Actions.UPDATE_CONFIG_SUMMARIES,
);
export const updateJudges = createAction<{ judges: IJudge[] }>(Actions.UPDATE_JUDGES);
export const loadConfigSuccess = createAction<{ config: ICanaryConfig }>(Actions.LOAD_CONFIG_SUCCESS);
export const loadConfigFailure = createAction<{ error: Error }>(Actions.LOAD_CONFIG_FAILURE);
export const copySelectedConfig = createAction(Actions.COPY_SELECTED_CONFIG);
export const createNewConfig = createAction(Actions.CREATE_NEW_CONFIG);
export const editGroupBegin = createAction<{ group: string }>(Actions.EDIT_GROUP_BEGIN);
export const editGroupUpdate = createAction<{ edit: string }>(Actions.EDIT_GROUP_UPDATE);
export const editGroupConfirm = createAction<{ group: string; edit: string }>(Actions.EDIT_GROUP_CONFIRM);
export const changeMetricGroupSelect = createAction<{ group: string }>(Actions.CHANGE_METRIC_GROUP_SELECT);
export const changeMetricGroupConfirm = createAction<{ metricId: string }>(Actions.CHANGE_METRIC_GROUP_CONFIRM);
export const loadRunRequest = createAction<{ configId: string; runId: string }>(Actions.LOAD_RUN_REQUEST);
export const loadRunSuccess = createAction<{ run: ICanaryExecutionStatusResult }>(Actions.LOAD_RUN_SUCCESS);
export const loadRunFailure = createAction<{ error: Error }>(Actions.LOAD_RUN_FAILURE);
export const selectReportMetricGroup = createAction<{ group: string }>(Actions.SELECT_REPORT_METRIC_GROUP);
export const selectReportMetric = createAction<{ metricId: string }>(Actions.SELECT_REPORT_METRIC);
export const loadMetricSetPairRequest = createAction<{ pairId: string }>(Actions.LOAD_METRIC_SET_PAIR_REQUEST);
export const loadMetricSetPairSuccess = createAction<{ metricSetPair: IMetricSetPair }>(
  Actions.LOAD_METRIC_SET_PAIR_SUCCESS,
);
export const loadMetricSetPairFailure = createAction<{ error: Error }>(Actions.LOAD_METRIC_SET_PAIR_FAILURE);
export const addTemplate = createAction(Actions.ADD_TEMPLATE);
export const editTemplateBegin = createAction<{ name: string; value: string }>(Actions.EDIT_TEMPLATE_BEGIN);
export const editTemplateConfirm = createAction(Actions.EDIT_TEMPLATE_CONFIRM);
export const editTemplateCancel = createAction(Actions.EDIT_TEMPLATE_CANCEL);
export const editTemplateName = createAction<{ name: string }>(Actions.EDIT_TEMPLATE_NAME);
export const editTemplateValue = createAction<{ value: string }>(Actions.EDIT_TEMPLATE_VALUE);
export const editInlineTemplate = createAction<{ value: string }>(Actions.EDIT_INLINE_TEMPLATE);
export const updatePrometheusLabelBindings = createAction<IUpdateListPayload>(Actions.UPDATE_PROMETHEUS_LABEL_BINDINGS);
export const updatePrometheusGroupBy = createAction<IUpdateListPayload>(Actions.UPDATE_PROMETHEUS_GROUP_BY_FIELDS);
export const updatePrometheusMetricQueryField = createAction<{
  field: keyof IPrometheusCanaryMetricSetQueryConfig;
  value: IPrometheusCanaryMetricSetQueryConfig[keyof IPrometheusCanaryMetricSetQueryConfig];
}>(Actions.UPDATE_PROMETHEUS_METRIC_QUERY_FIELD);
export const updateStackdriverGroupBy = createAction<IUpdateListPayload>(Actions.UPDATE_STACKDRIVER_GROUP_BY_FIELDS);
export const deleteTemplate = createAction<{ name: string }>(Actions.DELETE_TEMPLATE);
export const selectTemplate = createAction<{ name: string }>(Actions.SELECT_TEMPLATE);
export const changeMetricGroup = createAction<{ id: string }>(Actions.CHANGE_METRIC_GROUP);
export const loadMetricsServiceMetadataRequest = createAction<{ filter: string; metricsAccountName: string }>(
  Actions.LOAD_METRICS_SERVICE_METADATA_REQUEST,
);
export const loadMetricsServiceMetadataSuccess = createAction<{ data: IMetricsServiceMetadata }>(
  Actions.LOAD_METRICS_SERVICE_METADATA_SUCCESS,
);
export const loadMetricsServiceMetadataFailure = createAction<{ error: Error }>(
  Actions.LOAD_METRICS_SERVICE_METADATA_FAILURE,
);
export const updatePrometheusMetricDescriptorFilter = createAction<{ filter: string; metricsAccountName: string }>(
  Actions.UPDATE_PROMETHEUS_METRIC_DESCRIPTOR_FILTER,
);
export const updateGraphiteMetricDescriptorFilter = createAction<{ filter: string }>(
  Actions.UPDATE_GRAPHITE_METRIC_DESCRIPTOR_FILTER,
);
export const updateStackdriverMetricDescriptorFilter = createAction<{ filter: string }>(
  Actions.UPDATE_STACKDRIVER_METRIC_DESCRIPTOR_FILTER,
);
export const updateDatadogMetricDescriptorFilter = createAction<{ filter: string }>(
  Actions.UPDATE_DATADOG_METRIC_DESCRIPTOR_FILTER,
);
export const updateStackdriverMetricResourceField = createAction<{
  field: keyof IStackdriverCanaryMetricSetQueryConfig;
  value: IStackdriverCanaryMetricSetQueryConfig[keyof IStackdriverCanaryMetricSetQueryConfig];
}>(Actions.UPDATE_STACKDRIVER_METRIC_QUERY_FIELD);
export const updateDatadogMetricName = createAction<{ metricName: string }>(Actions.UPDATE_DATADOG_METRIC_NAME);
export const updateNewRelicSelect = createAction<{ select: string }>(Actions.UPDATE_NEWRELIC_SELECT);
export const loadExecutionsRequest = createAction(Actions.LOAD_EXECUTIONS_REQUEST);
export const loadExecutionsFailure = createAction<{ error: Error }>(Actions.LOAD_EXECUTIONS_FAILURE);
export const loadExecutionsSuccess = createAction<{ executions: ICanaryExecutionStatusResult[] }>(
  Actions.LOAD_EXECUTIONS_SUCCESS,
);
export const selectGraphType = createAction<{ type: GraphType }>(Actions.SELECT_GRAPH_TYPE);
export const loadKayentaAccountsRequest = createAction(Actions.LOAD_KAYENTA_ACCOUNTS_REQUEST);
export const loadKayentaAccountsSuccess = createAction<{ accounts: IKayentaAccount[] }>(
  Actions.LOAD_KAYENTA_ACCOUNTS_SUCCESS,
);
export const loadKayentaAccountsFailure = createAction<{ error: Error }>(Actions.LOAD_KAYENTA_ACCOUNTS_FAILURE);
export const selectMetricStore = createAction<{ store: string }>(Actions.SELECT_METRIC_STORE);
export const updateSignalFxMetricName = createAction<{ metricName: string }>(Actions.UPDATE_SIGNAL_FX_METRIC_NAME);
export const updateSignalFxAggregationMethod = createAction<{ aggregationMethod: string }>(
  Actions.UPDATE_SIGNAL_FX_AGGREGATION_METHOD,
);
export const updateSignalFxQueryPairs = createAction<IUpdateKeyValueListPayload>(Actions.UPDATE_SIGNAL_FX_QUERY_PAIRS);
export const updateGraphiteMetricName = createAction<{ metricName: string }>(Actions.UPDATE_GRAPHITE_METRIC_NAME);
