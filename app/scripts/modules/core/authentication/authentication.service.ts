import {module} from 'angular';

export interface IUser {
  name: string;
  authenticated: boolean;
  roles?: string[];
  lastAuthenticated?: number;
}

export class AuthenticationService {

  private user: IUser = {
    name: '[anonymous]',
    roles: [],
    authenticated: false
  };

  private authEvents: Function[] = [];

  public getAuthenticatedUser(): IUser {
    return Object.assign({}, this.user);
  }

  public setAuthenticatedUser(authenticatedUser: IUser): void {
    if (authenticatedUser && authenticatedUser.name) {
      this.user.name = authenticatedUser.name;
      this.user.authenticated = true;
      this.user.lastAuthenticated = new Date().getTime();
      this.user.roles = authenticatedUser.roles;
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
module(AUTHENTICATION_SERVICE, [])
  .service('authenticationService', AuthenticationService);
