import { Observable } from 'rxjs/Observable';
import 'rxjs/add/observable/concat';
import { MiddlewareAPI } from 'redux';
import { createEpicMiddleware, combineEpics } from 'redux-observable';
import {
  deleteCanaryConfig,
  getCanaryConfigById, mapStateToConfig,
  updateCanaryConfig
} from '../service/canaryConfig.service';
import {
  CONFIG_LOAD_ERROR,
  LOAD_CONFIG, SAVE_CONFIG_ERROR, SAVE_CONFIG_SAVING, SAVE_CONFIG_SAVED,
  SELECT_CONFIG, DELETE_CONFIG_DELETING, DELETE_CONFIG_COMPLETED,
  DELETE_CONFIG_ERROR, DELETE_CONFIG_MODAL_CLOSE
} from '../actions/index';
import { ICanaryState } from '../reducers/index';
import { ReactInjector } from '@spinnaker/core';

const selectConfigEpic = (action$: Observable<any>) =>
  action$
    .filter(action => action.type === LOAD_CONFIG)
    .concatMap(action =>
      getCanaryConfigById(action.id)
        .then(config => ({type: SELECT_CONFIG, config}))
        .catch(error => ({type: CONFIG_LOAD_ERROR, error}))
    );

const saveConfigEpic = (action$: Observable<any>, store: MiddlewareAPI<ICanaryState>) =>
  action$
    .filter(action => action.type === SAVE_CONFIG_SAVING)
    .concatMap(() =>
      updateCanaryConfig(mapStateToConfig(store.getState()))
        .then(configName => ({type: SAVE_CONFIG_SAVED, configName}))
        .catch(error => ({type: SAVE_CONFIG_ERROR, error}))
    );

const deleteConfigEpic = (action$: Observable<any>, store: MiddlewareAPI<ICanaryState>) =>
  action$
    .filter(action => action.type === DELETE_CONFIG_DELETING)
    .concatMap(() =>
      deleteCanaryConfig(store.getState().selectedConfig.name)
        .then(() => ({type: DELETE_CONFIG_COMPLETED}))
        .catch(error => ({type: DELETE_CONFIG_ERROR, error}))
    );

const deleteConfigCompletedEpic = (action$: Observable<any>, store: MiddlewareAPI<ICanaryState>) =>
  action$
    .filter(action => action.type === DELETE_CONFIG_COMPLETED)
    .concatMap(() =>
      Promise.all([
        ReactInjector.$state.go('^'), // Why does '.canary' not work?
        // TODO: handle config summary load failure (in general, not just here).
        store.getState().application.getDataSource('canaryConfigs').refresh(true),
      ]).then(() => ({type: DELETE_CONFIG_MODAL_CLOSE}))
    );

const rootEpic = combineEpics(
  selectConfigEpic,
  saveConfigEpic,
  deleteConfigEpic,
  deleteConfigCompletedEpic,
);

export const epicMiddleware = createEpicMiddleware(rootEpic);
