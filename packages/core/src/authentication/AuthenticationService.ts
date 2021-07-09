export interface IUser {
  name: string;
  authenticated: boolean;
  roles?: string[];
  lastAuthenticated?: number;
}

const defaultUser: IUser = {
  name: '[anonymous]',
  roles: [],
  authenticated: false,
};

export class AuthenticationService {
  private static user: IUser = { ...defaultUser };

  private static authEvents: Function[] = [];

  public static getAuthenticatedUser(): IUser {
    return { ...this.user };
  }

  public static setAuthenticatedUser(authenticatedUser: IUser): void {
    if (authenticatedUser && authenticatedUser.name) {
      this.user.name = authenticatedUser.name;
      this.user.authenticated = true;
      this.user.lastAuthenticated = new Date().getTime();
      this.user.roles = authenticatedUser.roles;
    }

    this.authEvents.forEach((event: Function) => event());
  }

  public static onAuthentication(event: Function): void {
    this.authEvents.push(event);
  }

  public static authenticationExpired(): void {
    this.user.authenticated = false;
  }

  public static reset(): void {
    this.user = { ...defaultUser };
  }
}
