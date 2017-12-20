import { createAction } from 'redux-actions';
import { Action } from 'redux';

import * as Actions from './index';
import { ConfigJsonModalTabState } from '../edit/configJsonModal';
import {ICanaryConfig, ICanaryMetricConfig} from '../domain/ICanaryConfig';
import { ICanaryConfigSummary } from '../domain/ICanaryConfigSummary';
import { IJudge } from '../domain/IJudge';
import { IMetricSetPair } from '../domain/IMetricSetPair';
import { ICanaryExecutionStatusResult } from '../domain/ICanaryExecutionStatusResult';
import { IUpdateListPayload } from '../layout/list';

export interface IKayentaAction<T> extends Action {
  payload: T;
}

const typedPayloadCreator = <T>() => (payload: T): T => payload;

export const openDeleteConfigModal = createAction(Actions.DELETE_CONFIG_MODAL_OPEN);
export const closeDeleteConfigModal = createAction(Actions.DELETE_CONFIG_MODAL_CLOSE);
export const deleteConfig = createAction(Actions.DELETE_CONFIG_REQUEST);
export const closeConfigJsonModal = createAction(Actions.CONFIG_JSON_MODAL_CLOSE);
export const openConfigJsonModal = createAction(Actions.CONFIG_JSON_MODAL_OPEN);
export const editMetricCancel = createAction(Actions.EDIT_METRIC_CANCEL);
export const editMetricConfirm = createAction(Actions.EDIT_METRIC_CONFIRM);
export const updateMetricDirection = createAction(Actions.UPDATE_METRIC_DIRECTION, typedPayloadCreator<{id: string, direction: string}>());
export const addGroup = createAction(Actions.ADD_GROUP);
export const saveConfig = createAction(Actions.SAVE_CONFIG_REQUEST);
export const dismissSaveConfigError = createAction(Actions.DISMISS_SAVE_CONFIG_ERROR);
export const setConfigJsonModalTabState = createAction(Actions.SET_CONFIG_JSON_MODAL_TAB_STATE, typedPayloadCreator<{state: ConfigJsonModalTabState}>());
export const setConfigJson = createAction(Actions.SET_CONFIG_JSON, typedPayloadCreator<{json: string}>());
export const deleteConfigSuccess = createAction(Actions.DELETE_CONFIG_SUCCESS);
export const loadConfigRequest = createAction(Actions.LOAD_CONFIG_REQUEST, typedPayloadCreator<{id: string}>());
export const saveConfigSuccess = createAction(Actions.SAVE_CONFIG_SUCCESS, typedPayloadCreator<{id: string}>());
export const selectConfig = createAction(Actions.SELECT_CONFIG, typedPayloadCreator<{config: ICanaryConfig}>());
export const clearSelectedConfig = createAction(Actions.CLEAR_SELECTED_CONFIG);
export const renameMetric = createAction(Actions.RENAME_METRIC, typedPayloadCreator<{id: string, name: string}>());
export const selectGroup = createAction(Actions.SELECT_GROUP, typedPayloadCreator<{name: string}>());
export const updateGroupWeight = createAction(Actions.UPDATE_GROUP_WEIGHT, typedPayloadCreator<{group: string, weight: number}>());
export const selectJudgeName = createAction(Actions.SELECT_JUDGE_NAME, typedPayloadCreator<{judge: {name: string}}>());
export const editMetricBegin = createAction(Actions.EDIT_METRIC_BEGIN, typedPayloadCreator<{id: string}>());
export const removeMetric = createAction(Actions.REMOVE_METRIC, typedPayloadCreator<{id: string}>());
export const addMetric = createAction(Actions.ADD_METRIC, typedPayloadCreator<{metric: ICanaryMetricConfig}>());
export const updateConfigName = createAction(Actions.UPDATE_CONFIG_NAME, typedPayloadCreator<{name: string}>());
export const updateConfigDescription = createAction(Actions.UPDATE_CONFIG_DESCRIPTION, typedPayloadCreator<{description: string}>());
export const updateScoreThresholds = createAction(Actions.UPDATE_SCORE_THRESHOLDS, typedPayloadCreator<{marginal: number, pass: number}>());
export const saveConfigFailure = createAction(Actions.SAVE_CONFIG_FAILURE, typedPayloadCreator<{error: Error}>());
export const deleteConfigFailure = createAction(Actions.DELETE_CONFIG_FAILURE, typedPayloadCreator<{error: Error}>());
export const updateConfigSummaries = createAction(Actions.UPDATE_CONFIG_SUMMARIES, typedPayloadCreator<{configSummaries: ICanaryConfigSummary[]}>());
export const updateJudges = createAction(Actions.UPDATE_JUDGES, typedPayloadCreator<{judges: IJudge[]}>());
export const loadConfigSuccess = createAction(Actions.LOAD_CONFIG_SUCCESS, typedPayloadCreator<{config: ICanaryConfig}>());
export const loadConfigFailure = createAction(Actions.LOAD_CONFIG_FAILURE, typedPayloadCreator<{error: Error}>());
export const copySelectedConfig = createAction(Actions.COPY_SELECTED_CONFIG);
export const createNewConfig = createAction(Actions.CREATE_NEW_CONFIG);
export const editGroupBegin = createAction(Actions.EDIT_GROUP_BEGIN, typedPayloadCreator<{group: string}>());
export const editGroupUpdate = createAction(Actions.EDIT_GROUP_UPDATE, typedPayloadCreator<{edit: string}>());
export const editGroupConfirm = createAction(Actions.EDIT_GROUP_CONFIRM, typedPayloadCreator<{group: string, edit: string}>());
export const changeMetricGroupSelect = createAction(Actions.CHANGE_METRIC_GROUP_SELECT, typedPayloadCreator<{group: string}>());
export const changeMetricGroupConfirm = createAction(Actions.CHANGE_METRIC_GROUP_CONFIRM, typedPayloadCreator<{metricId: string}>());
export const loadRunRequest = createAction(Actions.LOAD_RUN_REQUEST, typedPayloadCreator<{configId: string, runId: string}>());
export const loadRunSuccess = createAction(Actions.LOAD_RUN_SUCCESS, typedPayloadCreator<{run: ICanaryExecutionStatusResult}>());
export const loadRunFailure = createAction(Actions.LOAD_RUN_FAILURE, typedPayloadCreator<{error: Error}>());
export const selectReportMetricGroup = createAction(Actions.SELECT_REPORT_METRIC_GROUP, typedPayloadCreator<{group: string}>());
export const selectReportMetric = createAction(Actions.SELECT_REPORT_METRIC, typedPayloadCreator<{metric: string}>());
export const loadMetricSetPairRequest = createAction(Actions.LOAD_METRIC_SET_PAIR_REQUEST, typedPayloadCreator<{pairId: string}>());
export const loadMetricSetPairSuccess = createAction(Actions.LOAD_METRIC_SET_PAIR_SUCCESS, typedPayloadCreator<{metricSetPair: IMetricSetPair}>());
export const loadMetricSetPairFailure = createAction(Actions.LOAD_METRIC_SET_PAIR_FAILURE, typedPayloadCreator<{error: Error}>());
export const editTemplateBegin = createAction(Actions.EDIT_TEMPLATE_BEGIN, typedPayloadCreator<{name: string, value: string}>());
export const editTemplateConfirm = createAction(Actions.EDIT_TEMPLATE_CONFIRM);
export const editTemplateCancel = createAction(Actions.EDIT_TEMPLATE_CANCEL);
export const editTemplateName = createAction(Actions.EDIT_TEMPLATE_NAME, typedPayloadCreator<{name: string}>());
export const editTemplateValue = createAction(Actions.EDIT_TEMPLATE_VALUE, typedPayloadCreator<{value: string}>());
export const updateStackdriverGroupBy = createAction(Actions.UPDATE_STACKDRIVER_GROUP_BY_FIELDS, typedPayloadCreator<IUpdateListPayload>())
export const deleteTemplate = createAction(Actions.DELETE_TEMPLATE, typedPayloadCreator<{name: string}>());
export const selectTemplate = createAction(Actions.SELECT_TEMPLATE, typedPayloadCreator<{name: string}>());
