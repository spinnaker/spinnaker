/* eslint-disable @spinnaker/migrate-to-mock-http-client */
import { mock } from 'angular';
import { Subscription } from 'rxjs';

import { AuthenticationInitializer } from './AuthenticationInitializer';
import { AuthenticationService } from './AuthenticationService';
import { AUTHENTICATION_MODULE, initializeAuthentication, resetAuthenticationRuntime } from './authentication.module';
import { AngularServices } from '../angular/services';
import { RequestBuilder } from '../api/ApiService';
import { FailClosedHttpClient, mockHttpClient } from '../api/mock/jasmine';
import { SETTINGS } from '../config/settings';
import type { IScheduler } from '../scheduler/SchedulerFactory';
import { SchedulerFactory } from '../scheduler/SchedulerFactory';

declare const window: any;

const createDeferred = <T>() => {
  let resolve: (value: T | PromiseLike<T>) => void;
  let reject: (reason?: unknown) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });

  return { promise, reject, resolve };
};

describe('AuthenticationInitializer', function () {
  const authenticationUnsubscribes: Array<() => void> = [];

  beforeEach(() => (SETTINGS.authEnabled = false));
  beforeEach(() => (window.spinnakerSettings.authEnabled = false));
  beforeEach(() => AuthenticationService.reset());
  beforeEach(mock.module(AUTHENTICATION_MODULE));

  afterEach(() => {
    authenticationUnsubscribes.splice(0).forEach((unsubscribe) => unsubscribe());
    AuthenticationService.reset();
    SETTINGS.resetToOriginal();
  });

  describe('authenticateUser', () => {
    it('keeps an unstubbed authentication request fail-closed without using fetch', async () => {
      const client = RequestBuilder.defaultHttpClient as FailClosedHttpClient;
      const loginRedirect = spyOn(AuthenticationInitializer, 'loginRedirect');
      const fetchRequest = spyOn(window, 'fetch');

      const result = await AuthenticationInitializer.authenticateUser();

      expect(result).toBe(false);
      expect(loginRedirect).toHaveBeenCalledTimes(1);
      expect(fetchRequest).not.toHaveBeenCalled();
      expect(client.requests).toEqual([{ method: 'GET', url: SETTINGS.authEndpoint }]);
      client.requests.length = 0;
    });

    it('uses the controlled HTTP client configured by the test harness', async () => {
      const http = mockHttpClient();
      http.expectGET(SETTINGS.authEndpoint).respond(200, { username: 'controlled-user', roles: ['controlled-role'] });
      const fetchRequest = spyOn(window, 'fetch');

      const authentication = AuthenticationInitializer.authenticateUser();
      await http.flush();

      expect(await authentication).toBe(true);
      expect(fetchRequest).not.toHaveBeenCalled();
      expect(AuthenticationService.getAuthenticatedUser()).toEqual(
        jasmine.objectContaining({ name: 'controlled-user', roles: ['controlled-role'], authenticated: true }),
      );
    });

    it('resolves true after updating the authenticated user', async function () {
      const http = mockHttpClient();
      http.expectGET(SETTINGS.authEndpoint).respond(200, {
        username: 'joe!',
        roles: ['role-a'],
        canMintApiTokens: true,
        isAdmin: true,
      });

      const authentication = AuthenticationInitializer.authenticateUser();

      expect((AngularServices.$rootScope as any).authenticating).toBe(true);
      await http.flush();
      const result = await authentication;

      expect(result).toBe(true);
      expect((AngularServices.$rootScope as any).authenticating).toBe(false);
      expect(AuthenticationService.getAuthenticatedUser()).toEqual(
        jasmine.objectContaining({
          name: 'joe!',
          roles: ['role-a'],
          authenticated: true,
          canMintApiTokens: true,
          isAdmin: true,
        }),
      );
    });

    it('defaults API-token and admin permissions when the auth response omits them', async function () {
      const http = mockHttpClient();
      http.expectGET(SETTINGS.authEndpoint).respond(200, { username: 'joe!' });

      const authentication = AuthenticationInitializer.authenticateUser();

      await http.flush();
      await authentication;

      expect(AuthenticationService.getAuthenticatedUser()).toEqual(
        jasmine.objectContaining({ canMintApiTokens: false, isAdmin: false }),
      );
    });

    it('resolves false and redirects once when the response has no username', async function () {
      const loginRedirect = spyOn(AuthenticationInitializer, 'loginRedirect');
      AuthenticationService.setAuthenticatedUser({
        name: 'stale-user',
        authenticated: false,
        roles: ['stale-role'],
      });
      const http = mockHttpClient();
      http.expectGET(SETTINGS.authEndpoint).respond(200, {});

      const authentication = AuthenticationInitializer.authenticateUser();

      await http.flush();
      const result = await authentication;

      expect(result).toBe(false);
      expect(loginRedirect).toHaveBeenCalledTimes(1);
      expect((AngularServices.$rootScope as any).authenticating).toBe(false);
      expect(AuthenticationService.getAuthenticatedUser()).toEqual({
        name: '[anonymous]',
        authenticated: false,
        roles: [],
        canMintApiTokens: false,
        isAdmin: false,
      });
    });

    it('resolves false and redirects once when the authentication request fails', async function () {
      const loginRedirect = spyOn(AuthenticationInitializer, 'loginRedirect');
      AuthenticationService.setAuthenticatedUser({
        name: 'stale-user',
        authenticated: false,
        roles: ['stale-role'],
      });
      const http = mockHttpClient();
      http.expectGET(SETTINGS.authEndpoint).respond(500, null);

      const authentication = AuthenticationInitializer.authenticateUser();

      await http.flush();
      const result = await authentication;

      expect(result).toBe(false);
      expect(loginRedirect).toHaveBeenCalledTimes(1);
      expect((AngularServices.$rootScope as any).authenticating).toBe(false);
      expect(AuthenticationService.getAuthenticatedUser()).toEqual({
        name: '[anonymous]',
        authenticated: false,
        roles: [],
        canMintApiTokens: false,
        isAdmin: false,
      });
    });

    it('resolves false and redirects once when a successful response is malformed', async function () {
      const loginRedirect = spyOn(AuthenticationInitializer, 'loginRedirect');
      AuthenticationService.setAuthenticatedUser({
        name: 'stale-user',
        authenticated: false,
        roles: ['stale-role'],
      });
      const http = mockHttpClient();
      http.expectGET(SETTINGS.authEndpoint).respond(200, null);

      const authentication = AuthenticationInitializer.authenticateUser();

      await http.flush();
      const result = await authentication;

      expect(result).toBe(false);
      expect(loginRedirect).toHaveBeenCalledTimes(1);
      expect((AngularServices.$rootScope as any).authenticating).toBe(false);
      expect(AuthenticationService.getAuthenticatedUser()).toEqual({
        name: '[anonymous]',
        authenticated: false,
        roles: [],
        canMintApiTokens: false,
        isAdmin: false,
      });
    });

    it('keeps valid authentication successful when one listener throws', async function () {
      const loginRedirect = spyOn(AuthenticationInitializer, 'loginRedirect');
      const reportError = spyOn(console, 'error');
      const nextListener = jasmine.createSpy('nextListener');
      const listenerError = new Error('listener failed');
      authenticationUnsubscribes.push(
        AuthenticationService.onAuthentication(() => {
          throw listenerError;
        }),
        AuthenticationService.onAuthentication(nextListener),
      );
      const http = mockHttpClient();
      http.expectGET(SETTINGS.authEndpoint).respond(200, { username: 'joe!', roles: ['role-a'] });

      const authentication = AuthenticationInitializer.authenticateUser();

      await http.flush();
      const result = await authentication;

      expect(result).toBe(true);
      expect(loginRedirect).not.toHaveBeenCalled();
      expect((AngularServices.$rootScope as any).authenticating).toBe(false);
      expect(nextListener).toHaveBeenCalledTimes(1);
      expect(reportError).toHaveBeenCalledOnceWith('Authentication listener failed', listenerError);
      expect(AuthenticationService.getAuthenticatedUser()).toEqual(
        jasmine.objectContaining({ name: 'joe!', authenticated: true, roles: ['role-a'] }),
      );
    });
  });
});

