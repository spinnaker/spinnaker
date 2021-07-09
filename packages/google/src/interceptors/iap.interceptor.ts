import { IHttpInterceptor, IHttpPromiseCallbackArg, IHttpProvider, module } from 'angular';
import { $http, $q } from 'ngimport';
import { SETTINGS } from '@spinnaker/core';

/**
 * When a Cloud IAP (if enabled) session expires, refreshes session.
 */

export class IapInterceptor implements IHttpInterceptor {
  public responseError = <T>(response: IHttpPromiseCallbackArg<T>): PromiseLike<T> => {
    const { config, status } = response;

    if (status === 401 && SETTINGS.feature.iapRefresherEnabled) {
      return $http.get('/_gcp_iap/do_session_refresh').then(
        () => {
          return $http.get(config.url, config).then((result: T) => result);
        },
        // Reject with original response
        () => $q.reject(response),
      );
    }

    return $q.reject(response);
  };
}

export const IAP_INTERCEPTOR = 'spinnaker.gce.iap.interceptor';
module(IAP_INTERCEPTOR, [])
  .service('iapInterceptor', IapInterceptor)
  .config([
    '$httpProvider',
    ($httpProvider: IHttpProvider) => {
      $httpProvider.interceptors.push('iapInterceptor');
    },
  ]);
