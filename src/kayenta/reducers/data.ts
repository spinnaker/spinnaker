import { Action, combineReducers } from 'redux';
import { without } from 'lodash';

import { Application } from '@spinnaker/core';

import * as Actions from '../actions';
import { ICanaryConfigSummary } from '../domain/ICanaryConfigSummary';
import { IJudge } from '../domain/IJudge';
import { ICanaryConfig } from '../domain/ICanaryConfig';

export interface IDataState {
  application: Application;
  configSummaries: ICanaryConfigSummary[];
  configs: ICanaryConfig[];
  judges: IJudge[];
}

function application(state: Application = null, action: Action & any): Application {
  switch (action.type) {
    case Actions.INITIALIZE:
      return action.state.data.application;

    default:
      return state;
  }
}

function configSummaries(state: ICanaryConfigSummary[] = [], action: Action & any): ICanaryConfigSummary[] {
  switch (action.type) {
    case Actions.INITIALIZE:
      return action.state.data.configSummaries;

    case Actions.UPDATE_CONFIG_SUMMARIES:
      return action.configSummaries;

    default:
      return state;
  }
}

function configs(state: ICanaryConfig[] = [], action: Action & any): ICanaryConfig[] {
  switch (action.type) {
    case Actions.LOAD_CONFIG_SUCCESS:
      if (state.some(config => config.name === action.config.name)) {
        return without(
          state,
          state.find(config => config.name === action.config.name)
        ).concat([action.config]);
      } else {
        return state.concat([action.config]);
      }

    default:
      return state;
  }
}

function judges(state: IJudge[] = null, action: Action & any): IJudge[] {
  switch (action.type) {
    case Actions.INITIALIZE:
      return action.state.data.judges;

    case Actions.UPDATE_JUDGES:
      return action.judges;

    default:
      return state;
  }
}

export const data = combineReducers<IDataState>({
  application,
  configSummaries,
  judges,
  configs,
});
