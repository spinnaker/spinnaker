import * as Actions from 'kayenta/actions';
import * as Creators from 'kayenta/actions/creators';
import { ICanaryConfigUpdateResponse, KayentaAccountType } from 'kayenta/domain';
import { ICanaryState } from 'kayenta/reducers';
import { runSelector } from 'kayenta/selectors';
import {
  createCanaryConfig,
  deleteCanaryConfig,
  getCanaryConfigById,
  listKayentaAccounts,
  mapStateToConfig,
  updateCanaryConfig,
} from 'kayenta/service/canaryConfig.service';
import { listMetricsServiceMetadata } from 'kayenta/service/metricsServiceMetadata.service';
import { Action, MiddlewareAPI } from 'redux';
import { combineEpics, createEpicMiddleware, EpicMiddleware } from 'redux-observable';
import { Observable } from 'rxjs/Observable';
import 'rxjs/add/observable/concat';
import 'rxjs/add/observable/forkJoin';
import 'rxjs/add/observable/fromPromise';
import 'rxjs/add/operator/catch';
import 'rxjs/add/operator/concatMap';
import 'rxjs/add/operator/debounceTime';
import 'rxjs/add/operator/filter';
import 'rxjs/add/operator/map';
import 'rxjs/add/operator/mapTo';

import { ReactInjector } from '@spinnaker/core';

import { getCanaryRun, getMetricSetPair } from '../service/canaryRun.service';

const typeMatches = (...actions: string[]) => (action: Action & any) => actions.includes(action.type);

const loadConfigEpic = (action$: Observable<Action & any>) =>
  action$.filter(typeMatches(Actions.LOAD_CONFIG_REQUEST, Actions.SAVE_CONFIG_SUCCESS)).concatMap((action) =>
    Observable.fromPromise(getCanaryConfigById(action.payload.id))
      .map((config) => Creators.loadConfigSuccess({ config }))
      .catch((error) => Observable.of(Creators.loadConfigFailure({ error }))),
  );

const selectConfigEpic = (action$: Observable<Action & any>) =>
  action$
    .filter(typeMatches(Actions.LOAD_CONFIG_SUCCESS))
    .map((action) => Creators.selectConfig({ config: action.payload.config }));

const saveConfigEpic = (action$: Observable<Action & any>, store: MiddlewareAPI<ICanaryState>) =>
  action$.filter(typeMatches(Actions.SAVE_CONFIG_REQUEST)).concatMap(() => {
    const config = mapStateToConfig(store.getState());
    let saveAction: PromiseLike<ICanaryConfigUpdateResponse>;
    if (config.isNew) {
      delete config.isNew;
      saveAction = createCanaryConfig(config);
    } else {
      saveAction = updateCanaryConfig(config);
    }

    return Observable.fromPromise(saveAction)
      .concatMap(({ canaryConfigId }) =>
        Observable.forkJoin(
          ReactInjector.$state.go('^.configDetail', { id: canaryConfigId, copy: false, new: false }),
          store.getState().data.application.getDataSource('canaryConfigs').refresh(true),
        ).mapTo(Creators.saveConfigSuccess({ id: canaryConfigId })),
      )
      .catch((error: Error) => Observable.of(Creators.saveConfigFailure({ error })));
  });

const deleteConfigRequestEpic = (action$: Observable<Action & any>, store: MiddlewareAPI<ICanaryState>) =>
  action$.filter(typeMatches(Actions.DELETE_CONFIG_REQUEST)).concatMap(() =>
    Observable.fromPromise(deleteCanaryConfig(store.getState().selectedConfig.config.id))
      .mapTo(Creators.deleteConfigSuccess())
      .catch((error: Error) => Observable.of(Creators.deleteConfigFailure({ error }))),
  );

const deleteConfigSuccessEpic = (action$: Observable<Action & any>, store: MiddlewareAPI<ICanaryState>) =>
  action$
    .filter(typeMatches(Actions.DELETE_CONFIG_SUCCESS))
    .concatMap(() =>
      Observable.forkJoin(
        ReactInjector.$state.go('^.configDefault'),
        // TODO: handle config summary load failure (in general, not just here).
        store.getState().data.application.getDataSource('canaryConfigs').refresh(true),
      ),
    )
    .mapTo(Creators.closeDeleteConfigModal());

const loadCanaryRunRequestEpic = (action$: Observable<Action & any>) =>
  action$.filter(typeMatches(Actions.LOAD_RUN_REQUEST)).concatMap((action) =>
    Observable.fromPromise(getCanaryRun(action.payload.configId, action.payload.runId))
      .map((run) => Creators.loadRunSuccess({ run }))
      .catch((error: Error) => Observable.of(Creators.loadRunFailure({ error }))),
  );

