import { Observable } from 'rxjs/Observable';
import 'rxjs/add/observable/concat';
import { Action, MiddlewareAPI } from 'redux';
import { createEpicMiddleware, combineEpics } from 'redux-observable';
import {
  createCanaryConfig,
  deleteCanaryConfig,
  getCanaryConfigById, mapStateToConfig,
  updateCanaryConfig
} from '../service/canaryConfig.service';
import * as Actions from '../actions/index';
import { ICanaryState } from '../reducers/index';
import { ReactInjector } from '@spinnaker/core';

const loadConfigEpic = (action$: Observable<Action & any>) =>
  action$
    .filter(action => [Actions.LOAD_CONFIG_REQUEST, Actions.SAVE_CONFIG_SUCCESS].includes(action.type))
    .concatMap(action =>
      getCanaryConfigById(action.id)
        .then(config => ({type: Actions.LOAD_CONFIG_SUCCESS, config}))
        .catch(error => ({type: Actions.LOAD_CONFIG_FAILURE, error}))
    );

const selectConfigEpic = (action$: Observable<Action & any>) =>
  action$
    .filter(action => action.type === Actions.LOAD_CONFIG_SUCCESS)
    .map(action => ({type: Actions.SELECT_CONFIG, config: action.config}));

const saveConfigEpic = (action$: Observable<Action & any>, store: MiddlewareAPI<ICanaryState>) =>
  action$
    .filter(action => action.type === Actions.SAVE_CONFIG_REQUEST)
    .concatMap(() => {
      const config = mapStateToConfig(store.getState());
      let saveAction: Promise<{id: string}>;
      if (config.isNew) {
        delete config.isNew;
        saveAction = createCanaryConfig(config);
      } else {
        saveAction = updateCanaryConfig(config);
      }

      return saveAction
        .then(() => Promise.all([
          ReactInjector.$state.go('^.configDetail', {configName: config.name}),
          store.getState().data.application.getDataSource('canaryConfigs').refresh(true),
        ]))
        .then(() => ({type: Actions.SAVE_CONFIG_SUCCESS, id: config.name}))
        .catch((error: Error) => ({type: Actions.SAVE_CONFIG_FAILURE, error}));
    });

const deleteConfigRequestEpic = (action$: Observable<Action & any>, store: MiddlewareAPI<ICanaryState>) =>
  action$
    .filter(action => action.type === Actions.DELETE_CONFIG_REQUEST)
    .concatMap(() =>
      deleteCanaryConfig(store.getState().selectedConfig.config.name)
        .then(() => ({type: Actions.DELETE_CONFIG_SUCCESS}))
        .catch(error => ({type: Actions.DELETE_CONFIG_FAILURE, error}))
    );

const deleteConfigSuccessEpic = (action$: Observable<Action & any>, store: MiddlewareAPI<ICanaryState>) =>
  action$
    .filter(action => action.type === Actions.DELETE_CONFIG_SUCCESS)
    .concatMap(() =>
      Promise.all([
        ReactInjector.$state.go('^.default'),
        // TODO: handle config summary load failure (in general, not just here).
        store.getState().data.application.getDataSource('canaryConfigs').refresh(true),
      ]).then(() => ({type: Actions.DELETE_CONFIG_MODAL_CLOSE}))
    );

const rootEpic = combineEpics(
  loadConfigEpic,
  selectConfigEpic,
  saveConfigEpic,
  deleteConfigRequestEpic,
  deleteConfigSuccessEpic,
);

export const epicMiddleware = createEpicMiddleware(rootEpic);
