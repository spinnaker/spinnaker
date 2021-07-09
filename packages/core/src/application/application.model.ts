import { IScope } from 'angular';
import { map, union, uniq } from 'lodash';
import { $log, $q } from 'ngimport';
import { combineLatest as observableCombineLatest, Observable, ReplaySubject, Subject, Subscription } from 'rxjs';
import { map as rxMap } from 'rxjs/operators';

import { ICluster } from '../domain/ICluster';
import { ApplicationDataSource, IDataSourceConfig, IFetchStatus } from './service/applicationDataSource';

export class Application {
  [k: string]: any;

  /**
   * A collection of all available data sources for the given application
   * @type {Array}
   */
  public dataSources: ApplicationDataSource[] = [];

  /**
   * The name of the application
   */
  public get name(): string {
    return this.applicationName;
  }

  /**
   * A list of all accounts currently used within the application. Accounts come from either:
   *  - those explicitly configured on the application (to be removed soon), or
   *  - a dataSource configured with a credentialsField
   * @type {Array}
   */
  public accounts: string[] = [];

  /**
   * A list of all cluster currently used within the application.
   * @type {Array}
   */
  public clusters: ICluster[] = [];

  /**
   * A timestamp indicating the last time the onRefresh method succeeded
   */
  public lastRefresh: number;

  /**
   * A map where the key is the provider and the value is the default credentials value.
   * Default values are determined by querying all data sources with a credentialsField.
   * IFF only one unique value is found, that value is set in the map. Otherwise, the provider is not present in the map
   * @type {Map<string, string>}
   */
  public defaultCredentials: any = {} as any;

  /**
   * A map where the key is the provider and the value is the default region value.
   * Default values are determined by querying all data sources with a regionField.
   * IFF only one unique value is found, that value is set in the map. Otherwise, the provider is not present in the map
   * @type {Map<string, string>}
   */
  public defaultRegions: any = {} as any;

  /**
   * An arbitrary collection of attributes coming from Front50
   * @type {Map<string, string>}
   */
  public attributes: any = {} as any;

  /**
   * Indicates that the application was not found in Front50
   * @type {boolean}
   */
  public notFound = false;

  /**
   * Indicates that there was an exception while trying to load or create
   * the application model.
   * @type {boolean}
   */
  public hasError = false;

  /**
   * Indicates that the application does not exist and is used as a stub
   * @type {boolean}
   */
  public isStandalone = false;

  /**
   * If managed delivery is enabled, indicates whether an entire application is in an explicit paused state
   */
  public isManagementPaused = false;

  /**
   * Which data source is the active state
   * @type {ApplicationDataSource}
   */
  public activeState: ApplicationDataSource = null;

  private refreshListeners: Array<() => void> = [];

  public activeDataSource$ = new ReplaySubject<ApplicationDataSource>(1);
  /** @deprecated use activeDataSource$ */
  public activeStateChangeStream: Subject<any> = new Subject();

  private refreshStream: Subject<any> = new Subject();
  private refreshFailureStream: Subject<any> = new Subject();

  // Stream of fetch status
  public status$: Observable<IFetchStatus>;

  private dataLoader: Subscription;

  constructor(
    private applicationName: string,
    private scheduler: any,
    dataSourceConfigs: Array<IDataSourceConfig<any>>,
  ) {
    dataSourceConfigs.forEach((config) => this.addDataSource(config));
    this.status$ = observableCombineLatest(this.dataSources.map((ds) => ds.status$)).pipe(
      rxMap((statuses: IFetchStatus[]) => this.getDerivedApplicationStatus(statuses)),
    );
  }

  private getDerivedApplicationStatus(statuses: IFetchStatus[]): IFetchStatus {
    const ERROR_STATUS = statuses.some(({ status }) => status === 'ERROR') ? 'ERROR' : undefined;
    const FETCHING_STATUS = statuses.some(({ status }) => status === 'FETCHING') ? 'FETCHING' : undefined;
    const FETCHED_STATUS = statuses.some(({ status }) => status === 'FETCHED') ? 'FETCHED' : undefined;

    const rolledUpStatus: IFetchStatus['status'] =
      ERROR_STATUS || FETCHING_STATUS || FETCHED_STATUS || 'NOT_INITIALIZED';

    const noneLeftToFetch = statuses.every(({ status }) => ['FETCHED', 'NOT_INITIALIZED'].includes(status));
    const lastFetch = statuses.reduce((latest, status) => Math.max(latest, status.lastRefresh || 0), 0);
    const allLoaded = statuses.every(({ loaded }) => loaded);

    return {
      status: rolledUpStatus,
      lastRefresh: noneLeftToFetch ? lastFetch : this.lastRefresh,
      loaded: allLoaded,
      data: undefined,
    };
  }

  private addDataSource<T>(config: IDataSourceConfig<T>) {
    const dataSource = new ApplicationDataSource(config, this);
    this.dataSources.push(dataSource);
    this[config.key] = dataSource;
  }

  /**
   * Returns a data source based on its key. Data sources can be accessed on the application directly via the key,
   * e.g. application.serverGroups, but this is the preferred access method, as it allows type inference
   * @param key
   */
  public getDataSource(key: string): ApplicationDataSource {
    return this.dataSources.find((ds) => ds.key === key);
  }