const loadMetricSetPairEpic = (action$: Observable<Action & any>, store: MiddlewareAPI<ICanaryState>) =>
  action$.filter(typeMatches(Actions.LOAD_METRIC_SET_PAIR_REQUEST)).concatMap((action) => {
    const run = runSelector(store.getState());
    return Observable.fromPromise(getMetricSetPair(run.metricSetPairListId, action.payload.pairId))
      .map((metricSetPair) => Creators.loadMetricSetPairSuccess({ metricSetPair }))
      .catch((error: Error) => Observable.of(Creators.loadMetricSetPairFailure({ error })));
  });

const updatePrometheusMetricDescriptionFilterEpic = (action$: Observable<Action & any>) =>
  action$
    .filter(typeMatches(Actions.UPDATE_PROMETHEUS_METRIC_DESCRIPTOR_FILTER))
    .filter((action) => action.payload.filter && action.payload.filter.length > 2)
    .debounceTime(200 /* milliseconds */)
    .map((action) => {
      return Creators.loadMetricsServiceMetadataRequest({
        filter: action.payload.filter,
        metricsAccountName: action.payload.metricsAccountName,
      });
    });

const updateStackdriverMetricDescriptionFilterEpic = (
  action$: Observable<Action & any>,
  store: MiddlewareAPI<ICanaryState>,
) =>
  action$
    .filter(typeMatches(Actions.UPDATE_STACKDRIVER_METRIC_DESCRIPTOR_FILTER))
    .filter((action) => action.payload.filter && action.payload.filter.length > 2)
    .debounceTime(200 /* milliseconds */)
    .map((action) => {
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
    });

const updateGraphiteMetricDescriptionFilterEpic = (
  action$: Observable<Action & any>,
  store: MiddlewareAPI<ICanaryState>,
) =>
  action$
    .filter(typeMatches(Actions.UPDATE_GRAPHITE_METRIC_DESCRIPTOR_FILTER))
    .filter((action) => action.payload.filter && action.payload.filter.length > 2)
    .debounceTime(200 /* milliseconds */)
    .map((action) => {
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
    });

const updateDatadogMetricDescriptionFilterEpic = (
  action$: Observable<Action & any>,
  store: MiddlewareAPI<ICanaryState>,
) =>
  action$
    .filter(typeMatches(Actions.UPDATE_DATADOG_METRIC_DESCRIPTOR_FILTER))
    .filter((action) => action.payload.filter && action.payload.filter.length > 2)
    .debounceTime(200 /* milliseconds */)
    .map((action) => {
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
    });

const loadMetricsServiceMetadataEpic = (action$: Observable<Action & any>) =>
  action$.filter(typeMatches(Actions.LOAD_METRICS_SERVICE_METADATA_REQUEST)).concatMap((action) => {
    return Observable.fromPromise(listMetricsServiceMetadata(action.payload.filter, action.payload.metricsAccountName))
      .map((data) => Creators.loadMetricsServiceMetadataSuccess({ data }))
      .catch((error: Error) => Observable.of(Creators.loadMetricsServiceMetadataFailure({ error })));
  });

const loadKayentaAccountsEpic = (action$: Observable<Action & any>) =>
  action$.filter(typeMatches(Actions.LOAD_KAYENTA_ACCOUNTS_REQUEST, Actions.INITIALIZE)).concatMap(() =>
    Observable.fromPromise(listKayentaAccounts())
      .map((accounts) => Creators.loadKayentaAccountsSuccess({ accounts }))
      .catch((error: Error) => Observable.of(Creators.loadKayentaAccountsFailure({ error }))),
  );

const rootEpic = combineEpics(
  loadConfigEpic,
  selectConfigEpic,
  saveConfigEpic,
  deleteConfigRequestEpic,
  deleteConfigSuccessEpic,
  loadCanaryRunRequestEpic,
  loadMetricSetPairEpic,
  updateGraphiteMetricDescriptionFilterEpic,
  updatePrometheusMetricDescriptionFilterEpic,
  updateStackdriverMetricDescriptionFilterEpic,
  updateDatadogMetricDescriptionFilterEpic,
  loadMetricsServiceMetadataEpic,
  loadKayentaAccountsEpic,
);

export const epicMiddleware: EpicMiddleware<Action & any, ICanaryState> = createEpicMiddleware(rootEpic);
