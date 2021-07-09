import { IScope } from 'angular';
import { $log, $q } from 'ngimport';
import {
  BehaviorSubject,
  empty as observableEmpty,
  from as observableFrom,
  merge as observableMerge,
  Observable,
  of as observableOf,
  Subject,
} from 'rxjs';
import {
  catchError,
  filter,
  map,
  mergeMap,
  skip,
  startWith,
  switchMap,
  take,
  takeUntil,
  tap,
  withLatestFrom,
} from 'rxjs/operators';

import { Application } from '../application.model';
import { IEntityTags } from '../../domain';
import { IconNames, robotToHuman } from '../../presentation';
import { ReactInjector } from '../../reactShims';
import { FirewallLabels } from '../../securityGroup';
import { toIPromise } from '../../utils';

export interface IFetchStatus {
  status: 'NOT_INITIALIZED' | 'FETCHING' | 'FETCHED' | 'ERROR';
  loaded: boolean;
  error?: any;
  lastRefresh: number;
  data: any;
}

export interface IDataSourceConfig<T> {
  /**
   * (Optional) Used to determine when the application header tab should appear active; the field will be used via
   * $state.includes(activeState).
   *
   * If omitted, the value will default to "**.{key}.**"
   */
  activeState?: string;

  /**
   * (Optional) A method that is called after the "onLoad" method resolves. The data source's data will be populated
   * when this method is called.
   */
  afterLoad?: (application: Application) => void;

  /**
   * (Optional) Enables automatic activate/deactivate of a lazy data source based on the value of 'activeState'
   * and the current state
   *
   * If omitted, the value will default to false
   */
  autoActivate?: boolean;

  /**
   * (Optional) the key of another data source that will display the number of data items contained by that data source,
   * e.g. runningTasks, runningExecutions
   */
  badge?: string;

  /**
   * (Optional) If the data source should contribute to the application's default credentials setting, this field should be set
   * to the field name on each data item.
   */
  credentialsField?: string;

  /**
   * (Optional) a description of the data source that will be displayed on the app config screen
   *
   * Only needed if the data source is optional
   */
  description?: string;

  /**
   * Represents a font-awesome icon to be displayed before the name of the tab
   */
  icon?: string;

  /**
   * Represents the name of the svg to be used with the svg loader (Icon.tsx)
   */
  iconName?: IconNames;

  /**
   * unique value for this data source; the data source will be available on the Application directly via this key,
   * e.g. if the key is "serverGroups", you can access the data source via application.serverGroups
   */
  key: string;

  /**
   * The initial value of the data source
   */
  defaultData: T;

  /**
   * (Optional) The display label of the application header tab
   *
   * If omitted, the value will default to the result of running the "key" through the robotToHuman filter
   */
  label?: string;

  /**
   * (Optional) Determines whether the data source should participate in the application's refresh cycle. If set to true, the
   * data source will not be loaded unless "activate()" has been called on it
   */
  lazy?: boolean;

  /**
   * (Optional) Method used to populate the data source. The method must return a promise; the return value of the
   * promise will be passed to the data source's "onLoad" method.
   *
   * It does *not* automatically populate the "data" field of the data source - that is the responsibility of the
   * "onLoad" method.
   */
  loader?: (application: Application) => PromiseLike<any>;

  /**
   * (Optional) A method that is called when the "loader" method resolves. The method must return a promise. If the "loader"
   * promise resolves with data, the "onLoad" method is responsible for transforming the data, then returning the new
   * value via the promise, which will be assigned to the data source's "data" field.
   *
   * If the onLoad method resolves with a null value, the result will be discarded and the data source's "data" field
   * will remain unchanged.
   */
  onLoad?: (application: Application, result: any) => PromiseLike<T>;

  /**
   * (Optional) whether this data source should be included in the application by default
   *
   * Use this for optional or experimental features that users must explicitly enable.
   *
   * If omitted, the value will default to false
   */
  optIn?: boolean;

  /**
   * (Optional) whether this data source can be disabled
   *
   * If omitted, the value will default to false
   */
  optional?: boolean;

  /**
   * (Optional) whether this data source is visible in the UI (used only for early access/beta testing)
   */
  hidden?: boolean;

  /**
   * (Optional) Determines whether the data source is listed directly to the left of the application name
   */
  primary?: boolean;

  /**
   * (Optional) The application has potentially two default fields for each provider: region and credentials. These fields will
   * only have a value if every data source that contributes values has just one unique value for each provider. Useful
   * for setting initial values in modal dialogs when creating new server groups, load balancers, etc.
   * If the data source should contribute to the application's default region or credentials, this field should be set
   * to the field name that represents the provider on each data item.
   */
  providerField?: string;

