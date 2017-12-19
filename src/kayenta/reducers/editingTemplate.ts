import { combineActions, handleActions } from 'redux-actions';
import { Action, combineReducers } from 'redux';
import * as Actions from 'kayenta/actions';

export interface IEditingTemplateState {
  name: string;
  editedName: string;
  editedValue: string;
}

const name = handleActions<string>({
  [Actions.EDIT_TEMPLATE_BEGIN]: (_state, action: Action & any) => action.payload.name,
}, null);

const editedName = handleActions<string>({
  [combineActions(Actions.EDIT_TEMPLATE_BEGIN, Actions.EDIT_TEMPLATE_NAME)]:
    (_state, action: Action & any) => action.payload.name,
  [Actions.EDIT_TEMPLATE_CANCEL]: () => null,
}, null);

const editedValue = handleActions<string>({
  [combineActions(Actions.EDIT_TEMPLATE_VALUE, Actions.EDIT_TEMPLATE_BEGIN)]:
    (_state, action: Action & any) => action.payload.value,
  [Actions.EDIT_TEMPLATE_CANCEL]: () => null,
}, null);

export const editingTemplate = combineReducers<IEditingTemplateState>({
  name,
  editedName,
  editedValue,
});
