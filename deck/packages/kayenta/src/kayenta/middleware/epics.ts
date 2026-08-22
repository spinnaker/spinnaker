import type { UIRouter } from '@uirouter/core';
import type { Action, MiddlewareAPI } from 'redux';
import type { EpicMiddleware } from 'redux-observable';
import { combineEpics, createEpicMiddleware } from 'redux-observable';
import type { Observable } from 'rxjs';
import { forkJoin } from 'rxjs/observable/forkJoin';
import { from } from 'rxjs/observable/from';
import { of } from 'rxjs/observable/of';
import { catchError, concatMap, debounceTime, filter, map, mapTo } from 'rxjs/operators';

import * as Actions from '../actions';
import * as Creators from '../actions/creators';
import type { ICanaryConfigUpdateResponse } from '../domain';
import { KayentaAccountType } from '../domain';
import type { ICanaryState } from '../reducers';
import { runSelector } from '../selectors';
import {
  createCanaryConfig,
  deleteCanaryConfig,
  getCanaryConfigById,
  listKayentaAccounts,
  mapStateToConfig,
  updateCanaryConfig,
} from '../service/canaryConfig.service';
import { getCanaryRun, getMetricSetPair } from '../service/canaryRun.service';
import { listMetricsServiceMetadata } from '../service/metricsServiceMetadata.service';

const typeMatches = (...actions: string[]) => (action: Action & any) => actions.includes(action.type);

const loadConfigEpic = (action$: Observable<Action & any>) =>
  action$.pipe(
    filter(typeMatches(Actions.LOAD_CONFIG_REQUEST, Actions.SAVE_CONFIG_SUCCESS)),
    concatMap((action) =>
      from(getCanaryConfigById(action.payload.id)).pipe(
        map((config) => Creators.loadConfigSuccess({ config })),
        catchError((error) => of(Creators.loadConfigFailure({ error }))),
      ),
    ),
  );

const selectConfigEpic = (action$: Observable<Action & any>) =>
  action$.pipe(
    filter(typeMatches(Actions.LOAD_CONFIG_SUCCESS)),
    map((action) => Creators.selectConfig({ config: action.payload.config })),
  );

const saveConfigEpic = (uiRouter: UIRouter) => (
  action$: Observable<Action & any>,
  store: MiddlewareAPI<ICanaryState>,
) =>
  action$.pipe(
    filter(typeMatches(Actions.SAVE_CONFIG_REQUEST)),
    concatMap(() => {
      const config = mapStateToConfig(store.getState());
      let saveAction: Promise<ICanaryConfigUpdateResponse>;
      if (config.isNew) {
        delete config.isNew;
        saveAction = createCanaryConfig(config);
      } else {
        saveAction = updateCanaryConfig(config);
      }

      return from(saveAction).pipe(
        concatMap(({ canaryConfigId }) =>
          forkJoin(
            uiRouter.stateService.go('^.configDetail', { id: canaryConfigId, copy: false, new: false }),
            store.getState().data.application.getDataSource('canaryConfigs').refresh(true),
          ).pipe(mapTo(Creators.saveConfigSuccess({ id: canaryConfigId }))),
        ),
        catchError((error: Error) => of(Creators.saveConfigFailure({ error }))),
      );
    }),
  );

const deleteConfigRequestEpic = (action$: Observable<Action & any>, store: MiddlewareAPI<ICanaryState>) =>
  action$.pipe(
    filter(typeMatches(Actions.DELETE_CONFIG_REQUEST)),
    concatMap(() =>
      from(deleteCanaryConfig(store.getState().selectedConfig.config.id)).pipe(
        mapTo(Creators.deleteConfigSuccess()),
        catchError((error: Error) => of(Creators.deleteConfigFailure({ error }))),
      ),
    ),
  );

const deleteConfigSuccessEpic = (uiRouter: UIRouter) => (
  action$: Observable<Action & any>,
  store: MiddlewareAPI<ICanaryState>,
) =>
  action$.pipe(
    filter(typeMatches(Actions.DELETE_CONFIG_SUCCESS)),
    concatMap(() =>
      forkJoin(
        uiRouter.stateService.go('^.configDefault'),
        // TODO: handle config summary load failure (in general, not just here).
        store.getState().data.application.getDataSource('canaryConfigs').refresh(true),
      ),
    ),
    mapTo(Creators.closeDeleteConfigModal()),
  );

const loadCanaryRunRequestEpic = (action$: Observable<Action & any>) =>
  action$.pipe(
    filter(typeMatches(Actions.LOAD_RUN_REQUEST)),
    concatMap((action) =>
      from(getCanaryRun(action.payload.configId, action.payload.runId)).pipe(
        map((run) => Creators.loadRunSuccess({ run })),
        catchError((error: Error) => of(Creators.loadRunFailure({ error }))),
      ),
    ),
  );

const loadMetricSetPairEpic = (action$: Observable<Action & any>, store: MiddlewareAPI<ICanaryState>) =>
  action$.pipe(
    filter(typeMatches(Actions.LOAD_METRIC_SET_PAIR_REQUEST)),
    concatMap((action) => {
      const run = runSelector(store.getState());
      return from(getMetricSetPair(run.metricSetPairListId, action.payload.pairId)).pipe(
        map((metricSetPair) => Creators.loadMetricSetPairSuccess({ metricSetPair })),
        catchError((error: Error) => of(Creators.loadMetricSetPairFailure({ error }))),
      );
    }),
  );

