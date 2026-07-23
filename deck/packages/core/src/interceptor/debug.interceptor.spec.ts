import type { ILocationService, ILogService, IRequestConfig } from 'angular';

import { DebugInterceptor } from './debug.interceptor';
import { JsonUtils } from '../utils';

describe('DebugInterceptor', () => {
  let $location: jasmine.SpyObj<ILocationService>;
  let $log: jasmine.SpyObj<ILogService>;
  let interceptor: DebugInterceptor;

  beforeEach(() => {
    $location = jasmine.createSpyObj('$location', ['url']);
    $log = jasmine.createSpyObj('$log', ['log', 'warn']);
    interceptor = new DebugInterceptor($location, $log);
  });

  it('logs only the exact mutating methods when the injected location enables debugging', () => {
    $location.url.and.returnValue('/applications?debug=true');
    const data = { z: 1, a: 2 };

    ['POST', 'PUT', 'DELETE'].forEach((method) => {
      const config = { method, url: '/tasks', data } as IRequestConfig;
      expect(interceptor.request(config)).toBe(config);
    });
    interceptor.request({ method: 'GET', url: '/tasks', data } as IRequestConfig);
    interceptor.request({ method: 'PATCH', url: '/tasks', data } as IRequestConfig);
    interceptor.request({ method: 'post', url: '/tasks', data } as IRequestConfig);

    const sortedData = JsonUtils.makeSortedStringFromObject(data);
    expect($log.log.calls.allArgs()).toEqual([
      ['POST: /tasks \n', sortedData],
      ['PUT: /tasks \n', sortedData],
      ['DELETE: /tasks \n', sortedData],
    ]);
  });

  it('does not log when the injected location does not enable debugging', () => {
    $location.url.and.returnValues('', '/applications?debug=false');

    interceptor.request({ method: 'POST', url: '/first' } as IRequestConfig);
    interceptor.request({ method: 'DELETE', url: '/second' } as IRequestConfig);

    expect($log.log).not.toHaveBeenCalled();
  });

  it('reports interceptor failures through the injected logger and preserves the request', () => {
    $location.url.and.returnValue('/applications?debug=true');
    const error = new Error('cannot serialize');
    spyOn(JsonUtils, 'makeSortedStringFromObject').and.throwError(error);
    const config = { method: 'POST', url: '/tasks', data: {} } as IRequestConfig;

    expect(interceptor.request(config)).toBe(config);

    expect($log.warn).toHaveBeenCalledOnceWith('Debug interceptor bug: ', error.message);
  });
});
