import { Observable } from 'rxjs/Observable';
import 'rxjs/add/observable/concat';
import { Action, MiddlewareAPI } from 'redux';
import { createEpicMiddleware, combineEpics, EpicMiddleware } from 'redux-observable';
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
import {
  getCanaryRun,
  getMetricSetPair
} from '../service/run/canaryRun.service';
import { configNameSelector, runSelector } from '../selectors/index';

const typeMatches = (...actions: string[]) => (action: Action & any) => actions.includes(action.type);

const loadConfigEpic = (action$: Observable<Action & any>) =>
  action$
    .filter(typeMatches(Actions.LOAD_CONFIG_REQUEST, Actions.SAVE_CONFIG_SUCCESS))
    .concatMap(action =>
      Observable.fromPromise(getCanaryConfigById(action.payload.configName))
        .map(config => Creators.loadConfigSuccess({ config }))
        .catch(error => Observable.of(Creators.loadConfigFailure({ error })))
    );

const selectConfigEpic = (action$: Observable<Action & any>) =>
  action$
    .filter(typeMatches(Actions.LOAD_CONFIG_SUCCESS))
    .map(action => Creators.selectConfig({ config: action.payload.config }));

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
          ReactInjector.$state.go('^.configDetail', {configName: config.name, copy: false, 'new': false}),
          store.getState().data.application.getDataSource('canaryConfigs').refresh(true)
        ))
        .mapTo(Creators.saveConfigSuccess({ configName: config.name }))
        .catch((error: Error) => Observable.of(Creators.saveConfigFailure({ error })));
    });

const deleteConfigRequestEpic = (action$: Observable<Action & any>, store: MiddlewareAPI<ICanaryState>) =>
  action$
    .filter(typeMatches(Actions.DELETE_CONFIG_REQUEST))
    .concatMap(() =>
      Observable.fromPromise(deleteCanaryConfig(store.getState().selectedConfig.config.name))
        .mapTo(Creators.deleteConfigSuccess())
        .catch((error: Error) => Observable.of(Creators.deleteConfigFailure({ error })))
    );

const deleteConfigSuccessEpic = (action$: Observable<Action & any>, store: MiddlewareAPI<ICanaryState>) =>
  action$
    .filter(typeMatches(Actions.DELETE_CONFIG_SUCCESS))
    .concatMap(() =>
      Observable.forkJoin(
        ReactInjector.$state.go('^.configDefault'),
        // TODO: handle config summary load failure (in general, not just here).
        store.getState().data.application.getDataSource('canaryConfigs').refresh(true),
      )
    ).mapTo(Creators.closeDeleteConfigModal());

const loadCanaryRunRequestEpic = (action$: Observable<Action & any>) =>
  action$
    .filter(typeMatches(Actions.LOAD_RUN_REQUEST))
    .concatMap(action =>
      Observable.fromPromise(getCanaryRun(action.payload.configName, action.payload.runId))
        .map(run => Creators.loadRunSuccess({ run }))
        .catch((error: Error) => Observable.of(Creators.loadRunFailure({ error })))
    );

const loadMetricSetPairEpic = (action$: Observable<Action & any>, store: MiddlewareAPI<ICanaryState>) =>
  action$
    .filter(typeMatches(Actions.LOAD_METRIC_SET_PAIR_REQUEST))
    .concatMap(action => {
      const configName = configNameSelector(store.getState());
      const runId = runSelector(store.getState()).id;

      return Observable.fromPromise(getMetricSetPair(configName, runId, action.payload.pairId))
        .map(metricSetPair => Creators.loadMetricSetPairSuccess({ metricSetPair }))
        .catch((error: Error) => Observable.of(Creators.loadMetricSetPairFailure({ error })))
    });

const rootEpic = combineEpics(
  loadConfigEpic,
  selectConfigEpic,
  saveConfigEpic,
  deleteConfigRequestEpic,
  deleteConfigSuccessEpic,
  loadCanaryRunRequestEpic,
  loadMetricSetPairEpic,
);

export const epicMiddleware: EpicMiddleware<Action & any, ICanaryState> = createEpicMiddleware(rootEpic);
