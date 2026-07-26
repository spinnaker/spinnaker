import type { IHttpPromiseCallbackArg } from 'angular';
import type { Subscription } from 'rxjs';
import { fromEvent as observableFromEvent } from 'rxjs';

import { AuthenticationService } from './AuthenticationService';
import { LoggedOutModal } from './LoggedOutModal';
import { SETTINGS } from '../config/settings';
import { ReactModal } from '../presentation/ReactModal';

interface IAuthResponse {
  username: string;
  roles?: string[];
  canMintApiTokens?: boolean;
  isAdmin?: boolean;
}

export interface IAuthenticationHttpClient {
  get<T>(config: { url: string; headers?: Record<string, string> }): PromiseLike<T>;
}

const fetchAuthenticationHttpClient: IAuthenticationHttpClient = {
  get: async <T>({ url, headers }: { url: string; headers?: Record<string, string> }) => {
    const response = await fetch(url, { credentials: 'include', headers });
    if (!response.ok) {
      throw response;
    }

    const contentType = response.headers.get('content-type') || '';
    return (contentType.includes('json') ? await response.json() : await response.text()) as T;
  },
};

let authenticationHttpClient = fetchAuthenticationHttpClient;

export function setAuthenticationHttpClient(client: IAuthenticationHttpClient = fetchAuthenticationHttpClient): void {
  authenticationHttpClient = client;
}

export class AuthenticationInitializer {
  private static userLoggedOut = false;
  private static visibilityWatch: Subscription = null;

  private static get<T = IAuthResponse>(url: string, config?: any): PromiseLike<IHttpPromiseCallbackArg<T>> {
    return Promise.resolve(
      authenticationHttpClient.get<T>({ url, headers: config?.headers }),
    ).then((data) => (({ data } as unknown) as IHttpPromiseCallbackArg<T>));
  }

  private static checkForReauthentication(visibilityWatch: Subscription): void {
    this.get(SETTINGS.authEndpoint)
      .then((response: IHttpPromiseCallbackArg<IAuthResponse>) => {
        if (this.visibilityWatch === visibilityWatch && response.data.username) {
          AuthenticationService.setAuthenticatedUser({
            name: response.data.username,
            authenticated: false,
            roles: response.data.roles,
            canMintApiTokens: response.data.canMintApiTokens,
            isAdmin: response.data.isAdmin,
          });
          this.userLoggedOut = false;
          this.visibilityWatch?.unsubscribe();
          this.visibilityWatch = null;
          ReactModal.dismissAll('reauthentication');
        }
      })
      .catch(() => {});
  }

  private static loginNotification(): void {
    AuthenticationService.authenticationExpired();
    this.userLoggedOut = true;
    this.openLoggedOutModal();

    this.visibilityWatch?.unsubscribe();
    const visibilityWatch = observableFromEvent(document, 'visibilitychange').subscribe(() => {
      if (document.visibilityState === 'visible') {
        this.checkForReauthentication(visibilityWatch);
      }
    });
    this.visibilityWatch = visibilityWatch;
  }

  private static openLoggedOutModal(): void {
    LoggedOutModal.show();
  }

  public static loginRedirect(): void {
    const callback: string = encodeURIComponent(window.location.href);
    window.location.href = `${SETTINGS.gateUrl}/auth/redirect?to=${callback}`;
  }

  public static authenticateUser(isCurrent: () => boolean = () => true): Promise<boolean> {
    return Promise.resolve(this.get(SETTINGS.authEndpoint))
      .then((response: IHttpPromiseCallbackArg<IAuthResponse>) => {
        if (!response.data.username) {
          throw new Error('Authentication response did not include a username');
        }

        if (!isCurrent()) {
          return false;
        }

        AuthenticationService.setAuthenticatedUser({
          name: response.data.username,
          authenticated: false,
          roles: response.data.roles,
          canMintApiTokens: response.data.canMintApiTokens,
          isAdmin: response.data.isAdmin,
        });
        return true;
      })
      .catch(() => {
        if (isCurrent()) {
          AuthenticationService.reset();
          this.loginRedirect();
        }
        return false;
      });
  }

  public static reauthenticateUser(): void {
    if (!this.userLoggedOut) {
      this.userLoggedOut = true;
      this.get(SETTINGS.authEndpoint)
        .then((response: IHttpPromiseCallbackArg<IAuthResponse>) => {
          if (response.data.username) {
            AuthenticationService.setAuthenticatedUser({
              name: response.data.username,
              authenticated: false,
              roles: response.data.roles,
              canMintApiTokens: response.data.canMintApiTokens,
              isAdmin: response.data.isAdmin,
            });
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

      this.get(`${SETTINGS.gateUrl}/auth/logout`, config).then(
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
