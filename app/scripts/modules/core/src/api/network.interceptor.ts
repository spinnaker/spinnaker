import {
  IDeferred, IHttpInterceptor, IHttpPromiseCallbackArg, IHttpProvider, IPromise, IQService, IRequestConfig,
  IWindowService, module
} from 'angular';

/**
 * Handles two scenarios:
 *   1. computer loses network connection (retries connections when network returns)
 *   2. requests are aborted due to a network change (retries immediately)
 */

export class NetworkInterceptor implements IHttpInterceptor {

  private networkAvailable: IDeferred<void>;
  private retryQueue: IRequestConfig[] = [];

  constructor(private $q: IQService,
              private $window: IWindowService,
              private $injector: any) {
    'ngInject';
    this.$window.addEventListener('offline', this.handleOffline);
    this.$window.addEventListener('online', this.handleOnline);
    this.resetNetworkAvailable();
  }

  private handleOffline(): void {
    this.networkAvailable = this.$q.defer();
  }

  private handleOnline(): void {
    this.networkAvailable.resolve();
    this.resetNetworkAvailable();
  }

  private resetNetworkAvailable(): void {
    this.networkAvailable = this.$q.defer();
    this.networkAvailable.resolve();
  }

  private removeFromQueue(config: IRequestConfig): void {
    this.retryQueue = this.retryQueue.filter(c => c !== config);
  }

  // see http://www.couchcoder.com/angular-1-interceptors-using-typescript for more details on why we need to do this
  // in essence, we need to do this because "the ng1 implementation of interceptors only keeps references to the handler
  // functions themselves and invokes them directly without any context (stateless) which means we lose `this` inside
  // the handlers"
  public responseError = <T>(response: IHttpPromiseCallbackArg<T>): IPromise<T> => {
    const { config, status } = response;
    // status of -1 indicates the request was aborted, retry if we haven't already
    if (status === -1 && !this.retryQueue.includes(config)) {
      return this.networkAvailable.promise.then(() => {
        return this.$q.resolve(this.$injector.get('$http')(config))
          .finally(() => this.removeFromQueue(config));
      });
    }
    return this.$q.reject(response).finally(() => this.removeFromQueue(config));
  }
}

export const NETWORK_INTERCEPTOR = 'spinnaker.core.network.interceptor';
module(NETWORK_INTERCEPTOR, [])
  .service('networkInterceptor', NetworkInterceptor)
  .config(($httpProvider: IHttpProvider) => {
    $httpProvider.interceptors.push('networkInterceptor');
  });
