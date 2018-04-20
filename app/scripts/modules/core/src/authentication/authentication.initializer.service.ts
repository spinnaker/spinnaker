import { module } from 'angular';
import { Observable, Subscription } from 'rxjs';
import { $location, $rootScope, $http } from 'ngimport';

import { LoggedOutModal } from 'core/authentication/LoggedOutModal';
import { ModalInjector } from 'core/reactShims/modal.injector';
import { SETTINGS } from 'core/config/settings';

import { AuthenticationService } from './AuthenticationService';

interface IAuthResponse {
  username: string;
  roles?: string[];
}

export class AuthenticationInitializer {
  private userLoggedOut = false;
  private visibilityWatch: Subscription = null;

  private checkForReauthentication(): void {
    $http
      .get(SETTINGS.authEndpoint)
      .then((response: ng.IHttpPromiseCallbackArg<IAuthResponse>) => {
        if (response.data.username) {
          AuthenticationService.setAuthenticatedUser({
            name: response.data.username,
            authenticated: false,
            roles: response.data.roles,
          });
          ModalInjector.modalStackService.dismissAll();
          this.visibilityWatch.unsubscribe();
        }
      })
      .catch(() => {});
  }

  private loginNotification(): void {
    AuthenticationService.authenticationExpired();
    this.userLoggedOut = true;
    this.openLoggedOutModal();

    this.visibilityWatch = Observable.fromEvent(document, 'visibilitychange').subscribe(() => {
      if (document.visibilityState === 'visible') {
        this.checkForReauthentication();
      }
    });
  }

  private openLoggedOutModal(): void {
    LoggedOutModal.show();
  }

  private loginRedirect(): void {
    const callback: string = encodeURIComponent($location.absUrl());
    window.location.href = `${SETTINGS.gateUrl}/auth/redirect?to=${callback}`;
  }

  public authenticateUser() {
    $rootScope.authenticating = true;
    $http
      .get(SETTINGS.authEndpoint)
      .then((response: ng.IHttpPromiseCallbackArg<IAuthResponse>) => {
        if (response.data.username) {
          AuthenticationService.setAuthenticatedUser({
            name: response.data.username,
            authenticated: false,
            roles: response.data.roles,
          });
          $rootScope.authenticating = false;
        } else {
          this.loginRedirect();
        }
      })
      .catch(() => this.loginRedirect());
  }

  public reauthenticateUser(): void {
    if (!this.userLoggedOut) {
      $http
        .get(SETTINGS.authEndpoint)
        .then((response: ng.IHttpPromiseCallbackArg<IAuthResponse>) => {
          if (response.data.username) {
            AuthenticationService.setAuthenticatedUser({
              name: response.data.username,
              authenticated: false,
              roles: response.data.roles,
            });
            $rootScope.authenticating = false;
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
        headers: { 'Content-Type': 'text/plain' },
        transformResponse: (response: string) => response,
      };

      $http
        .get(`${SETTINGS.gateUrl}/auth/logout`, config)
        .then(() => this.loggedOutSequence(), () => this.loggedOutSequence());
    }
  }

  private loggedOutSequence(): void {
    AuthenticationService.authenticationExpired();
    this.userLoggedOut = true;
    this.openLoggedOutModal();
  }
}

export const AUTHENTICATION_INITIALIZER_SERVICE = 'spinnaker.authentication.initializer.service';
module(AUTHENTICATION_INITIALIZER_SERVICE, []).service('authenticationInitializer', AuthenticationInitializer);
