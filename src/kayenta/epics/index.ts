import { Observable } from 'rxjs/Observable';
import 'rxjs/add/observable/concat';
import { Action, MiddlewareAPI } from 'redux';
import { createEpicMiddleware, combineEpics } from 'redux-observable';
import {
  createCanaryConfig,
  deleteCanaryConfig,
  getCanaryConfigById,
  mapStateToConfig,
  updateCanaryConfig
} from '../service/canaryConfig.service';
import * as Actions from '../actions/index';
import * as Creators from '../actions/creators';
import { ICanaryState } from '../reducers/index';
import { ReactInjector } from '@spinnaker/core';

const typeMatches = (...actions: string[]) => (action: Action & any) => actions.includes(action.type);

const loadConfigEpic = (action$: Observable<Action & any>) =>
  action$
    .filter(typeMatches(Actions.LOAD_CONFIG_REQUEST, Actions.SAVE_CONFIG_SUCCESS))
    .concatMap(action => getCanaryConfigById(action.id))
    .map(config => ({type: Actions.LOAD_CONFIG_SUCCESS, config}))
    .catch(error => Observable.of({type: Actions.LOAD_CONFIG_FAILURE, error}));

const selectConfigEpic = (action$: Observable<Action & any>) =>
  action$
    .filter(typeMatches(Actions.LOAD_CONFIG_SUCCESS))
    .map(action => ({type: Actions.SELECT_CONFIG, config: action.config}));

const saveConfigEpic = (action$: Observable<Action & any>, store: MiddlewareAPI<ICanaryState>) =>
  action$
    .filter(typeMatches(Actions.SAVE_CONFIG_REQUEST))
    .concatMap(() => {
      const config = mapStateToConfig(store.getState());
      let saveAction: Promise<{id: string}>;
      if (config.isNew) {
        delete config.isNew;
        saveAction = createCanaryConfig(config);
      } else {
        saveAction = updateCanaryConfig(config);
      }

      return Observable.fromPromise(saveAction)
        .map(() => Observable.forkJoin(
          ReactInjector.$state.go('^.configDetail', {configName: config.name}),
          store.getState().data.application.getDataSource('canaryConfigs').refresh(true)
        ))
        .mapTo({type: Actions.SAVE_CONFIG_SUCCESS, id: config.name})
        .catch((error: Error) => Observable.of({type: Actions.SAVE_CONFIG_FAILURE, error}));
    });

const deleteConfigRequestEpic = (action$: Observable<Action & any>, store: MiddlewareAPI<ICanaryState>) =>
  action$
    .filter(typeMatches(Actions.DELETE_CONFIG_REQUEST))
    .concatMap(() => deleteCanaryConfig(store.getState().selectedConfig.config.name))
    .mapTo(Creators.deleteConfigSuccess())
    .catch((error: Error) => Observable.of({type: Actions.DELETE_CONFIG_FAILURE, error}));

const deleteConfigSuccessEpic = (action$: Observable<Action & any>, store: MiddlewareAPI<ICanaryState>) =>
  action$
    .filter(typeMatches(Actions.DELETE_CONFIG_SUCCESS))
    .concatMap(() =>
      Observable.forkJoin(
        ReactInjector.$state.go('^.default'),
        // TODO: handle config summary load failure (in general, not just here).
        store.getState().data.application.getDataSource('canaryConfigs').refresh(true),
      )
    ).mapTo(Creators.closeDeleteConfigModal());

const rootEpic = combineEpics(
  loadConfigEpic,
  selectConfigEpic,
  saveConfigEpic,
  deleteConfigRequestEpic,
  deleteConfigSuccessEpic,
);

export const epicMiddleware = createEpicMiddleware(rootEpic);
