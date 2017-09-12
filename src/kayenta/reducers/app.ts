import { combineReducers } from 'redux';
import { handleActions, combineActions } from 'redux-actions';

import * as Actions from '../actions';

export interface IAppState {
  deleteConfigModalOpen: boolean;
  editConfigJsonModalOpen: boolean;
}

const deleteConfigModalOpen = handleActions({
  [Actions.DELETE_CONFIG_MODAL_OPEN]: () => true,
  [combineActions(Actions.DELETE_CONFIG_MODAL_CLOSE, Actions.DELETE_CONFIG_SUCCESS)]: () => false,
}, false);

const editConfigJsonModalOpen = handleActions({
  [Actions.EDIT_CONFIG_JSON_MODAL_OPEN]: () => true,
  [combineActions(Actions.EDIT_CONFIG_JSON_MODAL_CLOSE, Actions.SELECT_CONFIG)]: () => false,
}, false);

export const app = combineReducers<IAppState>({
  deleteConfigModalOpen,
  editConfigJsonModalOpen,
});
