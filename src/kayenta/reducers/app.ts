import { combineReducers, Action } from 'redux';
import { handleActions, combineActions } from 'redux-actions';

import * as Actions from '../actions';
import { ConfigJsonModalTabState } from '../edit/configJsonModal';

export interface IAppState {
  deleteConfigModalOpen: boolean;
  configJsonModalOpen: boolean;
  configJsonModalTabState: ConfigJsonModalTabState;
}

const deleteConfigModalOpen = handleActions({
  [Actions.DELETE_CONFIG_MODAL_OPEN]: () => true,
  [combineActions(Actions.DELETE_CONFIG_MODAL_CLOSE, Actions.DELETE_CONFIG_SUCCESS)]: () => false,
}, false);

const configJsonModalOpen = handleActions({
  [Actions.CONFIG_JSON_MODAL_OPEN]: () => true,
  [combineActions(Actions.CONFIG_JSON_MODAL_CLOSE, Actions.SELECT_CONFIG)]: () => false,
}, false);

const configJsonModalTabState = handleActions({
  [Actions.SET_CONFIG_JSON_MODAL_TAB_STATE]: (_state: ConfigJsonModalTabState, action: Action & any) => action.payload.state,
  [Actions.CONFIG_JSON_MODAL_OPEN]: () => ConfigJsonModalTabState.Edit,
}, ConfigJsonModalTabState.Edit);

export const app = combineReducers<IAppState>({
  deleteConfigModalOpen,
  configJsonModalOpen,
  configJsonModalTabState,
});