describe('initializeAuthentication', () => {
  let scheduledReauthentication: () => void;

  const createTestScheduler = (): IScheduler => ({
    subscribe: jasmine.createSpy('subscribe').and.callFake((next?: () => void) => {
      scheduledReauthentication = next;
      return new Subscription();
    }),
    scheduleImmediate: jasmine.createSpy('scheduleImmediate'),
    unsubscribe: jasmine.createSpy('unsubscribe'),
  });

  beforeEach(() => {
    SETTINGS.authEnabled = true;
    SETTINGS.authTtl = 1234;
    AuthenticationService.reset();
  });

  afterEach(() => {
    resetAuthenticationRuntime();
    AuthenticationService.reset();
    SETTINGS.resetToOriginal();
    scheduledReauthentication = null;
  });

  it('resolves true without authenticating or creating a scheduler when auth is disabled', async () => {
    SETTINGS.authEnabled = false;
    const authenticateUser = spyOn(AuthenticationInitializer, 'authenticateUser');
    const createScheduler = spyOn(SchedulerFactory, 'createScheduler');

    const result = await initializeAuthentication();

    expect(result).toBe(true);
    expect(authenticateUser).not.toHaveBeenCalled();
    expect(createScheduler).not.toHaveBeenCalled();
  });

  it('shares one authentication request and scheduler between concurrent successful initializations', async () => {
    const request = createDeferred<any>();
    const scheduler = createTestScheduler();
    const createScheduler = spyOn(SchedulerFactory, 'createScheduler').and.returnValue(scheduler);
    const get = spyOn(AuthenticationInitializer as any, 'get').and.returnValue(request.promise);

    const firstInitialization = initializeAuthentication();
    const secondInitialization = initializeAuthentication();

    expect(secondInitialization).toBe(firstInitialization);
    expect(get).toHaveBeenCalledOnceWith(SETTINGS.authEndpoint);
    expect(createScheduler).toHaveBeenCalledOnceWith(1234);
    expect(scheduler.subscribe).toHaveBeenCalledTimes(1);

    request.resolve({ data: { username: 'new-user', roles: ['new-role'] } });

    expect(await Promise.all([firstInitialization, secondInitialization])).toEqual([true, true]);
    expect(AuthenticationService.getAuthenticatedUser()).toEqual(
      jasmine.objectContaining({ name: 'new-user', authenticated: true, roles: ['new-role'] }),
    );
    expect((AngularServices.$rootScope as any).authenticating).toBe(false);
  });

  it('shares one failed authentication result and redirect between concurrent initializations', async () => {
    const request = createDeferred<any>();
    const scheduler = createTestScheduler();
    spyOn(SchedulerFactory, 'createScheduler').and.returnValue(scheduler);
    const get = spyOn(AuthenticationInitializer as any, 'get').and.returnValue(request.promise);
    const loginRedirect = spyOn(AuthenticationInitializer, 'loginRedirect');
    AuthenticationService.setAuthenticatedUser({
      name: 'stale-user',
      authenticated: false,
      roles: ['stale-role'],
    });

    const firstInitialization = initializeAuthentication();
    const secondInitialization = initializeAuthentication();
    request.resolve({ data: {} });

    expect(await Promise.all([firstInitialization, secondInitialization])).toEqual([false, false]);
    expect(get).toHaveBeenCalledTimes(1);
    expect(loginRedirect).toHaveBeenCalledTimes(1);
    expect(AuthenticationService.getAuthenticatedUser()).toEqual({
      name: '[anonymous]',
      authenticated: false,
      roles: [],
      canMintApiTokens: false,
      isAdmin: false,
    });
    expect((AngularServices.$rootScope as any).authenticating).toBe(false);
  });

  it('authenticates again after the previous initialization settles', async () => {
    const scheduler = createTestScheduler();
    const createScheduler = spyOn(SchedulerFactory, 'createScheduler').and.returnValue(scheduler);
    const authenticateUser = spyOn(AuthenticationInitializer, 'authenticateUser').and.returnValues(
      Promise.resolve(true),
      Promise.resolve(false),
    );
    const reauthenticateUser = spyOn(AuthenticationInitializer, 'reauthenticateUser');

    expect(await initializeAuthentication()).toBe(true);
    expect(await initializeAuthentication()).toBe(false);
    scheduledReauthentication();

    expect(createScheduler).toHaveBeenCalledOnceWith(1234);
    expect(scheduler.subscribe).toHaveBeenCalledTimes(1);
    expect(authenticateUser).toHaveBeenCalledTimes(2);
    expect(reauthenticateUser).toHaveBeenCalledTimes(1);
  });

  [
    { description: 'missing', value: undefined },
    { description: 'zero', value: 0 },
    { description: 'negative', value: -1 },
    { description: 'NaN', value: Number.NaN },
    { description: 'infinite', value: Number.POSITIVE_INFINITY },
  ].forEach(({ description, value }) => {
    it(`uses the default authentication interval when authTtl is ${description}`, async () => {
      SETTINGS.authTtl = value as number;
      const scheduler = createTestScheduler();
      const createScheduler = spyOn(SchedulerFactory, 'createScheduler').and.returnValue(scheduler);
      spyOn(AuthenticationInitializer, 'authenticateUser').and.returnValue(Promise.resolve(true));

      await initializeAuthentication();

      expect(createScheduler).toHaveBeenCalledOnceWith(600000);
    });
  });

  it('unsubscribes the scheduler and creates a new one after reset', async () => {
    const firstScheduler = createTestScheduler();
    const secondScheduler = createTestScheduler();
    const createScheduler = spyOn(SchedulerFactory, 'createScheduler').and.returnValues(
      firstScheduler,
      secondScheduler,
    );
    spyOn(AuthenticationInitializer, 'authenticateUser').and.returnValue(Promise.resolve(true));

    await initializeAuthentication();
    resetAuthenticationRuntime();
    await initializeAuthentication();

    expect(firstScheduler.unsubscribe).toHaveBeenCalledTimes(1);
    expect(secondScheduler.subscribe).toHaveBeenCalledTimes(1);
    expect(createScheduler).toHaveBeenCalledTimes(2);
  });

  it('does not let a successful stale generation overwrite a new initialization', async () => {
    const firstRequest = createDeferred<any>();
    const secondRequest = createDeferred<any>();
    const firstScheduler = createTestScheduler();
    const secondScheduler = createTestScheduler();
    spyOn(SchedulerFactory, 'createScheduler').and.returnValues(firstScheduler, secondScheduler);
    spyOn(AuthenticationInitializer as any, 'get').and.returnValues(firstRequest.promise, secondRequest.promise);
    const loginRedirect = spyOn(AuthenticationInitializer, 'loginRedirect');

    const firstInitialization = initializeAuthentication();
    resetAuthenticationRuntime();
    const secondInitialization = initializeAuthentication();

    firstRequest.resolve({ data: { username: 'stale-user', roles: ['stale-role'] } });

    expect(await firstInitialization).toBe(false);
    expect(AuthenticationService.getAuthenticatedUser().name).toBe('[anonymous]');
    expect((AngularServices.$rootScope as any).authenticating).toBe(true);
    expect(initializeAuthentication()).toBe(secondInitialization);

    secondRequest.resolve({ data: { username: 'new-user', roles: ['new-role'] } });

    expect(await secondInitialization).toBe(true);
    expect(AuthenticationService.getAuthenticatedUser()).toEqual(
      jasmine.objectContaining({ name: 'new-user', authenticated: true, roles: ['new-role'] }),
    );
    expect(loginRedirect).not.toHaveBeenCalled();
    expect((AngularServices.$rootScope as any).authenticating).toBe(false);
  });

  it('does not let a failed stale generation clear a new initialization or redirect', async () => {
    const firstRequest = createDeferred<any>();
    const secondRequest = createDeferred<any>();
    const firstScheduler = createTestScheduler();
    const secondScheduler = createTestScheduler();
    spyOn(SchedulerFactory, 'createScheduler').and.returnValues(firstScheduler, secondScheduler);
    spyOn(AuthenticationInitializer as any, 'get').and.returnValues(firstRequest.promise, secondRequest.promise);
    const loginRedirect = spyOn(AuthenticationInitializer, 'loginRedirect');

    const firstInitialization = initializeAuthentication();
    resetAuthenticationRuntime();
    const secondInitialization = initializeAuthentication();

    firstRequest.reject(new Error('stale request failed'));

    expect(await firstInitialization).toBe(false);
    expect(loginRedirect).not.toHaveBeenCalled();
    expect((AngularServices.$rootScope as any).authenticating).toBe(true);
    expect(initializeAuthentication()).toBe(secondInitialization);

    secondRequest.resolve({ data: { username: 'new-user', roles: ['new-role'] } });

    expect(await secondInitialization).toBe(true);
    expect(AuthenticationService.getAuthenticatedUser()).toEqual(
      jasmine.objectContaining({ name: 'new-user', authenticated: true, roles: ['new-role'] }),
    );
    expect(loginRedirect).not.toHaveBeenCalled();
    expect((AngularServices.$rootScope as any).authenticating).toBe(false);
  });
});