  /**
   * (Optional) If the data source should contribute to the application's default region setting, this field should be set
   * to the field name on each data item.
   */
  regionField?: string;

  /**
   * (Optional) whether this data source will be displayed on non-configured applications
   *
   * Use this for features that require additional attributes from the application (e.g. email) to function properly
   *
   * If omitted, the value will default to false
   */
  requireConfiguredApp?: boolean;

  /**
   * (Optional) the sref used to route to the view associated with this data source. This value will be used to populate
   * the link in the application's header.
   *
   * If the "visible" field is set to false, this value is ignored; if "visible" is true and this field is omitted, the
   * data source will not generate any navigation elements
   */
  sref?: string;

  /**
   * (Optional) Determines whether the data source appears on the application header and contributes to the application's ready state.
   *
   * Default: true
   */
  visible?: boolean;

  /**
   * (Optional) a data source that will only be available and visible if this data source (by key) is enabled
   */
  requiresDataSource?: string;

  /**
   * (Optional) Determines which second-level navigation menu this data source will belong to
   */
  category?: string;
}

export class ApplicationDataSource<T = any> implements IDataSourceConfig<T> {
  /** Index Signature */
  [k: string]: any;

  public activeState: string;
  public afterLoad: (application: Application) => void;
  public autoActivate = false;
  public badge: string;
  public credentialsField: string;
  public description: string;
  public icon: string;
  public iconName: IconNames;
  public key: string;
  public label: string;
  public category: string;
  public lazy = false;
  public loader: (application: Application) => PromiseLike<any>;
  public onLoad: (application: Application, result: any) => PromiseLike<T>;
  public optIn = false;
  public optional = false;
  public primary = false;
  public providerField: string;
  public regionField: string;
  public requireConfiguredApp = false;
  public sref: string;
  public visible = true;
  public hidden = false;
  public requiresDataSource: string;

  /**
   * State flag that indicates whether the data source has been loaded. If the data source does not have a declared
   * "loader", this flag will be set to true immediately once the "refresh" method has been called.
   */
  public loaded = false;

  /**
   * State flag that indicates a data source is in the process of refreshing its data
   */
  public loading = false;

  /**
   * Indicates the data source is not used by the application. A disabled data source does not contribute to the
   * application's refresh cycle, nor does it appear in the application header.
   *
   * This flag is set by the ApplicationReader, based on the dataSources attribute, which is stored in Front50.
   */
  public disabled = false;

  /**
   * State flag that indicates an error occurred when the "loader" method rejects
   */
  public loadFailure = false;

  /**
   * State flag that indicates the data source should participate in the application refresh cycle.
   *
   * If the data source is lazy, this field will default to false.
   *
   * To activate a data source, call "activate()" or set "autoActivate = true"
   */
  public active = false;

  /**
   * The initial default value for the data source
   */
  public defaultData: T;

  /**
   * The actual data (if any) for the data source. This field should only be populated by the "loader" method.
   */
  public data: T;

  /**
   * The current fetch status of the data source
   */
  public status: IFetchStatus;

  /**
   * If entity tags are enabled, and any of the data has entity tags with alerts, they will be added to the data source
   * on load, and the alerts will be displayed in the tab next to the tab name.
   */
  public alerts: IEntityTags[];

  /**
   * A timestamp indicating the last time the data source was successfully refreshed
   * @deprecated use the streams
   */
  public lastRefresh: number;

  /** This subject is used to cancel the internal subscription */
  private destroy$: Subject<void>;

  /** This subject is used to trigger a new fetch */
  private fetchRequest$: Subject<void>;

  /**
   * Stream of IFetchStatus.
   * Updated when the status and/or data changes.
   * Starts with NOT_INITIALIZED
   */
  public status$: BehaviorSubject<IFetchStatus>;

  /** BehaviorSubject of data changes, starts by emitting the current value */
  public data$: BehaviorSubject<T>;

  /**
   * Stream of data changes
   * @deprecated use data$.skip(1) instead
   */
  public refresh$: Observable<T>;

  /** Stream of failed IFetchStatus */
  private refreshFailure$: Observable<IFetchStatus>;

  /** Stream that throws fetch failures. */
  private throwFailures$: Observable<never>;

  /** A stream that either emits the next data change, or throws */
  private nextRefresh$: Observable<T>;

