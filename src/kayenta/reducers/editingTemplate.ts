import * as Actions from 'kayenta/actions';
import { Action, combineReducers } from 'redux';
import { combineActions, handleActions } from 'redux-actions';

export interface IEditingTemplateState {
  name: string;
  editedName: string;
  editedValue: string;
  isNew: boolean;
}

const name = handleActions<string>(
  {
    [Actions.EDIT_TEMPLATE_BEGIN]: (_state, action: Action & any) => action.payload.name,
    [Actions.EDIT_TEMPLATE_CANCEL]: () => null,
    [Actions.ADD_TEMPLATE]: () => '',
  },
  null,
);

const editedName = handleActions<string>(
  {
    [combineActions(Actions.EDIT_TEMPLATE_BEGIN, Actions.EDIT_TEMPLATE_NAME)]: (_state, action: Action & any) =>
      action.payload.name,
    [Actions.EDIT_TEMPLATE_CANCEL]: () => null,
    [Actions.ADD_TEMPLATE]: () => '',
  },
  null,
);

const editedValue = handleActions<string>(
  {
    [combineActions(Actions.EDIT_TEMPLATE_VALUE, Actions.EDIT_TEMPLATE_BEGIN)]: (_state, action: Action & any) =>
      action.payload.value,
    [Actions.EDIT_TEMPLATE_CANCEL]: () => null,
    [Actions.ADD_TEMPLATE]: () => '',
  },
  null,
);

const isNew = handleActions<boolean>(
  {
    [Actions.ADD_TEMPLATE]: () => true,
    [Actions.EDIT_TEMPLATE_CANCEL]: () => false,
  },
  false,
);

export const editingTemplate = combineReducers<IEditingTemplateState>({
  name,
  editedName,
  editedValue,
  isNew,
});
