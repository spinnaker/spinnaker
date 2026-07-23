import type { IHttpClientImplementation } from './ApiService';
import { invalidContentMessage, makeRequestBuilderConfig, RequestBuilder } from './ApiService';
import { AuthenticationInitializer } from '../authentication/AuthenticationInitializer';
import { SETTINGS } from '../config/settings';
import { resetIapSessionRefreshState } from './iapSessionRefresh';

describe('RequestBuilder backend', () => {
  const createBackend = (): IHttpClientImplementation => jasmine.createSpyObj(['get', 'post', 'put', 'delete']);

  it('receives a url prefixed with the baseUrl', () => {
    const backend = createBackend();
    new RequestBuilder(undefined, backend, 'thebaseurl').get();
    expect(backend.get).toHaveBeenCalledWith(jasmine.objectContaining({ url: 'thebaseurl' }));
  });

  it('url prefix defaults to the gate url', () => {
    const backend = createBackend();
    new RequestBuilder(undefined, backend).get();
    expect(backend.get).toHaveBeenCalledWith(jasmine.objectContaining({ url: SETTINGS.gateUrl }));
  });

  it('receives the url prefixed with the gate url (when no baseUrl specified)', () => {
    const backend = createBackend();
    new RequestBuilder(undefined, backend).get();
    expect(backend.get).toHaveBeenCalledWith(jasmine.objectContaining({ url: SETTINGS.gateUrl }));
  });

  it('accepts a different base url', function () {
    const backend = createBackend();
    new RequestBuilder(undefined, backend, 'http://different').get();
    expect(backend.get).toHaveBeenCalledWith(jasmine.objectContaining({ url: 'http://different' }));
  });

  it('trims trailing slashes from base url', function () {
    const backend = createBackend();
    new RequestBuilder(undefined, backend, 'http://different//////').get();
    expect(backend.get).toHaveBeenCalledWith(jasmine.objectContaining({ url: 'http://different' }));
  });

  it('trims trailing slashes from base urls with embedded paths', function () {
    const backend = createBackend();
    new RequestBuilder(undefined, backend, 'http://different/path/////').get();
    expect(backend.get).toHaveBeenCalledWith(jasmine.objectContaining({ url: 'http://different/path' }));
  });

  it('trims all leading and trailing slashes from the base url', function () {
    const backend = createBackend();
    new RequestBuilder(undefined, backend, '///http://different/path/////').path('foo').get();
    expect(backend.get).toHaveBeenCalledWith(jasmine.objectContaining({ url: 'http://different/path/foo' }));
  });
});

