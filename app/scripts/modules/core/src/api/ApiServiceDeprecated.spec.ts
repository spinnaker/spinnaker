import Spy = jasmine.Spy;
import { mock, noop } from 'angular';
import { AuthenticationInitializer } from '../authentication/AuthenticationInitializer';
import { ICache } from '../cache';
import { API, InvalidAPIResponse, invalidContentMessage } from './ApiService';
import { SETTINGS } from '../config/settings';

describe('API Service', function () {
  let $httpBackend: ng.IHttpBackendService;
  let baseUrl: string;

  beforeEach(
    mock.inject(function (_$httpBackend_: ng.IHttpBackendService) {
      $httpBackend = _$httpBackend_;
      baseUrl = API.baseUrl;
    }),
  );

  afterEach(function () {
    SETTINGS.resetToOriginal();
    $httpBackend.verifyNoOutstandingExpectation();
    $httpBackend.verifyNoOutstandingRequest();
  });

  describe('ensure api requests generate normalized urls', function () {
    let expected: any;
    beforeEach(() => {
      expected = {
        url: '',
      };
    });

    it('trims leading slashes from urls', function () {
      const result = API.one('/foo');
      expected.url = `foo`;
      expect(result.config).toEqual(jasmine.objectContaining(expected));
    });

    it('trims repeated leading slashes from urls', function () {
      const result = API.one('/////foo');
      expected.url = `foo`;
      expect(result.config).toEqual(jasmine.objectContaining(expected));
    });

    it('trims trailing slashes from baseUrl', function () {
      SETTINGS.gateUrl = 'http://localhost/';
      expect(API.baseUrl).toEqual('http://localhost');
    });

    it('trims repeated trailing slashes from baseUrl', function () {
      SETTINGS.gateUrl = 'http://localhost/////';
      expect(API.baseUrl).toEqual('http://localhost');
    });
  });

  describe('validate response content-type header', function () {
    it('responses with non-"application/json" content types should trigger a reauthentication request and reject', function () {
      spyOn(AuthenticationInitializer, 'reauthenticateUser').and.callFake(noop);
      $httpBackend
        .expectGET(`${baseUrl}/bad`)
        .respond(200, '<html>this is the authentication page</html>', { 'content-type': 'text/html' });

      let rejected = false;
      API.one('bad')
        .get()
        .then(noop, () => (rejected = true));

      $httpBackend.flush();
      expect((AuthenticationInitializer.reauthenticateUser as Spy).calls.count()).toBe(1);
      expect(rejected).toBe(true);
    });

    it('application/foo+json is fine', () => {
      spyOn(AuthenticationInitializer, 'reauthenticateUser').and.callFake(noop);
      $httpBackend
        .expectGET(`${baseUrl}/bad`)
        .respond(200, '{"good":"job"}', { 'content-type': 'application/foo+json' });

      let rejected = false;
      API.one('bad')
        .get()
        .then(noop, () => (rejected = true));

      $httpBackend.flush();
      expect((AuthenticationInitializer.reauthenticateUser as Spy).calls.count()).toBe(0);
      expect(rejected).toBe(false);
    });

    it('application/x-yaml;charset=utf-8 is fine, too', () => {
      spyOn(AuthenticationInitializer, 'reauthenticateUser').and.callFake(noop);
      $httpBackend
        .expectGET(`${baseUrl}/yaml`)
        .respond(200, '---\nfoo: bar', { 'content-type': 'application/x-yaml;charset=utf-8' });

      let rejected = false;
      API.one('yaml')
        .get()
        .then(noop, () => (rejected = true));

      $httpBackend.flush();
      expect((AuthenticationInitializer.reauthenticateUser as Spy).calls.count()).toBe(0);
      expect(rejected).toBe(false);
    });

    it('string responses starting with <html should trigger a reauthentication request and reject', function () {
      spyOn(AuthenticationInitializer, 'reauthenticateUser').and.callFake(noop);
      $httpBackend.expectGET(`${baseUrl}/fine`).respond(200, 'this is fine');

      let rejected = false;
      let succeeded = false;
      API.one('fine')
        .get()
        .then(
          () => (succeeded = true),
          () => (rejected = true),
        );

      $httpBackend.flush();
      expect((AuthenticationInitializer.reauthenticateUser as Spy).calls.count()).toBe(0);
      expect(rejected).toBe(false);
      expect(succeeded).toBe(true);
    });

    it('object and array responses should pass through', function () {
      spyOn(AuthenticationInitializer, 'reauthenticateUser').and.callFake(noop);

      let rejected = false;
      let succeeded = false;
      $httpBackend.expectGET(`${baseUrl}/some-array`).respond(200, []);
      API.one('some-array')
        .get()
        .then(
          () => (succeeded = true),
          () => (rejected = true),
        );
      $httpBackend.flush();

      expect((AuthenticationInitializer.reauthenticateUser as Spy).calls.count()).toBe(0);
      expect(rejected).toBe(false);
      expect(succeeded).toBe(true);

      // verify object responses
      rejected = false;
      succeeded = false;
      $httpBackend.expectGET(`${baseUrl}/some-object`).respond(200, {});
      API.one('some-object')
        .get()
        .then(
          () => (succeeded = true),
          () => (rejected = true),
        );
      $httpBackend.flush();

      expect((AuthenticationInitializer.reauthenticateUser as Spy).calls.count()).toBe(0);
      expect(rejected).toBe(false);
      expect(succeeded).toBe(true);
    });

    it('rejects the request promise with an error when content mismatch occurs', () => {
      spyOn(AuthenticationInitializer, 'reauthenticateUser').and.callFake(noop);
      $httpBackend
        .expectGET(`${baseUrl}/bad`)
        .respond(200, '<html>this is the authentication page</html>', { 'content-type': 'text/html' });

      let err: any;
      API.one('bad')
        .get()
        .catch((e: any) => (err = e));

      $httpBackend.flush();
      expect(err instanceof InvalidAPIResponse).toBeTruthy();
    });

    it('returns a string error message in the format expected by UI components when content mismatch occurs', () => {
      spyOn(AuthenticationInitializer, 'reauthenticateUser').and.callFake(noop);
      $httpBackend
        .expectGET(`${baseUrl}/bad`)
        .respond(200, '<html>this is the authentication page</html>', { 'content-type': 'text/html' });

      let message = '';
      API.one('bad')
        .get()
        .catch((err: any) => (message = err.data.message));

      $httpBackend.flush();
      expect(message).toBe(invalidContentMessage);
    });

    it('returns a copy of the original response when content mismatch occurs', () => {
      spyOn(AuthenticationInitializer, 'reauthenticateUser').and.callFake(noop);
      const serverResult = { foo: 'bar' };
      $httpBackend.expectGET(`${baseUrl}/bad`).respond(200, serverResult, { 'content-type': 'foobar/json' });

      let receivedResult = null;
      API.one('bad')
        .get()
        .catch((err: any) => (receivedResult = err.originalResult.data));

      $httpBackend.flush();
      expect(receivedResult).toEqual(serverResult);
    });
  });

  describe('creating the config and testing the chaining functions without parameters', () => {
    let expected: any;
    beforeEach(() => {
      expected = {
        url: '',
      };
    });

    describe('creating the config with "one" function', function () {
      // it('missing url should create a default config with the base url', function () {
      //   const result = API.one();
      //   expected.url = baseUrl;
      //   expect(result.config).toEqual(jasmine.objectContaining(expected));
      // });

      it('single url should create a default config with the base url', function () {
        const result = API.one('foo');
        expected.url = `foo`;
        expect(result.config).toEqual(jasmine.objectContaining(expected));
      });

      it('multiple calls to "one" should create a default config with the base url and build out the url', function () {
        const result = API.one('foo').one('bar');
        expected.url = `foo/bar`;
        expect(result.config).toEqual(jasmine.objectContaining(expected));
      });

      it('should allow for multiple urls to be added to the url', function () {
        const result = API.one('foo', 'bar');
        expected.url = `foo/bar`;
        expect(result.config).toEqual(jasmine.objectContaining(expected));
      });
    });

    describe('creating the  config with "all" function', function () {
      // it('missing url should create a default config with the base url', function () {
      //   const result = API.all();
      //   expected.url = '';
      //   expect(result.config).toEqual(jasmine.objectContaining(expected));
      // });

      it('single url should create a default config with the base url', function () {
        const result = API.all('foo');
        expected.url = `foo`;
        expect(result.config).toEqual(jasmine.objectContaining(expected));
      });

      it('multiple calls to "all" should create a default config with the base url and build out the url', function () {
        const result = API.all('foo').all('bar');
        expected.url = `foo/bar`;
        expect(result.config).toEqual(jasmine.objectContaining(expected));
      });

      it('should allow for multiple urls to be added to the url', function () {
        const result = API.all('foo', 'bar');
        expected.url = `foo/bar`;
        expect(result.config).toEqual(jasmine.objectContaining(expected));
      });
    });

    describe('creating the  config with mix of "one" and "all" function', function () {
      it('single url should create a default config with the base url', function () {
        const result = API.all('foo').one('bar');
        expected.url = `foo/bar`;
        expect(result.config).toEqual(jasmine.objectContaining(expected));
      });

      it('multiple calls to "all" should create a default config with the base url and build out the url', function () {
        const result = API.one('foo').all('bar');
        expected.url = `foo/bar`;
        expect(result.config).toEqual(jasmine.objectContaining(expected));
      });

      it('should allow for multiple urls to be added to the url', function () {
        const result = API.all('foo', 'bar').one('baz');
        expected.url = `foo/bar/baz`;
        expect(result.config).toEqual(jasmine.objectContaining(expected));
      });
    });

    describe('creating multiple endpoints', function () {
      it('should not stomp on each other', function () {
        const first = API.one('bar');
        const second = API.one('foo');

        expect(first.config).toEqual(jasmine.objectContaining({ url: `bar` }));
        expect(second.config).toEqual(jasmine.objectContaining({ url: `foo` }));
      });
    });

    describe('create config with data', function () {
      it('should add data to the config if data object passed', function () {
        const data = { bar: 'baz' };
        const result = API.one('foo').data(data);
        expected.url = `foo`;
        expected.data = data;
        expect(result.config).toEqual(jasmine.objectContaining(expected));
      });
    });
  });

  describe('create a config with params', function () {
    it('when params are provided', function () {
      const result = API.one('foo').withParams({ one: 1 });
      expect(result.config).toEqual(jasmine.objectContaining({ url: `foo`, params: { one: 1 } }));
    });
  });

  describe('useCache()', function () {
    it('should set cache to "true" if no params set', function () {
      const result = API.one('foo').useCache();
      expect(result.config.cache).toBe(true);
    });

    it('should set cache to "false" if explicitly  set', function () {
      const result = API.one('foo').useCache(false);
      expect(result.config.cache).toBe(false);
    });

    it('should set cache to cache object if explicitly set', function () {
      const cacheObj = ({ count: 1 } as unknown) as ICache;
      const result = API.one('foo').useCache(cacheObj);
      expect(result.config.cache).toBe(cacheObj as any);
    });
  });

  describe('get(): create a url with a "GET" method', function () {
    it('should create the url and issue a get request with the "one" function', function () {
      $httpBackend.expectGET(`${baseUrl}/foo`).respond(200);

      API.one('foo').get();

      $httpBackend.flush();
    });

    it('should create the url and issue a get request with the "all" function', function () {
      $httpBackend.expectGET(`${baseUrl}/foo/bar`).respond(200);

      API.all('foo', 'bar').get();

      $httpBackend.flush();
    });

    it('should take a param object with one param', function () {
      $httpBackend.expectGET(`${baseUrl}/foo/bar?param1=2`).respond(200);

      API.one('foo', 'bar').get({ param1: 2 });

      $httpBackend.flush();
    });

    it('should take a param object with multiple params', function () {
      $httpBackend.expectGET(`${baseUrl}/foo/bar?param1=2&param2=foo`).respond(200);

      API.one('foo', 'bar').get({ param1: 2, param2: 'foo' });

      $httpBackend.flush();
    });
  });

  describe('getList(): create a url with a "GET" method', function () {
    it('should create the url and issue a get request with the "one" function', function () {
      $httpBackend.expectGET(`${baseUrl}/foo`).respond(200);

      API.one('foo').getList();

      $httpBackend.flush();
    });

    it('should create the url and issue a get request with the "all" function', function () {
      $httpBackend.expectGET(`${baseUrl}/foo/bar`).respond(200);

      API.all('foo', 'bar').getList();

      $httpBackend.flush();
    });

    it('should take a param object with one param', function () {
      $httpBackend.expectGET(`${baseUrl}/foo/bar?param1=2`).respond(200);

      API.one('foo', 'bar').getList({ param1: 2 });

      $httpBackend.flush();
    });

    it('should take a param object with multiple params', function () {
      $httpBackend.expectGET(`${baseUrl}/foo/bar?param1=2&param2=foo`).respond(200);

      API.one('foo', 'bar').getList({ param1: 2, param2: 'foo' });

      $httpBackend.flush();
    });
  });

  describe('post(): create a url with a "POST" method', function () {
    it('should create the url and make a POST call', function () {
      $httpBackend.expectPOST(`${baseUrl}/foo`).respond(200);

      API.one('foo').post();

      $httpBackend.flush();
    });

    it('should create the url and POST with data', function () {
      const data = { bar: 7 };
      $httpBackend.expectPOST(`${baseUrl}/foo`, data).respond(200);

      API.one('foo').post(data);

      $httpBackend.flush();
    });
  });

  describe('put(): create a url with a "PUT" method', function () {
    it('should create the url and make a POST call', function () {
      $httpBackend.expectPUT(`${baseUrl}/foo`).respond(200);

      API.one('foo').put();

      $httpBackend.flush();
    });

    it('should create the url and PUT with data', function () {
      const data = { bar: 7 };
      $httpBackend.expectPUT(`${baseUrl}/foo`, data).respond(200);

      API.one('foo').put(data);

      $httpBackend.flush();
    });
  });

  describe('remove(): create a url with a "DELETE" method', function () {
    it('should create the url and make a DELETE call', function () {
      $httpBackend.expectDELETE(`${baseUrl}/foo`).respond(200);

      API.one('foo').remove();

      $httpBackend.flush();
    });

    it('should create the url with params and  make a DELETE call', function () {
      const params = { bar: 7 };
      $httpBackend.expectDELETE(`${baseUrl}/foo?bar=7`).respond(200);

      API.one('foo').query(params).remove();

      $httpBackend.flush();
    });
  });
});
