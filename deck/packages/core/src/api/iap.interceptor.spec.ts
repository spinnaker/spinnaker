import type { IHttpBackendService, IHttpService, IQService, IRootScopeService } from 'angular';
import { mock } from 'angular';

import { SETTINGS } from '../config/settings';
import { IAP_INTERCEPTOR, IapInterceptor } from './iap.interceptor';
import {
  captureIapSessionRefreshGeneration,
  getIapSessionRequestState,
  refreshIapSession,
  resetIapSessionRefreshState,
} from './iapSessionRefresh';

describe('IapInterceptor', () => {
  const previousIapRefresherEnabled = SETTINGS.feature.iapRefresherEnabled;
  const $q = { reject: (response: any) => Promise.reject(response) } as IQService;

  function deferred<T>() {
    let resolve: (value: T) => void;
    let reject: (error: unknown) => void;
    const promise = new Promise<T>((resolvePromise, rejectPromise) => {
      resolve = resolvePromise;
      reject = rejectPromise;
    });
    return { promise, resolve: resolve!, reject: reject! };
  }

  function interceptorFor($http: any): IapInterceptor {
    return new IapInterceptor({ get: jasmine.createSpy('get').and.returnValue($http) } as any, $q);
  }

  beforeEach(() => {
    resetIapSessionRefreshState();
  });

  afterEach(() => {
    SETTINGS.feature.iapRefresherEnabled = previousIapRefresherEnabled;
  });

  it('refreshes an expired Cloud IAP session and retries the original request', async () => {
    SETTINGS.feature.iapRefresherEnabled = true;
    const retriedResponse = { data: 'retried' };
    const $http = jasmine.createSpy('$http').and.returnValue(Promise.resolve(retriedResponse)) as any;
    $http.get = jasmine.createSpy('get').and.returnValue(Promise.resolve({}));
    const requestConfig = { method: 'POST', url: '/applications' };

    const result = await interceptorFor($http).responseError({ config: requestConfig, status: 401 } as any);

    expect($http.get).toHaveBeenCalledWith('/_gcp_iap/do_session_refresh');
    expect($http).toHaveBeenCalledTimes(1);
    expect($http.calls.mostRecent().args[0]).toEqual(jasmine.objectContaining(requestConfig));
    expect(result).toBe(retriedResponse as any);
  });

  it('rejects the original response when the Cloud IAP refresh fails', async () => {
    SETTINGS.feature.iapRefresherEnabled = true;
    const originalResponse = { config: { method: 'GET', url: '/applications' }, status: 401 };
    const $http = jasmine.createSpy('$http') as any;
    $http.get = jasmine.createSpy('get').and.returnValue(Promise.reject(new Error('refresh failed')));

    await expectAsync(interceptorFor($http).responseError(originalResponse as any) as any).toBeRejectedWith(
      originalResponse,
    );
    expect($http).not.toHaveBeenCalled();
  });

  it('shares one in-flight refresh across concurrent responses and retries both requests', async () => {
    SETTINGS.feature.iapRefresherEnabled = true;
    const refresh = deferred<{}>();
    const $http = jasmine
      .createSpy('$http')
      .and.callFake((config: any) => Promise.resolve({ data: config.url })) as any;
    $http.get = jasmine.createSpy('get').and.returnValue(refresh.promise);
    const interceptor = interceptorFor($http);

    const first = interceptor.responseError({ config: { method: 'GET', url: '/first' }, status: 401 } as any);
    const second = interceptor.responseError({ config: { method: 'GET', url: '/second' }, status: 401 } as any);

    expect($http.get).toHaveBeenCalledTimes(1);
    refresh.resolve({});
    await expectAsync(first as any).toBeResolvedTo({ data: '/first' });
    await expectAsync(second as any).toBeResolvedTo({ data: '/second' });
    expect($http).toHaveBeenCalledTimes(2);
  });

  it('retries a staggered old-generation response without starting a second refresh', async () => {
    SETTINGS.feature.iapRefresherEnabled = true;
    const refresh = deferred<{}>();
    const $http = jasmine
      .createSpy('$http')
      .and.callFake((config: any) => Promise.resolve({ data: config.url })) as any;
    $http.get = jasmine.createSpy('get').and.returnValue(refresh.promise);
    const interceptor = interceptorFor($http);
    const firstConfig = { method: 'GET', url: '/first' };
    const secondConfig = { method: 'GET', url: '/second' };
    interceptor.request(firstConfig as any);
    interceptor.request(secondConfig as any);

    const first = interceptor.responseError({ config: firstConfig, status: 401 } as any);
    refresh.resolve({});
    await expectAsync(first as any).toBeResolvedTo({ data: '/first' });
    const second = interceptor.responseError({ config: secondConfig, status: 401 } as any);

    await expectAsync(second as any).toBeResolvedTo({ data: '/second' });
    expect($http.get).toHaveBeenCalledTimes(1);
    expect($http).toHaveBeenCalledTimes(2);
  });

  it('stores request generation metadata without adding enumerable config fields', () => {
    const $http = jasmine.createSpy('$http') as any;
    const config = { method: 'GET', url: '/applications', callerMetadata: 'preserved' };

    expect(interceptorFor($http).request(config as any)).toBe(config as any);
    expect(Object.keys(config)).toEqual(['method', 'url', 'callerMetadata']);
  });

  it('consumes copied retry state without leaking metadata or altering request data', async () => {
    SETTINGS.feature.iapRefresherEnabled = true;
    let interceptor: IapInterceptor;
    let retriedConfig: any;
    const headers = { 'X-Custom': 'value' };
    const params = { query: 'value' };
    const data = { value: 'body' };
    const originalConfig = { method: 'POST', url: '/applications', headers, params, data };
    const $http = jasmine.createSpy('$http').and.callFake((config: any) => {
      retriedConfig = { ...config };
      interceptor.request(retriedConfig);
      return Promise.resolve({ data: 'retried' });
    }) as any;
    $http.get = jasmine.createSpy('get').and.returnValue(Promise.resolve({}));
    interceptor = interceptorFor($http);
    interceptor.request(originalConfig as any);

    await interceptor.responseError({ config: originalConfig, status: 401 } as any);

    expect(Object.keys(originalConfig)).toEqual(['method', 'url', 'headers', 'params', 'data']);
    expect(Object.keys(retriedConfig)).toEqual(['method', 'url', 'headers', 'params', 'data']);
    expect(retriedConfig.headers).toBe(headers);
    expect(retriedConfig.params).toBe(params);
    expect(retriedConfig.data).toBe(data);
    expect(getIapSessionRequestState(retriedConfig)).toEqual({ generation: 1, refreshAttempted: true });
  });

  it('resets generation and detaches an in-flight refresh', async () => {
    const stale = deferred<{}>();
    const staleRefresh = refreshIapSession(() => stale.promise);

    resetIapSessionRefreshState();
    expect(captureIapSessionRefreshGeneration()).toBe(0);

    const current = deferred<{}>();
    const startCurrentRefresh = jasmine.createSpy('startCurrentRefresh').and.returnValue(current.promise);
    const currentRefresh = refreshIapSession(startCurrentRefresh);

    stale.resolve({});
    await staleRefresh;
    const joinedRefresh = refreshIapSession(startCurrentRefresh);
    expect(startCurrentRefresh).toHaveBeenCalledTimes(1);

    current.resolve({});
    await Promise.all([currentRefresh, joinedRefresh]);
    expect(captureIapSessionRefreshGeneration()).toBe(1);

    resetIapSessionRefreshState();
    expect(captureIapSessionRefreshGeneration()).toBe(0);
  });

  it('clears a failed refresh so a later response can refresh and retry', async () => {
    SETTINGS.feature.iapRefresherEnabled = true;
    const $http = jasmine.createSpy('$http').and.returnValue(Promise.resolve({ data: 'retried' })) as any;
    $http.get = jasmine
      .createSpy('get')
      .and.returnValues(Promise.reject(new Error('first refresh failed')), Promise.resolve({}));
    const interceptor = interceptorFor($http);
    const firstResponse = { config: { method: 'GET', url: '/first' }, status: 401 };

    await expectAsync(interceptor.responseError(firstResponse as any) as any).toBeRejectedWith(firstResponse);
    await expectAsync(
      interceptor.responseError({ config: { method: 'GET', url: '/second' }, status: 401 } as any) as any,
    ).toBeResolvedTo({ data: 'retried' });
    expect($http.get).toHaveBeenCalledTimes(2);
    expect($http).toHaveBeenCalledTimes(1);
  });

  it('does not advance generation after a failed refresh', async () => {
    SETTINGS.feature.iapRefresherEnabled = true;
    const $http = jasmine.createSpy('$http').and.returnValue(Promise.resolve({ data: 'retried' })) as any;
    $http.get = jasmine
      .createSpy('get')
      .and.returnValues(Promise.reject(new Error('first refresh failed')), Promise.resolve({}));
    const interceptor = interceptorFor($http);
    const firstConfig = { method: 'GET', url: '/first' };
    const secondConfig = { method: 'GET', url: '/second' };
    interceptor.request(firstConfig as any);
    interceptor.request(secondConfig as any);

    const firstResponse = { config: firstConfig, status: 401 };
    await expectAsync(interceptor.responseError(firstResponse as any) as any).toBeRejectedWith(firstResponse);
    await expectAsync(interceptor.responseError({ config: secondConfig, status: 401 } as any) as any).toBeResolvedTo({
      data: 'retried',
    });

    expect($http.get).toHaveBeenCalledTimes(2);
    expect($http).toHaveBeenCalledTimes(1);
  });

  it("does not refresh the refresh endpoint's own 401 response", async () => {
    SETTINGS.feature.iapRefresherEnabled = true;
    const response = { config: { method: 'GET', url: '/_gcp_iap/do_session_refresh' }, status: 401 };
    const $http = jasmine.createSpy('$http') as any;
    $http.get = jasmine.createSpy('get');

    await expectAsync(interceptorFor($http).responseError(response as any) as any).toBeRejectedWith(response);
    expect($http.get).not.toHaveBeenCalled();
    expect($http).not.toHaveBeenCalled();
  });

  it('does not refresh again when the retry also receives a 401 response', async () => {
    SETTINGS.feature.iapRefresherEnabled = true;
    const retryResponse = { config: null as any, status: 401, data: { attempt: 2 } };
    let interceptor: IapInterceptor;
    const $http = jasmine.createSpy('$http').and.callFake((config: any) => {
      interceptor.request(config);
      retryResponse.config = config;
      return interceptor.responseError(retryResponse as any);
    }) as any;
    $http.get = jasmine.createSpy('get').and.returnValue(Promise.resolve({}));
    interceptor = interceptorFor($http);

    await expectAsync(
      interceptor.responseError({ config: { method: 'GET', url: '/applications' }, status: 401 } as any) as any,
    ).toBeRejectedWith(retryResponse);
    expect($http.get).toHaveBeenCalledTimes(1);
    expect($http).toHaveBeenCalledTimes(1);
  });

  it('rejects a retried Angular request that also receives a qualifying 401 without refreshing again', async () => {
    SETTINGS.feature.iapRefresherEnabled = true;
    mock.module(IAP_INTERCEPTOR);

    let http: IHttpService;
    let httpBackend: IHttpBackendService;
    let rootScope: IRootScopeService;
    mock.inject(($http: IHttpService, $httpBackend: IHttpBackendService, $rootScope: IRootScopeService) => {
      http = $http;
      httpBackend = $httpBackend;
      rootScope = $rootScope;
    });
    let rejection: any;
    let applicationRequests = 0;
    let refreshRequests = 0;
    httpBackend.whenGET('/applications').respond(() => [401, { attempt: ++applicationRequests }]);
    httpBackend.whenGET('/_gcp_iap/do_session_refresh').respond(() => {
      refreshRequests += 1;
      return [204];
    });

    http.get('/applications').catch((response) => {
      rejection = response;
    });
    const flushOneWithoutDigest = () => (httpBackend.flush as any)(1, 0, false);
    rootScope.$digest();
    flushOneWithoutDigest();
    rootScope.$digest();
    flushOneWithoutDigest();
    rootScope.$digest();
    await new Promise((resolve) => setTimeout(resolve));
    rootScope.$digest();
    await new Promise((resolve) => setTimeout(resolve));
    rootScope.$digest();
    flushOneWithoutDigest();
    rootScope.$digest();
    await new Promise((resolve) => setTimeout(resolve));
    rootScope.$digest();

    expect(rejection?.status).toBe(401);
    expect(rejection?.data).toEqual({ attempt: 2 });
    expect(Object.keys(rejection?.config || {}).some((key) => /iap.*retry/i.test(key))).toBe(false);
    expect(applicationRequests).toBe(2);
    expect(refreshRequests).toBe(1);
    httpBackend.verifyNoOutstandingRequest();
  });

  it('does not refresh non-IAP responses', async () => {
    SETTINGS.feature.iapRefresherEnabled = false;
    const originalResponse = { config: { method: 'GET', url: '/applications' }, status: 401 };
    const $http = jasmine.createSpy('$http') as any;
    $http.get = jasmine.createSpy('get');

    await expectAsync(interceptorFor($http).responseError(originalResponse as any) as any).toBeRejectedWith(
      originalResponse,
    );
    expect($http.get).not.toHaveBeenCalled();
    expect($http).not.toHaveBeenCalled();
  });

  it('registers without creating a circular $http dependency', () => {
    mock.module(IAP_INTERCEPTOR);

    mock.inject(($http: IHttpService) => {
      expect($http).toBeDefined();
    });
  });
});
