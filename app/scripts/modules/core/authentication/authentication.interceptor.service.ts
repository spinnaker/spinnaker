import {module} from 'angular';
import {AUTHENTICATION_SERVICE, AuthenticationService} from './authentication.service';
import {SETTINGS} from 'core/config/settings';

export class AuthenticationInterceptor implements ng.IHttpInterceptor {

  static get $inject() {
    return ['$q', 'authenticationService'];
  }

  constructor(private $q: ng.IQService,
              private authService: AuthenticationService) {}

  // see http://www.couchcoder.com/angular-1-interceptors-using-typescript for more details on why we need to do this
  // in essense, we need to do this because "the ng1 implementaiton of interceptors only keeps references to the handler
  // functions themselves and invokes them directly without any context (stateless) which means we lose `this` inside
  // the handlers"
  public request = (config: ng.IRequestConfig): ng.IPromise<ng.IRequestConfig> => {

    return this.$q((resolve: ng.IQResolveReject<any>) => {

      // pass through to authentication endpoint and non-http resources
      if (config.url === SETTINGS.authEndpoint || config.url.indexOf('http') !== 0) {
        resolve(config);
      } else {
        const user = this.authService.getAuthenticatedUser();

        // only send the request if the user has authenticated within the refresh window for auth calls
        if (user.authenticated && user.lastAuthenticated + (SETTINGS.authTtl || 600000) > new Date().getTime()) {
          resolve(config);
        } else {
          this.authService.onAuthentication(() => resolve(config));
        }
      }
    });
  }
}

export const AUTHENTICATION_INTERCEPTOR_SERVICE = 'spinnaker.authentication.interceptor.service';
module(AUTHENTICATION_INTERCEPTOR_SERVICE, [AUTHENTICATION_SERVICE])
  .service('authenticationInterceptor', AuthenticationInterceptor)
  .config(($httpProvider: ng.IHttpProvider) => {
    if (SETTINGS.authEnabled) {
      $httpProvider.interceptors.push('authenticationInterceptor');
    }
  });
