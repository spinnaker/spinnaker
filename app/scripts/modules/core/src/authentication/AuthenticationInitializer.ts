import { IHttpPromiseCallbackArg } from 'angular';
import { $http, $location, $rootScope } from 'ngimport';
import { fromEvent as observableFromEvent, Subscription } from 'rxjs';

import { AuthenticationService } from './AuthenticationService';
import { LoggedOutModal } from './LoggedOutModal';
import { SETTINGS } from '../config/settings';
import { ModalInjector } from '../reactShims/modal.injector';

interface IAuthResponse {
  username: string;
  roles?: string[];
}

export class AuthenticationInitializer {
  private static userLoggedOut = false;
  private static visibilityWatch: Subscription = null;

  private static checkForReauthentication(): void {
    $http
      .get(SETTINGS.authEndpoint)
      .then((response: IHttpPromiseCallbackArg<IAuthResponse>) => {
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

  private static loginNotification(): void {
    AuthenticationService.authenticationExpired();
    this.userLoggedOut = true;
    this.openLoggedOutModal();

    this.visibilityWatch = observableFromEvent(document, 'visibilitychange').subscribe(() => {
      if (document.visibilityState === 'visible') {
        this.checkForReauthentication();
      }
    });
  }

  private static openLoggedOutModal(): void {
    LoggedOutModal.show();
  }

  public static loginRedirect(): void {
    const callback: string = encodeURIComponent($location.absUrl());
    window.location.href = `${SETTINGS.gateUrl}/auth/redirect?to=${callback}`;
  }

  public static authenticateUser() {
    $rootScope.authenticating = true;
    $http
      .get(SETTINGS.authEndpoint)
      .then((response: IHttpPromiseCallbackArg<IAuthResponse>) => {
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

  public static reauthenticateUser(): void {
    if (!this.userLoggedOut) {
      this.userLoggedOut = true;
      $http
        .get(SETTINGS.authEndpoint)
        .then((response: IHttpPromiseCallbackArg<IAuthResponse>) => {
          if (response.data.username) {
            AuthenticationService.setAuthenticatedUser({
              name: response.data.username,
              authenticated: false,
              roles: response.data.roles,
            });
            $rootScope.authenticating = false;
            this.userLoggedOut = false;
          } else {
            this.loginNotification();
          }
        })
        .catch(() => this.loginNotification());
    }
  }

  public static logOut(): void {
    if (!this.userLoggedOut) {
      const config = {
        headers: { 'Content-Type': 'text/plain' },
        transformResponse: (response: string) => response,
      };

      $http.get(`${SETTINGS.gateUrl}/auth/logout`, config).then(
        () => this.loggedOutSequence(),
        () => this.loggedOutSequence(),
      );
    }
  }

  private static loggedOutSequence(): void {
    AuthenticationService.authenticationExpired();
    this.userLoggedOut = true;
    this.openLoggedOutModal();
  }
}