describe('authentication module startup', () => {
  const initializationError = new Error('initialization failed');
  const reportError = jasmine.createSpy('reportError');

  beforeEach(() => {
    SETTINGS.authEnabled = true;
    SETTINGS.authTtl = 1234;
    const scheduler: IScheduler = {
      subscribe: () => new Subscription(),
      scheduleImmediate: jasmine.createSpy('scheduleImmediate'),
      unsubscribe: jasmine.createSpy('unsubscribe'),
    };
    spyOn(SchedulerFactory, 'createScheduler').and.returnValue(scheduler);
    spyOn(AuthenticationInitializer, 'authenticateUser').and.returnValue(Promise.reject(initializationError));
    spyOnProperty(AngularServices, '$log', 'get').and.returnValue({ error: reportError } as any);
  });
  beforeEach(mock.module(AUTHENTICATION_MODULE));
  beforeEach(mock.inject(() => undefined));

  afterEach(() => {
    resetAuthenticationRuntime();
    SETTINGS.resetToOriginal();
    reportError.calls.reset();
  });

  it('reports unexpected initializeAuthentication rejections', async () => {
    await new Promise((resolve) => setTimeout(resolve));

    expect(reportError).toHaveBeenCalledOnceWith('Failed to initialize authentication', initializationError);
  });
});