  /**
   * Refreshes all dataSources for the application
   * @param forceRefresh if true, will trigger a refresh on all data sources, even if the data source is currently
   * loading
   * @returns {PromiseLike<void>} a promise that resolves when the application finishes loading, rejecting with an error if
   * one of the data sources fails to refresh
   */
  public refresh(forceRefresh?: boolean): PromiseLike<any> {
    // refresh hidden data sources but do not consider their results when determining when the refresh completes
    this.dataSources.filter((ds) => !ds.visible).forEach((ds) => ds.refresh(forceRefresh));
    this.refreshListeners.forEach((cb) => cb());
    return $q.all(this.dataSources.filter((ds) => ds.visible).map((source) => source.refresh(forceRefresh))).then(
      () => this.applicationLoadSuccess(),
      (error) => this.applicationLoadError(error),
    );
  }

  /**
   * A promise that resolves immediately if all data sources are ready (i.e. loaded), or once all data sources have
   * loaded
   * @returns {PromiseLike<any>} the return value is a promise, but its value is
   * not useful - it's only useful to watch the promise itself
   */
  public ready(): PromiseLike<any> {
    return $q.all(
      this.dataSources.filter((ds) => ds.onLoad !== undefined && ds.visible).map((dataSource) => dataSource.ready()),
    );
  }

  /**
   * Used to subscribe to the application's refresh cycle. Will automatically be disposed when the $scope is destroyed.
   * @param $scope the $scope that will manage the lifecycle of the subscription
   *        If you pass in null for the $scope, you are responsible for unsubscribing when your component unmounts.
   * @param method the method to call when the refresh completes
   * @param failureMethod a method to call if the refresh fails
   * @return a method to call to unsubscribe
   */
  public onRefresh($scope: IScope, method: any, failureMethod?: any): () => void {
    const success: Subscription = this.refreshStream.subscribe(method);
    let failure: Subscription = null;
    if (failureMethod) {
      failure = this.refreshFailureStream.subscribe(failureMethod);
    }
    const unsubscribe = () => {
      success.unsubscribe();
      if (failure) {
        failure.unsubscribe();
      }
    };
    if ($scope) {
      $scope.$on('$destroy', () => unsubscribe());
    }
    return unsubscribe;
  }

  /**
   * This is really only used by the ApplicationController - it manages the refresh cycle for the overall application
   * and halts refresh when switching applications or navigating to a non-application view
   */
  public enableAutoRefresh(): void {
    this.dataLoader = this.scheduler.subscribe(() => this.refresh());
  }

  public disableAutoRefresh(): void {
    this.dataLoader && this.dataLoader.unsubscribe();
    this.scheduler.unsubscribe();
  }

  public setActiveState(dataSource: ApplicationDataSource = null): void {
    if (this.activeState !== dataSource) {
      this.activeState = dataSource;
      this.activeDataSource$.next(dataSource);
      this.activeStateChangeStream.next(null);
    }
  }

  private applicationLoadError(err: Error): void {
    $log.error(err, 'Failed to load application, will retry on next scheduler execution.');
    this.refreshFailureStream.next(err);
  }

  private applicationLoadSuccess(): void {
    this.setApplicationAccounts();
    this.setDefaults();
    this.lastRefresh = new Date().getTime();
    this.refreshStream.next(null);
  }

  private setApplicationAccounts(): void {
    let accounts = this.accounts.concat(this.attributes.accounts || []);
    this.dataSources
      .filter((ds) => Array.isArray(ds.data) && ds.credentialsField !== undefined)
      .forEach(
        (ds: ApplicationDataSource<any[]>) => (accounts = accounts.concat(ds.data.map((d) => d[ds.credentialsField]))),
      );

    this.accounts = uniq(accounts);
  }

  private extractProviderDefault(field: string): Map<string, string> {
    const results = new Map<string, string>();
    const sources: Array<ApplicationDataSource<any[]>> = this.dataSources.filter(
      (ds) => ds[field] !== undefined && Array.isArray(ds.data) && ds.providerField !== undefined,
    );
    const providers: string[][] = sources
      .map((ds) => ds.data.map((d) => d[ds.providerField]))
      .filter((p) => p.length > 0);
    const allProviders = union(...providers);
    allProviders.forEach((provider: string) => {
      const vals = sources
        .map((ds) =>
          map(
            ds.data.filter((d: any) => typeof d === 'object' && d[ds.providerField] === provider),
            ds[field],
          ),
        )
        .filter((v) => v.length > 0);
      const allValues = union(...vals);
      if (allValues.length === 1) {
        (results as any)[provider] = allValues[0];
      }
    });
    return results;
  }

  private setDefaults(): void {
    this.defaultCredentials = this.extractProviderDefault('credentialsField');
    this.defaultRegions = this.extractProviderDefault('regionField');
  }

  public subscribeToRefresh(onRefreshCb: () => void) {
    this.refreshListeners.push(onRefreshCb);
    const unsubscribeCb = () => {
      this.refreshListeners.filter((cb) => cb !== onRefreshCb);
    };
    return unsubscribeCb;
  }
}
