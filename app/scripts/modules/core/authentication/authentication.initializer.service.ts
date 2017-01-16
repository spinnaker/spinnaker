import {Observable} from 'rxjs/Observable';
import {Subscription} from 'rxjs/Subscription';
import {module} from 'angular';
import {IModalService, IModalStackService} from 'angular-ui-bootstrap';

import {IDeckRootScope} from '../domain/deckRootScope';
import {REDIRECT_SERVICE} from './redirect.service';
import {AUTHENTICATION_SERVICE, AuthenticationService} from './authentication.service';

interface IAuthResponse {
  username: string;
}

export class AuthenticationInitializer {

  static get $inject(): string[] {
    return [
      '$location', '$rootScope', '$http', '$uibModal', '$uibModalStack',
      'settings', 'redirectService', 'authenticationService'
    ];
  }

  private userLoggedOut = false;
  private visibilityWatch: Subscription = null;

  constructor(private $location: ng.ILocationService,
              private $rootScope: IDeckRootScope,
              private $http: ng.IHttpService,
              private $uibModal: IModalService,
              private $uibModalStack: IModalStackService,
              private settings: any,
              private redirectService: any,
              private authenticationService: AuthenticationService) {
  }

  private checkForReauthentication(): void {
    this.$http.get(this.settings.authEndpoint)
      .then((response: ng.IHttpPromiseCallbackArg<IAuthResponse>) => {
        if (response.data.username) {
          this.authenticationService.setAuthenticatedUser(response.data.username);
          this.$uibModalStack.dismissAll();
          this.visibilityWatch.unsubscribe();
        }
      });
  }

  private loginNotification(): void {
    this.authenticationService.authenticationExpired();
    this.userLoggedOut = true;
    this.$uibModal.open({
      templateUrl: require('./loggedOut.modal.html'),
      controller: 'LoggedOutModalCtrl as ctrl',
      size: 'squared'
    });

    this.visibilityWatch = Observable.fromEvent(document, 'visibilitychange')
      .subscribe(() => {
        if (document.visibilityState === 'visible') {
          this.checkForReauthentication();
        }
      });
  }

  private loginRedirect(): void {
    const callback: string = encodeURIComponent(this.$location.absUrl());
    this.redirectService.redirect(`${this.settings.gateUrl}/auth/redirect?to=${callback}`);
  }

  public authenticateUser() {
    this.$rootScope.authenticating = true;
    this.$http.get(this.settings.authEndpoint)
      .then((response: ng.IHttpPromiseCallbackArg<IAuthResponse>) => {
        if (response.data.username) {
          this.authenticationService.setAuthenticatedUser(response.data.username);
          this.$rootScope.authenticating = false;
        } else {
          this.loginRedirect();
        }
      })
      .catch(() => this.loginRedirect());
  }

  public reauthenticateUser(): void {
    if (!this.userLoggedOut) {
      this.$http.get(this.settings.authEndpoint)
        .then((response: ng.IHttpPromiseCallbackArg<IAuthResponse>) => {
          if (response.data.username) {
            this.authenticationService.setAuthenticatedUser(response.data.username);
            this.$rootScope.authenticating = false;
          } else {
            this.loginNotification();
          }
        })
        .catch(() => this.loginNotification());
    }
  }
}

export const AUTHENTICATION_INITIALIZER_SERVICE = 'spinnaker.authentication.initializer.service';
module(AUTHENTICATION_INITIALIZER_SERVICE, [
  require('angular-ui-bootstrap'),
  require('../config/settings'),
  REDIRECT_SERVICE,
  AUTHENTICATION_SERVICE,
  require('./loggedOut.modal.controller')
])
  .service('authenticationInitializer', AuthenticationInitializer);
