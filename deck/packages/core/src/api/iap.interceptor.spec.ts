import type { IHttpService, IQService } from 'angular';
import { mock } from 'angular';

import { SETTINGS } from '../config/settings';
import { IAP_INTERCEPTOR, IapInterceptor } from './iap.interceptor';

describe('IapInterceptor', () => {
  const previousIapRefresherEnabled = SETTINGS.feature.iapRefresherEnabled;
  const $q = { reject: (response: any) => Promise.reject(response) } as IQService;

  function interceptorFor($http: any): IapInterceptor {
    return new IapInterceptor({ get: jasmine.createSpy('get').and.returnValue($http) } as any, $q);
  }

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
    expect($http).toHaveBeenCalledWith(requestConfig);
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
