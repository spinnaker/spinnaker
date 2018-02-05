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
import { runSelector } from '../selectors/index';
import { ICanaryConfigUpdateResponse } from '../domain/ICanaryConfigUpdateResponse';
import { listMetricsServiceMetadata } from '../service/metricsServiceMetadata.service';

const typeMatches = (...actions: string[]) => (action: Action & any) => actions.includes(action.type);

const loadConfigEpic = (action$: Observable<Action & any>) =>
  action$
    .filter(typeMatches(Actions.LOAD_CONFIG_REQUEST, Actions.SAVE_CONFIG_SUCCESS))
    .concatMap(action =>
      Observable.fromPromise(getCanaryConfigById(action.payload.id))
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
      let saveAction: Promise<ICanaryConfigUpdateResponse>;
      if (config.isNew) {
        delete config.isNew;
        saveAction = createCanaryConfig(config);
      } else {
        saveAction = updateCanaryConfig(config);
      }

      return Observable.fromPromise(saveAction)
        .concatMap(({ canaryConfigId }) =>
          Observable.forkJoin(
            ReactInjector.$state.go('^.configDetail', { id: canaryConfigId, copy: false, 'new': false }),
            store.getState().data.application.getDataSource('canaryConfigs').refresh(true)
          ).mapTo(Creators.saveConfigSuccess({ id: canaryConfigId }))
        )
        .catch((error: Error) => Observable.of(Creators.saveConfigFailure({ error })));
    });

const deleteConfigRequestEpic = (action$: Observable<Action & any>, store: MiddlewareAPI<ICanaryState>) =>
  action$
    .filter(typeMatches(Actions.DELETE_CONFIG_REQUEST))
    .concatMap(() =>
      Observable.fromPromise(deleteCanaryConfig(store.getState().selectedConfig.config.id))
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
      Observable.fromPromise(getCanaryRun(action.payload.configId, action.payload.runId))
        .map(run => Creators.loadRunSuccess({ run }))
        .catch((error: Error) => Observable.of(Creators.loadRunFailure({ error })))
    );

const loadMetricSetPairEpic = (action$: Observable<Action & any>, store: MiddlewareAPI<ICanaryState>) =>
  action$
    .filter(typeMatches(Actions.LOAD_METRIC_SET_PAIR_REQUEST))
    .concatMap(action => {
      const run = runSelector(store.getState());
      return Observable.fromPromise(getMetricSetPair(run.result.metricSetPairListId, action.payload.pairId))
        .map(metricSetPair => Creators.loadMetricSetPairSuccess({ metricSetPair }))
        .catch((error: Error) => Observable.of(Creators.loadMetricSetPairFailure({ error })))
    });

const updateStackdriverMetricDescriptionFilterEpic = (action$: Observable<Action & any>) =>
  action$
    .filter(typeMatches(Actions.UPDATE_STACKDRIVER_METRIC_DESCRIPTOR_FILTER))
    .filter(action => action.payload.filter && action.payload.filter.length > 2)
    .debounceTime(200 /* milliseconds */)
    .map(action => Creators.loadMetricsServiceMetadataRequest({ filter: action.payload.filter }));

const loadMetricsServiceMetadataEpic = (action$: Observable<Action & any>) =>
  action$
    .filter(typeMatches(Actions.LOAD_METRICS_SERVICE_METADATA_REQUEST))
    .concatMap(action => {
      return Observable.fromPromise(listMetricsServiceMetadata(action.payload.filter))
        .map(data => Creators.loadMetricsServiceMetadataSuccess({ data }))
        .catch((error: Error) => Observable.of(Creators.loadMetricsServiceMetadataFailure({ error })));
    });

const rootEpic = combineEpics(
  loadConfigEpic,
  selectConfigEpic,
  saveConfigEpic,
  deleteConfigRequestEpic,
  deleteConfigSuccessEpic,
  loadCanaryRunRequestEpic,
  loadMetricSetPairEpic,
  updateStackdriverMetricDescriptionFilterEpic,
  loadMetricsServiceMetadataEpic,
);

export const epicMiddleware: EpicMiddleware<Action & any, ICanaryState> = createEpicMiddleware(rootEpic);
