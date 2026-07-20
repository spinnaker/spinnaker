import type { IHttpInterceptor, IHttpPromiseCallbackArg, IHttpProvider, IHttpService, IQService } from 'angular';
import { module } from 'angular';

import { SETTINGS } from '../config/settings';

export class IapInterceptor implements IHttpInterceptor {
  public static $inject = ['$injector', '$q'];
  public constructor(private $injector: angular.auto.IInjectorService, private $q: IQService) {}

  public responseError = <T>(response: IHttpPromiseCallbackArg<T>): PromiseLike<T> => {
    if (response.status === 401 && SETTINGS.feature.iapRefresherEnabled) {
      const $http = this.$injector.get('$http') as IHttpService;
      return $http.get('/_gcp_iap/do_session_refresh').then(
        () => $http(response.config).then((result: any) => result),
        () => this.$q.reject(response),
      );
    }

    return this.$q.reject(response);
  };
}

export const IAP_INTERCEPTOR = 'spinnaker.core.iap.interceptor';
module(IAP_INTERCEPTOR, [])
  .service('iapInterceptor', IapInterceptor)
  .config([
    '$httpProvider',
    ($httpProvider: IHttpProvider) => {
      $httpProvider.interceptors.push('iapInterceptor');
    },
  ]);
