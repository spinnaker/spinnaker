import type { IHttpInterceptor, IHttpProvider, ILocationService, ILogService, IRequestConfig } from 'angular';
import { module } from 'angular';

import { JsonUtils } from '../utils';

export class DebugInterceptor implements IHttpInterceptor {
  public static $inject = ['$location', '$log'];

  constructor(private $location: ILocationService, private $log: ILogService) {}

  public request = (config: IRequestConfig): IRequestConfig => {
    try {
      // This is a great opportunity to break Deck, so be careful.
      this.logMutatingRequest(config);
    } catch (e) {
      this.$log.warn('Debug interceptor bug: ', e.message);
    }
    return config;
  };

  private logMutatingRequest(config: IRequestConfig): void {
    const url = this.$location.url();
    if (url && url.includes('debug=true') && ['POST', 'PUT', 'DELETE'].includes(config.method)) {
      this.$log.log(`${config.method}: ${config.url} \n`, JsonUtils.makeSortedStringFromObject(config.data));
    }
  }
}

export const DEBUG_INTERCEPTOR = 'spinnaker.core.debug.interceptor';
module(DEBUG_INTERCEPTOR, [])
  .service('debugInterceptor', DebugInterceptor)
  .config(['$httpProvider', ($httpProvider: IHttpProvider) => $httpProvider.interceptors.push('debugInterceptor')]);