  /**
   * A flag to toggle debug messages on. To use, open the JS console and enter:
   * spinnaker.application.<datasource>.debugEnabled = true
   */
  private debugEnabled = false;

  /**
   * Called when a method mutates some item in the data source's data, e.g. when a running execution is updated
   * independent of the execution data source's refresh cycle
   */
  public dataUpdated(data?: T): void {
    if (this.loaded) {
      this.updateData(data !== undefined ? data : this.data);
      const updatedStatus = { data: this.data, ...this.status$.value };
      this.status$.next(updatedStatus);
    }
  }

  /** Applies status and status flags */
  private statusUpdated(fetchStatus: IFetchStatus) {
    const { status } = fetchStatus;

    this.status = fetchStatus;
    this.loaded = status === 'FETCHED' || this.loaded;
    this.loading = status === 'FETCHING';
    this.loadFailure = status === 'ERROR';
    this.lastRefresh = fetchStatus.lastRefresh;

    this.debug(`status: ${fetchStatus.status}`);
  }

  constructor(config: IDataSourceConfig<T>, private application: Application) {
    Object.assign(this, config);

    if (!config.hasOwnProperty('defaultData')) {
      throw new Error(
        'The defaultData field is required when registering a data source.\n\n' +
          'defaultData accepts the initial default value that will be used before data has been fetched.\n' +
          'For example, if your data source holds an array of objects, you should set defaultData to an empty array.',
      );
    }

    this.data = this.data || this.defaultData;
    this.label = FirewallLabels.get(config.label || robotToHuman(config.key));

    if (!config.activeState && this.sref) {
      this.activeState = '**' + this.sref + '.**';
    }

    if (config.autoActivate) {
      ReactInjector.$uiRouter.transitionService.onSuccess({ entering: this.activeState }, () => this.activate());
      ReactInjector.$uiRouter.transitionService.onSuccess({ exiting: this.activeState }, () => this.deactivate());
    }

    // While we can initialize these fields directly on the class to give them private/public
    // status, we have to configure their initial values down here or else things like this.data
    // and this.defaultData will be undefined. This is because member initialization gets transpiled
    // to the *top* of the constructor, so all custom constructor code runs last.
    this.destroy$ = new Subject();
    this.fetchRequest$ = new Subject();

    this.status$ = new BehaviorSubject({
      status: 'NOT_INITIALIZED',
      loaded: this.loaded,
      lastRefresh: 0,
      error: null,
      data: this.data,
    });

    this.data$ = new BehaviorSubject(this.data);

    this.refresh$ = this.data$.pipe(skip(1));
    this.refreshFailure$ = this.status$.pipe(
      skip(1),
      filter(({ status }) => status === 'ERROR'),
    );
    this.throwFailures$ = this.refreshFailure$.pipe(
      map(({ error }) => {
        throw error;
      }),
    );
    this.nextRefresh$ = observableMerge(this.data$.pipe(skip(1)), this.throwFailures$).pipe(take(1));

    const fetchStream$ = this.fetchRequest$.pipe(
      tap(() => this.debug('fetch requested...')),
      switchMap(() => {
        return observableFrom(this.loader(this.application)).pipe(
          mergeMap((data) => this.onLoad(this.application, data)),
          map((data) => ({ status: 'FETCHED', lastRefresh: Date.now(), data })),
          catchError((error) =>
            observableOf({ status: 'ERROR', lastRefresh: this.lastRefresh, data: this.data, error }),
          ),
          startWith({ status: 'FETCHING', lastRefresh: this.lastRefresh, data: this.data }),
        );
      }),
      startWith(this.status$.value),
      takeUntil(this.destroy$),
    ) as Observable<IFetchStatus>;

    // Some data sources expect other data sources to exist on the application
    // Wait one tick before processing the stream so all data sources are registered
    const nextTick$ = observableFrom($q.resolve());

    fetchStream$.pipe(withLatestFrom(nextTick$)).subscribe(([fetchStatus, _void]) => {
      // Update mutable flags
      this.statusUpdated(fetchStatus);
      fetchStatus.loaded = this.loaded;

      if (fetchStatus.status === 'FETCHED') {
        this.updateData(fetchStatus.data);
        this.afterLoad && this.afterLoad(this.application);
        this.addAlerts();
      }

      this.status$.next(fetchStatus);
    });
  }

  public destroy() {
    this.destroy$.next();
  }

