import { Action, combineReducers } from 'redux';

import * as Actions from '../actions';

export interface IAppState {
  deleteConfigModalOpen: boolean;
  editConfigJsonModalOpen: boolean;
}

function deleteConfigModalOpen(state = false, action: Action & any): boolean {
  switch (action.type) {
    case Actions.INITIALIZE:
      return action.state.app.deleteConfigModalOpen;

    case Actions.DELETE_CONFIG_MODAL_OPEN:
      return true;

    case Actions.DELETE_CONFIG_MODAL_CLOSE:
      return false;

    case Actions.DELETE_CONFIG_SUCCESS:
      return false;

    default:
      return state;
  }
}

function editConfigJsonModalOpen(state = false, action: Action & any): boolean {
  switch (action.type) {
    case Actions.INITIALIZE:
      return action.state.app.editConfigJsonModalOpen;

    case Actions.EDIT_CONFIG_JSON_MODAL_OPEN:
      return true;

    case Actions.EDIT_CONFIG_JSON_MODAL_CLOSE:
    case Actions.SELECT_CONFIG:
      return false;

    default:
      return state;
  }
}

export const app = combineReducers<IAppState>({
  deleteConfigModalOpen,
  editConfigJsonModalOpen,
});
