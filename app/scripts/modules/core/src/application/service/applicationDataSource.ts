import {get} from 'lodash';
import {ILogService, IPromise, IQService, IScope} from 'angular';
import {Subject, Subscription} from 'rxjs';

import {Application} from '../application.model';
import {IEntityTags} from 'core/domain/IEntityTags';

export class DataSourceConfig {

  /**
   * unique value for this data source; the data source will be available on the Application directly via this key,
   * e.g. if the key is "serverGroups", you can access the data source via application.serverGroups
   */
  public key: string;

  /**
   * (Optional) the sref used to route to the view associated with this data source. This value will be used to populate
   * the link in the application's header.
   *
   * If the "visible" field is set to false, this value is ignored; if "visible" is true and this field is omitted, the
   * tab will use ".insight.{key}" as the sref value in the application header tab
   */
  public sref: string;

  /**
   * (Optional) whether this data source should be included in the application by default
   *
   * Use this for optional or experimental features that users must explicitly enable.
   *
   * If omitted, the value will default to false
   */
  public optIn = false;

  /**
   * (Optional) whether this data source can be disabled
   *
   * If omitted, the value will default to false
   */
  public optional = false;

  /**
   * (Optional) a description of the data source that will be displayed on the app config screen
   *
   * Only needed if the data source is optional
   */
  public description: string;

  /**
   * (Optional) The display label of the application header tab
   *
   * If omitted, the value will default to the result of running the "key" through the robotToHuman filter
   */
  public label: string;

  /**
   * (Optional) the key of another data source that will display the number of data items contained by that data source,
   * e.g. runningTasks, runningExecutions
   */
  public badge: string;

  /**
   * (Optional) Used to determine when the application header tab should appear active; the field will be used via
   * $state.includes(activeState).
   *
   * If omitted, the value will default to "**.{key}.**"
   */
  public activeState: string;

  /**
   * Determines whether the data source appears on the application header and contributes to the application's ready state.
   *
   * Default: true
   */
  public visible = true;

  /**
   * Determines whether the data source should participate in the application's refresh cycle. If set to true, the
   * data source will not be loaded unless "activate()" has been called on it
   */
  public lazy = false;

  /**
   * Determines whether the data source is listed directly to the left of the application name
   */
  public primary = false;

  /**
   * Represents a font-awesome icon to be displayed before the name of the tab
   */
  public icon: string;

  /**
   * (Optional) Method used to populate the data source. The method must return a promise; the return value of the
   * promise will be passed to the data source's "onLoad" method.
   *
   * It does *not* automatically populate the "data" field of the data source - that is the responsibility of the
   * "onLoad" method.
   */
  public loader: {(fn: any): IPromise<any>};

  /**
   * A method that is called when the "loader" method resolves. The method must return a promise. If the "loader"
   * promise resolves with data, the "onLoad" method is responsible for transforming the data, then returning the new
   * value via the promise, which will be assigned to the data source's "data" field.
   *
   * If the onLoad method resolves with a null value, the result will be discarded and the data source's "data" field
   * will remain unchanged.
   */
  public onLoad: {(fn: any): IPromise<any>};

  /**
   * (Optional) A method that is called after the "onLoad" method resolves. The data source's data will be populated
   * when this method is called.
   */
  public afterLoad: {(application: Application): void};

  /**
   * If the data source should contribute to the application's default credentials setting, this field should be set
   * to the field name on each data item.
   */
  public credentialsField: string;

  /**
   * If the data source should contribute to the application's default region setting, this field should be set
   * to the field name on each data item.
   */
  public regionField: string;

  /**
   * The application has potentially two default fields for each provider: region and credentials. These fields will
   * only have a value if every data source that contributes values has just one unique value for each provider. Useful
   * for setting initial values in modal dialogs when creating new server groups, load balancers, etc.

   * If the data source should contribute to the application's default region or credentials, this field should be set
   * to the field name that represents the provider on each data item.
   */
  public providerField: string;

  constructor(config: any) {
    Object.assign(this, config);
  }
}

export class ApplicationDataSource {

  /**
   * Index Signature
   */
  [k: string]: any;

  /**
   * See DataSourceConfig#key
   */
  public key: string;

  /**
   * See DataSourceConfig#sref
   */
  public sref: string;

  /**
   * See DataSourceConfig#label
   */
  public label: string;

  /**
   * See DataSourceConfig#badge
   */
  public badge: string;

  /**
   * See DataSourceConfig#activeState
   */
  public activeState: string;

  /**
   * See DataSourceConfig#visible
   */
  public visible = true;

  /**
   * See DataSourceConfig#lazy
   */
  public lazy = false;

  /**
   * See DataSourceConfig#optional
   */
  public optional = false;

  /**
   * See DataSourceConfig#icon
   */
  public icon: string;

  /**
   * See DataSourceConfig#primary
   */
  public primary = false;

  /**
   * See DataSourceConfig#description
   */
  public description: string;

  /**
   * See DataSourceConfig#optIn
   */
  public optIn = false;

  /**
   * See DataSourceConfig#credentialsField
   */
  public credentialsField: string;

  /**
   * See DataSourceConfig#regionField
   */
  public regionField: string;

  /**
   * See DataSourceConfig#providerField
   */
  public providerField: string;

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
   * This flag is set by the applicationReader, based on the dataSources attribute, which is stored in Front50.
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
   * To activate a data source, call "activate()"
   */
  public active = false;

  /**
   * The actual data (if any) for the data source. This field should only be populated by the "loader" method.
   */
  public data: any[] = [];

