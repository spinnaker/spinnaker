import { Action, combineReducers, Reducer } from 'redux';
import { combineActions, handleActions } from 'redux-actions';

import * as Actions from '../actions';
import { CanarySettings } from '../canary.settings';
import { ConfigJsonModalTabState } from '../edit/configJsonModal';

export interface IAppState {
  deleteConfigModalOpen: boolean;
  configJsonModalOpen: boolean;
  configJsonModalTabState: ConfigJsonModalTabState;
  disableConfigEdit: boolean;
  executionsCount: number;
}

const executionsCount = handleActions(
  {
    [Actions.SET_EXECUTIONS_COUNT]: (_state: IAppState, action: Action & any) => action.payload.count,
  },
  CanarySettings.defaultExecutionCount ?? CanarySettings.executionsCountOptions?.[0] ?? 20,
);

const deleteConfigModalOpen = handleActions(
  {
    [Actions.DELETE_CONFIG_MODAL_OPEN]: () => true,
    [combineActions(Actions.DELETE_CONFIG_MODAL_CLOSE, Actions.DELETE_CONFIG_SUCCESS)]: () => false,
  },
  false,
);

const configJsonModalOpen = handleActions(
  {
    [Actions.CONFIG_JSON_MODAL_OPEN]: () => true,
    [combineActions(Actions.CONFIG_JSON_MODAL_CLOSE, Actions.SELECT_CONFIG)]: () => false,
  },
  false,
);

const configJsonModalTabState = handleActions(
  {
    [Actions.SET_CONFIG_JSON_MODAL_TAB_STATE]: (_state: ConfigJsonModalTabState, action: Action & any) =>
      action.payload.state,
    [Actions.CONFIG_JSON_MODAL_OPEN]: () => ConfigJsonModalTabState.Edit,
  },
  ConfigJsonModalTabState.Edit,
);

const disableConfigEdit = handleActions<boolean>({}, false);

export const app: Reducer<IAppState> = combineReducers<IAppState>({
  executionsCount,
  deleteConfigModalOpen,
  configJsonModalOpen,
  configJsonModalTabState,
  disableConfigEdit,
});
