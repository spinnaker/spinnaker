import { createAction } from 'redux-actions';

import * as Actions from './index';

export const openDeleteConfigModal = createAction(Actions.DELETE_CONFIG_MODAL_OPEN);
export const closeDeleteConfigModal = createAction(Actions.DELETE_CONFIG_MODAL_CLOSE);
export const deleteConfig = createAction(Actions.DELETE_CONFIG_REQUEST);
export const closeEditConfigJsonModal = createAction(Actions.EDIT_CONFIG_JSON_MODAL_CLOSE);
export const openEditConfigJsonModal = createAction(Actions.EDIT_CONFIG_JSON_MODAL_OPEN);
export const editMetricCancel = createAction(Actions.EDIT_METRIC_CANCEL);
export const editMetricConfirm = createAction(Actions.EDIT_METRIC_CONFIRM);
export const addGroup = createAction(Actions.ADD_GROUP);
export const saveConfig = createAction(Actions.SAVE_CONFIG_REQUEST);
export const dismissSaveConfigError = createAction(Actions.DISMISS_SAVE_CONFIG_ERROR);