  /**
   * If entity tags are enabled, and any of the data has entity tags with alerts, they will be added to the data source
   * on load, and the alerts will be displayed in the tab next to the tab name.
   */
  public alerts: IEntityTags[];


  /**
   * A timestamp indicating the last time the data source was successfully refreshed
   */
  public lastRefresh: number;

  /**
   * See DataSourceConfig#onLoad
   */
  public onLoad: {(application: Application, fn: any): IPromise<any>};

  /**
   * See DataSourceConfig#afterLoad
   */
  public afterLoad: {(application: Application): void};

  /**
   * See DataSourceConfig#loader
   */
  public loader: {(fn: any): IPromise<any>};

  private refreshStream: Subject<any> = new Subject();

  private refreshFailureStream: Subject<any> = new Subject();

  /**
   * Called when a method mutates some item in the data source's data, e.g. when a running execution is updated
   * independent of the execution data source's refresh cycle
   */
  public dataUpdated(): void {
    if (this.loaded) {
      this.refreshStream.next(null);
    }
  }

  constructor(config: DataSourceConfig, private application: Application, private $q?: IQService, private $log?: ILogService, private $filter?: any) {
    Object.assign(this, config);
    if (!config.sref && config.visible !== false) {
      this.sref = '.insight.' + config.key;
    }

    if (!config.label && this.$filter) {
      this.label = this.$filter('robotToHuman')(config.key);
    }

    if (!config.activeState) {
      this.activeState = '**' + this.sref + '.**';
    }
  }

  /**
   * A method that allows another method to be called the next time the data source refreshes
   *
   * @param $scope the controller scope of the calling method. If the $scope is destroyed, the subscription is disposed.
   *        If you pass in null for the $scope, you are responsible for unsubscribing when your component unmounts.
   * @param method the method to call the next time the data source refreshes
   * @param failureMethod (optional) a method to call if the data source refresh fails
   * @return a method to call to unsubscribe
   */
  public onNextRefresh($scope: IScope, method: any, failureMethod?: any): () => void {
    const success: Subscription = this.refreshStream.take(1).subscribe(method);
    let failure: Subscription = null;
    if (failureMethod) {
      failure = this.refreshFailureStream.take(1).subscribe(failureMethod);
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
   * A method that allows another method to be called the whenever the data source refreshes. The subscription will be
   * automatically disposed when the $scope is destroyed.
   *
   * @param $scope the controller scope of the calling method. If the $scope is destroyed, the subscription is disposed.
   *        If you pass in null for the $scope, you are responsible for unsubscribing when your component unmounts.
   * @param method the method to call the next time the data source refreshes
   * @param failureMethod (optional) a method to call if the data source refresh fails
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
   * Returns a promise that resolves immediately if:
   *  - the data source is disabled
   *  - the data source has successfully loaded
   *  - the data source is lazy and not active
   *
   * Otherwise, will resolve as soon as the data source successfully loads its data
   *
   * The promise will reject if the data source has failed to load, or fails to load the next time it tries to load
   *
   * @returns {IPromise<T>}
   */
  public ready(): IPromise<any> {
    const deferred = this.$q.defer();
    if (this.disabled || this.loaded || (this.lazy && !this.active)) {
      deferred.resolve();
    } else if (this.loadFailure) {
      deferred.reject();
    } else {
      this.refreshStream.take(1).subscribe(deferred.resolve);
      this.refreshFailureStream.take(1).subscribe(deferred.reject);
    }
    return deferred.promise;
  }

  /**
   * Sets the data source's "active" flag to false, preventing it from participating in the application refresh cycle
   */
  public deactivate(): void {
    this.active = false;
  }

  /**
   * Loads (or reloads, if already loaded) the data source, returning a promise that resolves once the data has been
   * loaded. The promise does not resolve with any data - that will be set on the data source itself.
   *
   * If the data source does not include a loader or its "disabled" flag is set to true, the "loaded" flag will be set
   * to true, and the promise will resolve immediately.
   *
   * If the data source is lazy and its "active" flag is set to false, any existing data will be cleared, the "loaded"
   * flag will be set to false, adn the promise will resolve immediately.
   *
   * If the data source is in the process of loading, the promise will resolve immediately. This behavior can be
   * overridden by calling "refresh(true)".
   *
   * @param forceRefresh
   * @returns {any}
   */
  public refresh(forceRefresh?: boolean): IPromise<any> {
    if (!this.loader || this.disabled) {
      this.data.length = 0;
      this.loading = false;
      this.loaded = true;
      return this.$q.when(null);
    }
    if (this.lazy && !this.active) {
      this.data.length = 0;
      this.loaded = false;
      return this.$q.when(null);
    }
    if (this.loading && !forceRefresh) {
      this.$log.info(`${this.key} still loading, skipping refresh`);
      return this.$q.when(null);
    }
    this.loading = true;
    return this.loader(this.application)
      .then((result) => {
        this.onLoad(this.application, result).then(data => {
          if (data) {
            this.data = data;
          }
          this.loaded = true;
          this.loading = false;
          this.loadFailure = false;
          this.lastRefresh = new Date().getTime();
          if (this.afterLoad) {
            this.afterLoad(this.application);
          }
          this.addAlerts();
          this.dataUpdated();
        });
      })
      .catch((rejection) => {
        this.$log.warn(`Error retrieving ${this.key}`, rejection);
        this.loading = false;
        this.loadFailure = true;
        this.refreshFailureStream.next(rejection);
      });
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

  private addAlerts(): void {
    this.alerts = [];
    if (this.data && this.data.length) {
      this.alerts = this.data
        .filter((d: any) => get(d, 'entityTags.alerts.length', 0))
        .map((d: any) => d['entityTags'] as IEntityTags);
    }
  }
}
