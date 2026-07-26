/* eslint-disable @spinnaker/api-deprecation, @spinnaker/api-no-slashes, @spinnaker/migrate-to-mock-http-client */

import { API, InvalidAPIResponse, invalidContentMessage, RequestBuilder } from './ApiService';
import { mockHttpClient } from './mock/jasmine';
import type { MockHttpClient } from './mock/mockHttpClient';
import type { ICache } from '../cache';
import { SETTINGS } from '../config/settings';

describe('API Service', function () {
  let baseUrl: string;
  let http: MockHttpClient;

  beforeEach(() => {
    baseUrl = API.baseUrl;
    http = mockHttpClient();
  });

  afterEach(function () {
    SETTINGS.resetToOriginal();
  });

  it('preserves the established invalid response rejection shape from the transport', async () => {
    const originalResult = { status: 200, statusText: 'OK', data: '<html>Sign in</html>' };
    const rejection = new InvalidAPIResponse(invalidContentMessage, originalResult);
    const transport = jasmine.createSpyObj('transport', ['get', 'post', 'put', 'patch', 'delete']);
    transport.get.and.returnValue(Promise.reject(rejection));
    RequestBuilder.defaultHttpClient = transport;

    try {
      await API.one('bad').get();
      fail('Expected invalid API response rejection');
    } catch (error) {
      expect(error).toBe(rejection);
      expect(error instanceof InvalidAPIResponse).toBe(true);
      expect((error as InvalidAPIResponse).data.message).toBe(invalidContentMessage);
      expect((error as InvalidAPIResponse).originalResult).toBe(originalResult);
    }
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
    it('should create the url and issue a get request with the "one" function', async function () {
      http.expectGET(`${baseUrl}/foo`).respond(200);

      const request = API.one('foo').get();

      await http.flush();
      await request;
    });

    it('should create the url and issue a get request with the "all" function', async function () {
      http.expectGET(`${baseUrl}/foo/bar`).respond(200);

      const request = API.all('foo', 'bar').get();

      await http.flush();
      await request;
    });

    it('should take a param object with one param', async function () {
      http.expectGET(`${baseUrl}/foo/bar`).withParams({ param1: 2 }).respond(200);

      const request = API.one('foo', 'bar').get({ param1: 2 });

      await http.flush();
      await request;
    });

    it('should take a param object with multiple params', async function () {
      http.expectGET(`${baseUrl}/foo/bar`).withParams({ param1: 2, param2: 'foo' }).respond(200);

      const request = API.one('foo', 'bar').get({ param1: 2, param2: 'foo' });

      await http.flush();
      await request;
    });
  });

  describe('getList(): create a url with a "GET" method', function () {
    it('should create the url and issue a get request with the "one" function', async function () {
      http.expectGET(`${baseUrl}/foo`).respond(200);

      const request = API.one('foo').getList();

      await http.flush();
      await request;
    });

    it('should create the url and issue a get request with the "all" function', async function () {
      http.expectGET(`${baseUrl}/foo/bar`).respond(200);

      const request = API.all('foo', 'bar').getList();

      await http.flush();
      await request;
    });

    it('should take a param object with one param', async function () {
      http.expectGET(`${baseUrl}/foo/bar`).withParams({ param1: 2 }).respond(200);

      const request = API.one('foo', 'bar').getList({ param1: 2 });

      await http.flush();
      await request;
    });

    it('should take a param object with multiple params', async function () {
      http.expectGET(`${baseUrl}/foo/bar`).withParams({ param1: 2, param2: 'foo' }).respond(200);

      const request = API.one('foo', 'bar').getList({ param1: 2, param2: 'foo' });

      await http.flush();
      await request;
    });
  });

  describe('post(): create a url with a "POST" method', function () {
    it('should create the url and make a POST call', async function () {
      http.expectPOST(`${baseUrl}/foo`).respond(200);

      const request = API.one('foo').post();

      await http.flush();
      await request;
    });

    it('should create the url and POST with data', async function () {
      const data = { bar: 7 };
      let receivedData: any;
      http
        .expectPOST(`${baseUrl}/foo`)
        .onRequestReceived((request) => (receivedData = request.data))
        .respond(200);

      const request = API.one('foo').post(data);

      await http.flush();
      await request;
      expect(receivedData).toEqual(data);
    });
  });

  describe('put(): create a url with a "PUT" method', function () {
    it('should create the url and make a POST call', async function () {
      http.expectPUT(`${baseUrl}/foo`).respond(200);

      const request = API.one('foo').put();

      await http.flush();
      await request;
    });

    it('should create the url and PUT with data', async function () {
      const data = { bar: 7 };
      let receivedData: any;
      http
        .expectPUT(`${baseUrl}/foo`)
        .onRequestReceived((request) => (receivedData = request.data))
        .respond(200);

      const request = API.one('foo').put(data);

      await http.flush();
      await request;
      expect(receivedData).toEqual(data);
    });
  });

  describe('remove(): create a url with a "DELETE" method', function () {
    it('should create the url and make a DELETE call', async function () {
      http.expectDELETE(`${baseUrl}/foo`).respond(200);

      const request = API.one('foo').remove();

      await http.flush();
      await request;
    });

    it('should create the url with params and  make a DELETE call', async function () {
      const params = { bar: 7 };
      http.expectDELETE(`${baseUrl}/foo`).withParams(params).respond(200);

      const request = API.one('foo').query(params).remove();

      await http.flush();
      await request;
    });
  });
});
