import { IHttpInterceptor, IHttpProvider, IRequestConfig, module } from 'angular';
import { $location, $log } from 'ngimport';

import { JsonUtils } from '../utils';

export class DebugInterceptor implements IHttpInterceptor {
  public request = (config: IRequestConfig): IRequestConfig => {
    try {
      // This is a great opportunity to break Deck, so be careful.
      this.logMutatingRequest(config);
    } catch (e) {
      $log.warn('Debug interceptor bug: ', e.message);
    }
    return config;
  };

  private logMutatingRequest(config: IRequestConfig): void {
    if (
      $location.url() &&
      $location.url().includes('debug=true') &&
      ['POST', 'PUT', 'DELETE'].includes(config.method)
    ) {
      $log.log(`${config.method}: ${config.url} \n`, JsonUtils.makeSortedStringFromObject(config.data));
    }
  }
}

export const DEBUG_INTERCEPTOR = 'spinnaker.core.debug.interceptor';
module(DEBUG_INTERCEPTOR, [])
  .service('debugInterceptor', DebugInterceptor)
  .config(['$httpProvider', ($httpProvider: IHttpProvider) => $httpProvider.interceptors.push('debugInterceptor')]);