const updatePrometheusMetricDescriptionFilterEpic = (action$: Observable<Action & any>) =>
  action$.pipe(
    filter(typeMatches(Actions.UPDATE_PROMETHEUS_METRIC_DESCRIPTOR_FILTER)),
    filter((action) => action.payload.filter && action.payload.filter.length > 2),
    debounceTime(200 /* milliseconds */),
    map((action) => {
      return Creators.loadMetricsServiceMetadataRequest({
        filter: action.payload.filter,
        metricsAccountName: action.payload.metricsAccountName,
      });
    }),
  );

const updateStackdriverMetricDescriptionFilterEpic = (
  action$: Observable<Action & any>,
  store: MiddlewareAPI<ICanaryState>,
) =>
  action$.pipe(
    filter(typeMatches(Actions.UPDATE_STACKDRIVER_METRIC_DESCRIPTOR_FILTER)),
    filter((action) => action.payload.filter && action.payload.filter.length > 2),
    debounceTime(200 /* milliseconds */),
    map((action) => {
      const [metricsAccountName] = store
        .getState()
        .data.kayentaAccounts.data.filter(
          (account) =>
            account.supportedTypes.includes(KayentaAccountType.MetricsStore) &&
            account.metricsStoreType === 'stackdriver',
        )
        .map((account) => account.name);

      return Creators.loadMetricsServiceMetadataRequest({
        filter: action.payload.filter,
        metricsAccountName,
      });
    }),
  );

const updateGraphiteMetricDescriptionFilterEpic = (
  action$: Observable<Action & any>,
  store: MiddlewareAPI<ICanaryState>,
) =>
  action$.pipe(
    filter(typeMatches(Actions.UPDATE_GRAPHITE_METRIC_DESCRIPTOR_FILTER)),
    filter((action) => action.payload.filter && action.payload.filter.length > 2),
    debounceTime(200 /* milliseconds */),
    map((action) => {
      const [metricsAccountName] = store
        .getState()
        .data.kayentaAccounts.data.filter(
          (account) => account.supportedTypes.includes(KayentaAccountType.MetricsStore) && account.type === 'graphite',
        )
        .map((account) => account.name);

      return Creators.loadMetricsServiceMetadataRequest({
        filter: action.payload.filter,
        metricsAccountName,
      });
    }),
  );

const updateDatadogMetricDescriptionFilterEpic = (
  action$: Observable<Action & any>,
  store: MiddlewareAPI<ICanaryState>,
) =>
  action$.pipe(
    filter(typeMatches(Actions.UPDATE_DATADOG_METRIC_DESCRIPTOR_FILTER)),
    filter((action) => action.payload.filter && action.payload.filter.length > 2),
    debounceTime(200 /* milliseconds */),
    map((action) => {
      const [metricsAccountName] = store
        .getState()
        .data.kayentaAccounts.data.filter(
          (account) => account.supportedTypes.includes(KayentaAccountType.MetricsStore) && account.type === 'datadog',
        )
        .map((account) => account.name);

      return Creators.loadMetricsServiceMetadataRequest({
        filter: action.payload.filter,
        metricsAccountName,
      });
    }),
  );

const loadMetricsServiceMetadataEpic = (action$: Observable<Action & any>) =>
  action$.pipe(
    filter(typeMatches(Actions.LOAD_METRICS_SERVICE_METADATA_REQUEST)),
    concatMap((action) =>
      from(listMetricsServiceMetadata(action.payload.filter, action.payload.metricsAccountName)).pipe(
        map((data) => Creators.loadMetricsServiceMetadataSuccess({ data })),
        catchError((error: Error) => of(Creators.loadMetricsServiceMetadataFailure({ error }))),
      ),
    ),
  );

const loadKayentaAccountsEpic = (action$: Observable<Action & any>) =>
  action$.pipe(
    filter(typeMatches(Actions.LOAD_KAYENTA_ACCOUNTS_REQUEST, Actions.INITIALIZE)),
    concatMap(() =>
      from(listKayentaAccounts()).pipe(
        map((accounts) => Creators.loadKayentaAccountsSuccess({ accounts })),
        catchError((error: Error) => of(Creators.loadKayentaAccountsFailure({ error }))),
      ),
    ),
  );

export const createKayentaEpicMiddleware = (uiRouter: UIRouter): EpicMiddleware<Action & any, ICanaryState> =>
  createEpicMiddleware(
    combineEpics(
      loadConfigEpic,
      selectConfigEpic,
      saveConfigEpic(uiRouter),
      deleteConfigRequestEpic,
      deleteConfigSuccessEpic(uiRouter),
      loadCanaryRunRequestEpic,
      loadMetricSetPairEpic,
      updateGraphiteMetricDescriptionFilterEpic,
      updatePrometheusMetricDescriptionFilterEpic,
      updateStackdriverMetricDescriptionFilterEpic,
      updateDatadogMetricDescriptionFilterEpic,
      loadMetricsServiceMetadataEpic,
      loadKayentaAccountsEpic,
    ),
  );
