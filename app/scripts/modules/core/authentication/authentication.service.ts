import {Injectable} from '@angular/core';

import {IDowngradeItem} from 'core/domain/IDowngradeItem';

export interface IUser {
  name: string;
  authenticated: boolean;
  lastAuthenticated?: number;
}

@Injectable()
export class AuthenticationService {

  private user: IUser = {
    name: '[anonymous]',
    authenticated: false
  };

  private authEvents: Function[] = [];

  public getAuthenticatedUser(): IUser {
    return Object.assign({}, this.user);
  }

  public setAuthenticatedUser(authenticatedUser: string): void {
    if (authenticatedUser) {
      this.user.name = authenticatedUser;
      this.user.authenticated = true;
      this.user.lastAuthenticated = new Date().getTime();
    }

    this.authEvents.forEach((event: Function) => event());
  }

  public onAuthentication(event: Function): void {
    this.authEvents.push(event);
  }

  public authenticationExpired(): void {
    this.user.authenticated = false;
  }
}

export const AUTHENTICATION_SERVICE = 'spinnaker.authentication.service';
export const AUTHENTICATION_SERVICE_DOWNGRADE: IDowngradeItem = {
  moduleName: AUTHENTICATION_SERVICE,
  injectionName: 'authenticationService',
  moduleClass: AuthenticationService
};
