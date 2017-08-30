import { combineReducers } from 'redux';
import { IDataState, data } from './data';
import { app, IAppState } from './app';
import {
  ISelectedConfigState,
  selectedConfig
} from './selectedConfig';

export interface ICanaryState {
  data: IDataState;
  selectedConfig: ISelectedConfigState;
  app: IAppState;
}

export const rootReducer = combineReducers<ICanaryState>({
  data,
  app,
  selectedConfig,
});
