import { module } from 'angular';
import { IModalService, IModalStackService } from 'angular-ui-bootstrap';
import { Observable, Subscription } from 'rxjs';

import { SETTINGS } from 'core/config/settings';
import { IDeckRootScope } from 'core/domain';

import { AUTHENTICATION_SERVICE, AuthenticationService } from './authentication.service';
import { REDIRECT_SERVICE, RedirectService } from './redirect.service';

interface IAuthResponse {
  username: string;
  roles?: string[];
}

export class AuthenticationInitializer {

  private userLoggedOut = false;
  private visibilityWatch: Subscription = null;

  constructor(private $location: ng.ILocationService,
              private $rootScope: IDeckRootScope,
              private $http: ng.IHttpService,
              private $uibModal: IModalService,
              private $uibModalStack: IModalStackService,
              private redirectService: RedirectService,
              private authenticationService: AuthenticationService) {
    'ngInject';
  }

  private checkForReauthentication(): void {
    this.$http.get(SETTINGS.authEndpoint)
      .then((response: ng.IHttpPromiseCallbackArg<IAuthResponse>) => {
        if (response.data.username) {
          this.authenticationService.setAuthenticatedUser({
            name: response.data.username,
            authenticated: false,
            roles: response.data.roles
          });
          this.$uibModalStack.dismissAll();
          this.visibilityWatch.unsubscribe();
        }
      });
  }

  private loginNotification(): void {
    this.authenticationService.authenticationExpired();
    this.userLoggedOut = true;
    this.openLoggedOutModal();

    this.visibilityWatch = Observable.fromEvent(document, 'visibilitychange')
      .subscribe(() => {
        if (document.visibilityState === 'visible') {
          this.checkForReauthentication();
        }
      });
  }

  private openLoggedOutModal(): void {
    this.$uibModal.open({
      templateUrl: require('./loggedOut.modal.html'),
      controller: 'LoggedOutModalCtrl as ctrl',
      size: 'squared'
    });
  }

  private loginRedirect(): void {
    const callback: string = encodeURIComponent(this.$location.absUrl());
    this.redirectService.redirect(`${SETTINGS.gateUrl}/auth/redirect?to=${callback}`);
  }

  public authenticateUser() {
    this.$rootScope.authenticating = true;
    this.$http.get(SETTINGS.authEndpoint)
      .then((response: ng.IHttpPromiseCallbackArg<IAuthResponse>) => {
        if (response.data.username) {
          this.authenticationService.setAuthenticatedUser({
            name: response.data.username,
            authenticated: false,
            roles: response.data.roles
          });
          this.$rootScope.authenticating = false;
        } else {
          this.loginRedirect();
        }
      })
      .catch(() => this.loginRedirect());
  }

  public reauthenticateUser(): void {
    if (!this.userLoggedOut) {
      this.$http.get(SETTINGS.authEndpoint)
        .then((response: ng.IHttpPromiseCallbackArg<IAuthResponse>) => {
          if (response.data.username) {
            this.authenticationService.setAuthenticatedUser({
              name: response.data.username,
              authenticated: false,
              roles: response.data.roles
            });
            this.$rootScope.authenticating = false;
          } else {
            this.loginNotification();
          }
        })
        .catch(() => this.loginNotification());
    }
  }

  public logOut(): void {
    if (!this.userLoggedOut) {
      const config = {
        headers: {'Content-Type': 'text/plain'},
        transformResponse: (response: string) => response,
      };

      this.$http.get(`${SETTINGS.gateUrl}/auth/logout`, config)
        .then(() => this.loggedOutSequence(), () => this.loggedOutSequence());
    }
  }

  private loggedOutSequence(): void {
    this.authenticationService.authenticationExpired();
    this.userLoggedOut = true;
    this.openLoggedOutModal();
  }
}

export const AUTHENTICATION_INITIALIZER_SERVICE = 'spinnaker.authentication.initializer.service';
module(AUTHENTICATION_INITIALIZER_SERVICE, [
  require('angular-ui-bootstrap'),
  REDIRECT_SERVICE,
  AUTHENTICATION_SERVICE,
  require('./loggedOut.modal.controller')
])
  .service('authenticationInitializer', AuthenticationInitializer);