describe('REST Service', function () {
  const builder = (pathPrefix?: string) => new RequestBuilder(makeRequestBuilderConfig(pathPrefix));
  afterEach(() => SETTINGS.resetToOriginal());

  describe('makeRequestBuilderConfig', () => {
    it('trims leading slashes from the path prefix', function () {
      const result = builder('/foo');
      expect(result['config'].url).toEqual(`foo`);
    });

    it('trims trailing slashes from the path prefix', function () {
      const result = builder('foo/');
      expect(result['config'].url).toEqual(`foo`);
    });

    it('trims repeated leading and trailing slashes from the path prefix', function () {
      const result = builder('///foo///');
      expect(result['config'].url).toEqual(`foo`);
    });
  });

  describe('RequestBuilder.path()', function () {
    it('joins a path to the current url using a /', function () {
      const result = builder().path('foo');
      expect(result['config'].url).toEqual(`foo`);
    });

    it('joins multiple path to the current url using a /', function () {
      const result = builder().path('foo', 'bar');
      expect(result['config'].url).toEqual(`foo/bar`);
    });

    it('can be chained', () => {
      const result = builder().path('foo').path('bar');
      expect(result['config'].url).toEqual(`foo/bar`);
    });

    it('does not modify the parent builder config', () => {
      const parent = builder().path('foo');
      const child = parent.path('bar');
      expect(parent['config'].url).toEqual(`foo`);
      expect(child['config'].url).toEqual(`foo/bar`);
    });

    it('uriencodes path() parameters', () => {
      const result = builder().path('foo/bar');
      expect(result['config'].url).toEqual(`foo%2Fbar`);
    });
  });

  describe('RequestBuilder.query()', function () {
    it('stores query params in the config', () => {
      const result = builder().query({ foo: 'foo' });
      expect(result['config'].params).toEqual({ foo: 'foo' });
    });

    it('merges new params with any already in the config', () => {
      const result = builder().query({ foo: 'foo' }).query({ bar: 'bar' });
      expect(result['config'].params).toEqual({ foo: 'foo', bar: 'bar' });
    });

    it('prefers newer params when keys are the same as those already in the config', () => {
      const result = builder().query({ foo: 'bar' }).query({ foo: 'foo' });
      expect(result['config'].params).toEqual({ foo: 'foo' });
    });
  });

  describe('RequestBuilder.useCache()', function () {
    it('should set cache to "true" if called with no args', function () {
      const result = builder().useCache();
      expect(result['config'].cache).toBe(true);
    });

    it('should set cache to "true" if called with "true"', function () {
      const result = builder().useCache(true);
      expect(result['config'].cache).toBe(true);
    });

    it('should set cache to "false" if called with "false"', function () {
      const result = builder().useCache(false);
      expect(result['config'].cache).toBe(false);
    });
  });

  describe('direct XHR fallback', () => {
    const originalXMLHttpRequest = window.XMLHttpRequest;
    let reauthenticateUser: jasmine.Spy;

    class FakeXMLHttpRequest {
      public static instances: FakeXMLHttpRequest[] = [];

      public method = '';
      public url = '';
      public explicitRequestHeaders: Array<{ name: string; value: string }> = [];
      public requestBody: BodyInit | unknown;
      public timeout = 0;
      public withCredentials = false;
      public status = 200;
      public statusText = '';
      public responseText = '';
      public onload: () => void = () => undefined;
      public onerror: () => void = () => undefined;
      public ontimeout: () => void = () => undefined;

      private responseHeaders: { [name: string]: string } = {};

      constructor() {
        FakeXMLHttpRequest.instances.push(this);
      }

      public open(method: string, url: string) {
        this.method = method;
        this.url = url;
      }

      public setRequestHeader(name: string, value: string) {
        this.explicitRequestHeaders.push({ name, value });
      }

      public getResponseHeader(name: string) {
        return this.responseHeaders[name.toLowerCase()] || null;
      }

      public send(body?: BodyInit | unknown) {
        this.requestBody = body;
      }

      public respond({
        status = 200,
        statusText = '',
        body = '',
        headers = {},
      }: {
        status?: number;
        statusText?: string;
        body?: string;
        headers?: { [name: string]: string };
      } = {}) {
        this.status = status;
        this.statusText = statusText;
        this.responseText = body;
        this.responseHeaders = Object.keys(headers).reduce(
          (result, name) => ({ ...result, [name.toLowerCase()]: headers[name] }),
          {},
        );
        this.onload();
      }

      public failRequest() {
        this.onerror();
      }

      public timeOut() {
        this.ontimeout();
      }

      public explicitHeaderValues(name: string) {
        return this.explicitRequestHeaders
          .filter((header) => header.name.toLowerCase() === name.toLowerCase())
          .map((header) => header.value);
      }
    }

    const xhrRequest = (method: string, config: { [key: string]: any }) => {
      const promise = (RequestBuilder.defaultHttpClient as any).xhrRequest(method, config) as Promise<any>;
      const request = FakeXMLHttpRequest.instances[FakeXMLHttpRequest.instances.length - 1];
      expect(request).toBeDefined();
      return { promise, request };
    };

    const waitForRequest = async (index: number): Promise<FakeXMLHttpRequest> => {
      for (let attempt = 0; attempt < 10; attempt++) {
        if (FakeXMLHttpRequest.instances[index]) {
          return FakeXMLHttpRequest.instances[index];
        }
        await Promise.resolve();
      }
      throw new Error(`Expected XHR request at index ${index}`);
    };

    class CustomRequestBody {
      constructor(public value: string) {}
    }

    const requestBodyCases: Array<{
      name: string;
      create: () => unknown;
      serializesAsJson: boolean;
    }> = [
      { name: 'plain string', create: () => 'plain request', serializesAsJson: false },
      { name: 'FormData', create: () => new FormData(), serializesAsJson: false },
      { name: 'Blob', create: () => new Blob(['blob request']), serializesAsJson: false },
      { name: 'File', create: () => new File(['file request'], 'request.txt'), serializesAsJson: false },
      { name: 'ArrayBuffer', create: () => new ArrayBuffer(8), serializesAsJson: false },
      { name: 'Uint8Array', create: () => new Uint8Array([1, 2, 3]), serializesAsJson: false },
      { name: 'DataView', create: () => new DataView(new ArrayBuffer(8)), serializesAsJson: false },
      { name: 'URLSearchParams', create: () => new URLSearchParams({ value: 'request' }), serializesAsJson: false },
      { name: 'Document', create: () => document, serializesAsJson: false },
      { name: 'plain object', create: () => ({ value: 'request' }), serializesAsJson: true },
      { name: 'array', create: () => ['request'], serializesAsJson: true },
      { name: 'Date', create: () => new Date('2026-07-10T12:00:00.000Z'), serializesAsJson: true },
      { name: 'custom class', create: () => new CustomRequestBody('request'), serializesAsJson: true },
    ];

    beforeEach(() => {
      FakeXMLHttpRequest.instances = [];
      window.XMLHttpRequest = FakeXMLHttpRequest as any;
      reauthenticateUser = spyOn(AuthenticationInitializer, 'reauthenticateUser');
      resetIapSessionRefreshState();
    });

    afterEach(() => {
      window.XMLHttpRequest = originalXMLHttpRequest;
    });

    it('preserves GET request parameters, headers, credentials, and an empty body', async () => {
      const { promise, request } = xhrRequest('GET', {
        url: '/example',
        headers: { 'X-Custom': 'caller' },
        params: { one: 'first', many: ['second', 'third'], ignored: null },
      });

      expect(request.method).toBe('GET');
      expect(request.url).toBe(`${window.location.origin}/example?one=first&many=second&many=third`);
      expect(request.explicitHeaderValues('X-Custom')).toEqual(['caller']);
      expect(request.explicitHeaderValues('Content-Type')).toEqual([]);
      expect(request.requestBody).toBeUndefined();
      expect(request.timeout).toBe(0);
      expect(request.withCredentials).toBe(true);

      request.respond({ body: 'ok', headers: { 'Content-Type': 'text/plain' } });
      await expectAsync(promise).toBeResolvedTo('ok');
    });

    (['POST', 'PUT', 'PATCH', 'DELETE'] as const).forEach((method) => {
      it(`serializes ${method} data and supplies the default JSON content type`, async () => {
        const data = { method: method.toLowerCase() };
        const { promise, request } = xhrRequest(method, {
          url: '/example',
          data,
          headers: { 'X-Custom': 'caller' },
        });

        expect(request.method).toBe(method);
        expect(request.explicitHeaderValues('X-Custom')).toEqual(['caller']);
        expect(request.explicitHeaderValues('Content-Type')).toEqual(['application/json;charset=utf-8']);
        expect(request.requestBody).toBe(JSON.stringify(data));
        expect(request.withCredentials).toBe(true);

        request.respond({ body: '{}', headers: { 'Content-Type': 'application/json' } });
        await expectAsync(promise).toBeResolvedTo({});
      });
    });

    it('serializes a cross-realm plain object with the default JSON content type', async () => {
      const iframe = document.createElement('iframe');
      document.body.appendChild(iframe);

      try {
        const data = iframe.contentWindow!.JSON.parse('{"value":"cross-realm request"}');
        const { promise, request } = xhrRequest('POST', { url: '/example', data });

        expect(request.requestBody).toBe(JSON.stringify(data));
        expect(request.explicitHeaderValues('Content-Type')).toEqual(['application/json;charset=utf-8']);

        request.respond({ body: 'ok', headers: { 'Content-Type': 'text/plain' } });
        await expectAsync(promise).toBeResolvedTo('ok');
      } finally {
        iframe.remove();
      }
    });

    requestBodyCases
      .filter(({ serializesAsJson }) => !serializesAsJson)
      .forEach(({ name, create }) => {
        it(`passes a ${name} request body through unchanged without setting an explicit JSON content type`, async () => {
          const data = create();
          const { promise, request } = xhrRequest('POST', { url: '/example', data });

          expect(request.requestBody).toBe(data);
          expect(request.explicitHeaderValues('Content-Type')).toEqual([]);

          request.respond({ body: 'ok', headers: { 'Content-Type': 'text/plain' } });
          await expectAsync(promise).toBeResolvedTo('ok');
        });
      });

    requestBodyCases
      .filter(({ serializesAsJson }) => serializesAsJson)
      .forEach(({ name, create }) => {
        it(`serializes a ${name} request body as JSON with the default content type`, async () => {
          const data = create();
          const { promise, request } = xhrRequest('POST', { url: '/example', data });

          expect(request.requestBody).toBe(JSON.stringify(data));
          expect(request.explicitHeaderValues('Content-Type')).toEqual(['application/json;charset=utf-8']);

          request.respond({ body: 'ok', headers: { 'Content-Type': 'text/plain' } });
          await expectAsync(promise).toBeResolvedTo('ok');
        });
      });

    requestBodyCases.forEach(({ name, create, serializesAsJson }) => {
      it(`preserves a caller-supplied content type for a ${name} request body`, async () => {
        const data = create();
        const { promise, request } = xhrRequest('POST', {
          url: '/example',
          data,
          headers: { 'cOnTeNt-TyPe': 'application/custom' },
        });

        expect(request.requestBody).toBe(serializesAsJson ? JSON.stringify(data) : data);
        expect(request.explicitHeaderValues('Content-Type')).toEqual(['application/custom']);

        request.respond({ body: 'ok', headers: { 'Content-Type': 'text/plain' } });
        await expectAsync(promise).toBeResolvedTo('ok');
      });
    });

    it('preserves a caller-supplied content type case-insensitively without adding another', async () => {
      const { promise, request } = xhrRequest('POST', {
        url: '/example',
        data: { example: true },
        headers: { 'cOnTeNt-TyPe': 'application/custom+json', 'X-Custom': 'caller' },
      });

      expect(request.explicitHeaderValues('Content-Type')).toEqual(['application/custom+json']);
      expect(request.explicitHeaderValues('X-Custom')).toEqual(['caller']);

      request.respond({ body: '{}', headers: { 'Content-Type': 'application/json' } });
      await expectAsync(promise).toBeResolvedTo({});
    });

    [
      {
        name: 'application/json',
        contentType: 'application/json;charset=utf-8',
        body: '{"value":"json"}',
        expected: { value: 'json' },
      },
      {
        name: 'application/*+json',
        contentType: 'application/problem+json',
        body: '{"message":"problem"}',
        expected: { message: 'problem' },
      },
      {
        name: 'object-looking text',
        contentType: 'text/plain',
        body: '  \n {"value":"object"}',
        expected: { value: 'object' },
      },
      {
        name: 'array-looking text',
        contentType: 'text/plain',
        body: '\t["array"]',
        expected: ['array'],
      },
      {
        name: 'an empty JSON response',
        contentType: 'application/json',
        body: '',
        expected: null,
      },
      {
        name: 'plain text',
        contentType: 'text/plain',
        body: 'plain response',
        expected: 'plain response',
      },
    ].forEach(({ name, contentType, body, expected }) => {
      it(`parses ${name}`, async () => {
        const { promise, request } = xhrRequest('GET', { url: '/example' });

        request.respond({ body, headers: { 'Content-Type': contentType } });

        await expectAsync(promise).toBeResolvedTo(expected);
      });
    });

    it('rejects HTTP failures with parsed response data', async () => {
      const { promise, request } = xhrRequest('POST', { url: '/example' });
      request.respond({
        status: 422,
        statusText: 'Unprocessable Entity',
        body: '{"message":"invalid"}',
        headers: { 'Content-Type': 'application/json' },
      });

      try {
        await promise;
        fail('Expected request to reject');
      } catch (error) {
        expect(error).toEqual({
          status: 422,
          statusText: 'Unprocessable Entity',
          data: { message: 'invalid' },
        });
      }
      expect(reauthenticateUser).not.toHaveBeenCalled();
    });

    it('refreshes an IAP session and retries the original request with its complete config', async () => {
      SETTINGS.feature.iapRefresherEnabled = true;
      const config = {
        url: '/example',
        data: { value: 'request' },
        headers: { 'X-Custom': 'caller' },
        params: { query: 'preserved' },
        timeout: 1234,
      };
      const { promise, request } = xhrRequest('POST', config);
      request.respond({
        status: 401,
        statusText: 'Unauthorized',
        body: '{"message":"IAP session expired"}',
        headers: { 'Content-Type': 'application/json' },
      });

      const refresh = await waitForRequest(1);
      expect(refresh.method).toBe('GET');
      expect(refresh.url).toBe(`${window.location.origin}/_gcp_iap/do_session_refresh`);
      expect(refresh.withCredentials).toBe(true);
      refresh.respond({ status: 204 });

      const retry = await waitForRequest(2);
      expect(retry.method).toBe('POST');
      expect(retry.url).toBe(`${window.location.origin}/example?query=preserved`);
      expect(retry.explicitHeaderValues('X-Custom')).toEqual(['caller']);
      expect(retry.explicitHeaderValues('Content-Type')).toEqual(['application/json;charset=utf-8']);
      expect(retry.requestBody).toBe(JSON.stringify(config.data));
      expect(retry.timeout).toBe(1234);
      expect(retry.withCredentials).toBe(true);
      retry.respond({ body: '{"value":"retried"}', headers: { 'Content-Type': 'application/json' } });

      await expectAsync(promise).toBeResolvedTo({ value: 'retried' });
      expect(reauthenticateUser).not.toHaveBeenCalled();
    });

    it('retries with the prepared URL, JSON body, headers, timeout, and credentials after config mutation', async () => {
      SETTINGS.feature.iapRefresherEnabled = true;
      const data = { value: 'original' };
      const headers = { 'X-Custom': 'original' };
      const params = { query: 'original' };
      const config = { url: '/original', data, headers, params, timeout: 1234 };
      const original = xhrRequest('POST', config);
      original.request.respond({ status: 401 });
      const refresh = await waitForRequest(1);

      config.url = '/mutated';
      data.value = 'mutated';
      headers['X-Custom'] = 'mutated';
      params.query = 'mutated';
      config.timeout = 9999;
      refresh.respond({ status: 204 });

      const retry = await waitForRequest(2);
      expect(retry.method).toBe(original.request.method);
      expect(retry.url).toBe(original.request.url);
      expect(retry.requestBody).toBe(original.request.requestBody);
      expect(retry.explicitRequestHeaders).toEqual(original.request.explicitRequestHeaders);
      expect(retry.timeout).toBe(original.request.timeout);
      expect(retry.withCredentials).toBe(original.request.withCredentials);
      retry.respond({ body: 'retried', headers: { 'Content-Type': 'text/plain' } });

      await expectAsync(original.promise).toBeResolvedTo('retried');
    });

    it('reuses the exact FormData body on retry; callers must not mutate native bodies after dispatch', async () => {
      SETTINGS.feature.iapRefresherEnabled = true;
      const data = new FormData();
      data.append('value', 'original');
      const original = xhrRequest('POST', { url: '/form', data });
      original.request.respond({ status: 401 });
      const refresh = await waitForRequest(1);
      refresh.respond({ status: 204 });

      const retry = await waitForRequest(2);
      expect(original.request.requestBody).toBe(data);
      expect(retry.requestBody).toBe(data);
      retry.respond({ body: 'retried', headers: { 'Content-Type': 'text/plain' } });

      await expectAsync(original.promise).toBeResolvedTo('retried');
    });

    it('shares one refresh across concurrent IAP failures and retries each request once', async () => {
      SETTINGS.feature.iapRefresherEnabled = true;
      const first = xhrRequest('GET', { url: '/first' });
      const second = xhrRequest('GET', { url: '/second' });
      first.request.respond({
        status: 401,
        body: '{"request":"first"}',
        headers: { 'Content-Type': 'application/json' },
      });
      second.request.respond({
        status: 401,
        body: '{"request":"second"}',
        headers: { 'Content-Type': 'application/json' },
      });

      const refresh = await waitForRequest(2);
      expect(FakeXMLHttpRequest.instances.length).toBe(3);
      refresh.respond({ status: 204 });

      const secondRetry = await waitForRequest(4);
      const retries = FakeXMLHttpRequest.instances.slice(3);
      expect(retries.map(({ url }) => url).sort()).toEqual(
        [`${window.location.origin}/first`, `${window.location.origin}/second`].sort(),
      );
      retries.forEach((retry) => {
        retry.respond({
          body: JSON.stringify({ url: retry.url }),
          headers: { 'Content-Type': 'application/json' },
        });
      });

      await expectAsync(first.promise).toBeResolvedTo({ url: `${window.location.origin}/first` });
      await expectAsync(second.promise).toBeResolvedTo({ url: `${window.location.origin}/second` });
      expect(secondRetry).toBeDefined();
      expect(reauthenticateUser).not.toHaveBeenCalled();
    });

    it('retries a staggered old-generation 401 without starting a second refresh', async () => {
      SETTINGS.feature.iapRefresherEnabled = true;
      const first = xhrRequest('GET', { url: '/first' });
      const second = xhrRequest('GET', { url: '/second' });

      first.request.respond({ status: 401 });
      const refresh = await waitForRequest(2);
      refresh.respond({ status: 204 });
      const firstRetry = await waitForRequest(3);
      firstRetry.respond({ body: 'first retried', headers: { 'Content-Type': 'text/plain' } });
      await expectAsync(first.promise).toBeResolvedTo('first retried');

      second.request.respond({ status: 401 });
      const secondRetry = await waitForRequest(4);
      expect(secondRetry.url).toBe(`${window.location.origin}/second`);
      expect(
        FakeXMLHttpRequest.instances.filter(({ url }) => url.endsWith('/_gcp_iap/do_session_refresh')).length,
      ).toBe(1);
      secondRetry.respond({ body: 'second retried', headers: { 'Content-Type': 'text/plain' } });

      await expectAsync(second.promise).toBeResolvedTo('second retried');
    });

    it('rejects concurrent requests with their own original parsed 401 when the shared refresh fails', async () => {
      SETTINGS.feature.iapRefresherEnabled = true;
      const first = xhrRequest('GET', { url: '/first' });
      const second = xhrRequest('GET', { url: '/second' });
      first.request.respond({
        status: 401,
        statusText: 'First Unauthorized',
        body: '{"request":"first"}',
        headers: { 'Content-Type': 'application/json' },
      });
      second.request.respond({
        status: 401,
        statusText: 'Second Unauthorized',
        body: '{"request":"second"}',
        headers: { 'Content-Type': 'application/json' },
      });

      const refresh = await waitForRequest(2);
      expect(FakeXMLHttpRequest.instances.length).toBe(3);
      refresh.respond({ status: 401, body: '{"refresh":"failed"}', headers: { 'Content-Type': 'application/json' } });

      await expectAsync(first.promise).toBeRejectedWith({
        status: 401,
        statusText: 'First Unauthorized',
        data: { request: 'first' },
      });
      await expectAsync(second.promise).toBeRejectedWith({
        status: 401,
        statusText: 'Second Unauthorized',
        data: { request: 'second' },
      });
      expect(FakeXMLHttpRequest.instances.length).toBe(3);
      expect(reauthenticateUser).not.toHaveBeenCalled();
    });

    it('clears a failed refresh so a later request can refresh and retry', async () => {
      SETTINGS.feature.iapRefresherEnabled = true;
      const first = xhrRequest('GET', { url: '/first' });
      const second = xhrRequest('GET', { url: '/second' });
      first.request.respond({ status: 401 });
      const failedRefresh = await waitForRequest(2);
      failedRefresh.respond({ status: 500 });
      await expectAsync(first.promise).toBeRejectedWith({ status: 401, statusText: '', data: '' });

      second.request.respond({ status: 401 });
      const successfulRefresh = await waitForRequest(3);
      expect(successfulRefresh.url).toBe(`${window.location.origin}/_gcp_iap/do_session_refresh`);
      successfulRefresh.respond({ status: 204 });
      const retry = await waitForRequest(4);
      retry.respond({ body: 'retried', headers: { 'Content-Type': 'text/plain' } });

      await expectAsync(second.promise).toBeResolvedTo('retried');
      expect(reauthenticateUser).not.toHaveBeenCalled();
    });

    it('surfaces a failed retry without starting another refresh', async () => {
      SETTINGS.feature.iapRefresherEnabled = true;
      const original = xhrRequest('GET', { url: '/example' });
      original.request.respond({ status: 401, body: '{"attempt":1}', headers: { 'Content-Type': 'application/json' } });
      const refresh = await waitForRequest(1);
      refresh.respond({ status: 204 });
      const retry = await waitForRequest(2);
      retry.respond({
        status: 401,
        statusText: 'Still Unauthorized',
        body: '{"attempt":2}',
        headers: { 'Content-Type': 'application/json' },
      });

      await expectAsync(original.promise).toBeRejectedWith({
        status: 401,
        statusText: 'Still Unauthorized',
        data: { attempt: 2 },
      });
      expect(FakeXMLHttpRequest.instances.length).toBe(3);
      expect(reauthenticateUser).not.toHaveBeenCalled();
    });

    it('preserves a non-IAP 401 response without reauthenticating', async () => {
      SETTINGS.feature.iapRefresherEnabled = false;
      const { promise, request } = xhrRequest('GET', { url: '/example' });
      request.respond({
        status: 401,
        statusText: 'Unauthorized',
        body: '{"message":"not permitted"}',
        headers: { 'Content-Type': 'application/json' },
      });

      await expectAsync(promise).toBeRejectedWith({
        status: 401,
        statusText: 'Unauthorized',
        data: { message: 'not permitted' },
      });
      expect(reauthenticateUser).not.toHaveBeenCalled();
    });

    it('rejects malformed JSON through the promise without throwing from the XHR callback', async () => {
      const { promise, request } = xhrRequest('GET', { url: '/example' });

      try {
        request.respond({ body: '{malformed', headers: { 'Content-Type': 'application/json' } });
      } catch (error) {
        fail(`Expected the parse error to reject the promise, but the callback threw ${error}`);
        return;
      }

      await expectAsync(promise).toBeRejected();
      expect(reauthenticateUser).not.toHaveBeenCalled();
    });

    it('reauthenticates once before preserving invalid HTML content rejection', async () => {
      const { promise, request } = xhrRequest('GET', { url: '/example' });
      request.respond({ body: '  <html>Sign in</html>', headers: { 'Content-Type': 'text/html' } });

      await expectAsync(promise).toBeRejectedWithError(
        Error,
        `${invalidContentMessage}: ${window.location.origin}/example`,
      );
      expect(reauthenticateUser).toHaveBeenCalledTimes(1);
    });

    it('reauthenticates once for each invalid HTML response across repeated requests', async () => {
      const first = xhrRequest('GET', { url: '/first' });
      const second = xhrRequest('GET', { url: '/second' });

      first.request.respond({ body: '<html>Sign in</html>', headers: { 'Content-Type': 'text/html' } });
      second.request.respond({ body: '<html>Sign in</html>', headers: { 'Content-Type': 'text/html' } });

      await expectAsync(first.promise).toBeRejected();
      await expectAsync(second.promise).toBeRejected();
      expect(reauthenticateUser).toHaveBeenCalledTimes(2);
    });

    it('preserves network error rejection', async () => {
      const { promise, request } = xhrRequest('PATCH', { url: '/example' });
      request.failRequest();

      await expectAsync(promise).toBeRejectedWithError(
        Error,
        `Failed to load resource: PATCH ${window.location.origin}/example`,
      );
      expect(reauthenticateUser).not.toHaveBeenCalled();
    });

    it('propagates the request timeout and rejects timed out requests with the AngularJS response shape', async () => {
      const { promise, request } = xhrRequest('DELETE', { url: '/example', timeout: 1234 });

      expect(request.timeout).toBe(1234);
      request.timeOut();

      try {
        await promise;
        fail('Expected request to reject');
      } catch (error) {
        expect(error).toEqual({
          status: -1,
          statusText: '',
          xhrStatus: 'timeout',
          data: null,
        });
      }
      expect(reauthenticateUser).not.toHaveBeenCalled();
    });
  });
});
