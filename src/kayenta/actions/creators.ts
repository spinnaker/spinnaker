import { createAction } from 'redux-actions';

import * as Actions from './index';
import { ConfigJsonModalTabState } from '../edit/configJsonModal';

const typedPayloadCreator = <T>() => (payload: T): T => payload;

export const openDeleteConfigModal = createAction(Actions.DELETE_CONFIG_MODAL_OPEN);
export const closeDeleteConfigModal = createAction(Actions.DELETE_CONFIG_MODAL_CLOSE);
export const deleteConfig = createAction(Actions.DELETE_CONFIG_REQUEST);
export const closeConfigJsonModal = createAction(Actions.CONFIG_JSON_MODAL_CLOSE);
export const openConfigJsonModal = createAction(Actions.CONFIG_JSON_MODAL_OPEN);
export const editMetricCancel = createAction(Actions.EDIT_METRIC_CANCEL);
export const editMetricConfirm = createAction(Actions.EDIT_METRIC_CONFIRM);
export const addGroup = createAction(Actions.ADD_GROUP);
export const saveConfig = createAction(Actions.SAVE_CONFIG_REQUEST);
export const dismissSaveConfigError = createAction(Actions.DISMISS_SAVE_CONFIG_ERROR);
export const setConfigJsonModalTabState = createAction(Actions.SET_CONFIG_JSON_MODAL_TAB_STATE, typedPayloadCreator<ConfigJsonModalTabState>());
export const setConfigJson = createAction(Actions.SET_CONFIG_JSON, typedPayloadCreator<string>());
export const deleteConfigSuccess = createAction(Actions.DELETE_CONFIG_SUCCESS);