  /**
   * A method that allows another method to be called the next time the data source refreshes
   *
   * @param $scope the controller scope of the calling method. If the $scope is destroyed, the subscription is disposed.
   *        If you pass in null for the $scope, you are responsible for unsubscribing when your component unmounts.
   * @param callback the method to call the next time the data source refreshes
   * @param onError (optional) a method to call if the data source refresh fails
   * @return a method to call to unsubscribe
   */
  public onNextRefresh($scope: IScope, callback: (data?: any) => void, onError?: (err?: any) => void): () => void {
    const subscription = this.nextRefresh$.subscribe(
      (data) => callback(data),
      (error) => onError && onError(error),
    );

    $scope && $scope.$on('$destroy', () => subscription.unsubscribe());
    return () => subscription.unsubscribe();
  }

  /**
   * A method that allows another method to be called the whenever the data source refreshes. The subscription will be
   * automatically disposed when the $scope is destroyed.
   *
   * @param $scope the controller scope of the calling method. If the $scope is destroyed, the subscription is disposed.
   *        If you pass in null for the $scope, you are responsible for unsubscribing when your component unmounts.
   * @param callback the method to call the next time the data source refreshes
   * @param onError (optional) a method to call if the data source refresh fails
   * @return a method to call to unsubscribe
   */
  public onRefresh($scope: IScope, callback: (data?: any) => void, onError?: (err?: any) => void): () => void {
    const failures$ = this.refreshFailure$.pipe(
      mergeMap(({ error }) => {
        onError && onError(error);
        return observableEmpty();
      }),
    );

    const subscription = observableMerge(this.data$.pipe(skip(1)), failures$).subscribe((data) => callback(data));

    $scope && $scope.$on('$destroy', () => subscription.unsubscribe());
    return () => subscription.unsubscribe();
  }

  /**
   * Returns a promise that resolves immediately if:
   *  - the data source is disabled
   *  - the data source has successfully loaded
   *  - the data source is lazy and not active
   *
   * Otherwise, will resolve as soon as the data source successfully loads its data
   *
   * The promise will reject if the data source has failed to load, or fails to load the next time it tries to load
   *
   * @returns {PromiseLike<T>}
   */
  public ready(): PromiseLike<T> {
    if (this.disabled || this.loaded || (this.lazy && !this.active)) {
      return $q.resolve(this.data);
    }

    return toIPromise(this.nextRefresh$);
  }

  /**
   * Sets the "active" flag to true and, if the data source has not been loaded, immediately calls "refresh()"
   */
  public activate(): void {
    if (!this.active) {
      this.active = true;
      if (!this.loaded) {
        this.refresh();
      }
    }
  }

  /**
   * Sets the data source's "active" flag to false, preventing it from participating in the application refresh cycle
   */
  public deactivate(): void {
    this.active = false;
  }

  private updateData(data: T) {
    this.data = data || this.defaultData;
    this.data$.next(this.data);
    this.debug(`this.data:`, this.data);
  }

  /**
   * Loads (or reloads, if already loaded) the data source, returning a promise that resolves once the data has been
   * loaded. The promise does not resolve with any data - that will be set on the data source itself.
   *
   * If the data source does not include a loader or its "disabled" flag is set to true, the "loaded" flag will be set
   * to true, and the promise will resolve immediately.
   *
   * If the data source is lazy and its "active" flag is set to false, any existing data will be cleared, the "loaded"
   * flag will be set to false, and the promise will resolve immediately.
   *
   * If the data source is in the process of loading, the promise will resolve immediately. This behavior can be
   * overridden by calling "refresh(true)".
   *
   * @param forceRefresh
   * @returns {any}
   */
  public refresh(forceRefresh?: boolean): PromiseLike<any> {
    this.debug(`refresh(${forceRefresh})`);
    if (!this.loader || this.disabled || (this.lazy && !this.active)) {
      this.loaded = false;
      this.updateData(this.defaultData);
      return $q.resolve(this.data);
    }

    const promise = toIPromise(this.data$.pipe(skip(1), take(1)));

    if (this.loading && !forceRefresh) {
      $log.info(`${this.key} still loading, skipping refresh`);
    } else {
      this.fetchRequest$.next();
    }

    return promise;
  }

  private addAlerts(): void {
    this.alerts = [];
    if (Array.isArray(this.data) && this.data.length) {
      this.alerts = this.data
        .filter((d: any) => d.entityTags?.alerts?.length ?? 0)
        .map((d: any) => d['entityTags'] as IEntityTags);
    }
  }

  private debug(message: string, object?: any) {
    if (this.debugEnabled) {
      // tslint:disable-next-line
      console.log(`DEBUG ${this.application.name}.${this.key}: ${message}`, object); // eslint-disable-line
    }
  }
}
