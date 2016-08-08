'use strict';

describe('API Service', function () {
  let API;
  let $httpBackend;
  let baseUrl;
  let authenticationInitializer;

  beforeEach(
    window.module(
      require('./api.service'),
      require('../config/settings')
    )
  );

  beforeEach(
    window.inject(function (_API_, _$httpBackend_, settings, _authenticationInitializer_) {
      API = _API_;
      $httpBackend = _$httpBackend_;
      baseUrl = settings.gateUrl;
      authenticationInitializer = _authenticationInitializer_;
    })
  );

  afterEach(function() {
    $httpBackend.verifyNoOutstandingExpectation();
    $httpBackend.verifyNoOutstandingRequest();
  });

  describe('validate response content-type header', function () {
    it('responses with non-"application/json" content types should trigger a reauthentication request and reject', function () {
      spyOn(authenticationInitializer, 'reauthenticateUser').and.callFake(angular.noop);
      $httpBackend.expectGET(`${baseUrl}/bad`).respond(200, '<html>this is the authentication page</html>', {'content-type':'text/html'});

      let rejected = false;
      API.one('bad').get().then(angular.noop, () => rejected = true);

      $httpBackend.flush();
      expect(authenticationInitializer.reauthenticateUser.calls.count()).toBe(1);
      expect(rejected).toBe(true);
    });

    it('string responses starting with <html should trigger a reauthentication request and reject', function () {
      spyOn(authenticationInitializer, 'reauthenticateUser').and.callFake(angular.noop);
      $httpBackend.expectGET(`${baseUrl}/fine`).respond(200, 'this is fine');

      let rejected = false;
      let succeeded = false;
      API.one('fine').get().then(() => succeeded = true, () => rejected = true);

      $httpBackend.flush();
      expect(authenticationInitializer.reauthenticateUser.calls.count()).toBe(0);
      expect(rejected).toBe(false);
      expect(succeeded).toBe(true);
    });

    it('object and array responses should pass through', function () {
      spyOn(authenticationInitializer, 'reauthenticateUser').and.callFake(angular.noop);

      let rejected = false;
      let succeeded = false;
      $httpBackend.expectGET(`${baseUrl}/some-array`).respond(200, []);
      API.one('some-array').get().then(() => succeeded = true, () => rejected = true);
      $httpBackend.flush();

      expect(authenticationInitializer.reauthenticateUser.calls.count()).toBe(0);
      expect(rejected).toBe(false);
      expect(succeeded).toBe(true);

      // verify object responses
      rejected = false;
      succeeded = false;
      $httpBackend.expectGET(`${baseUrl}/some-object`).respond(200, {});
      API.one('some-object').get().then(() => succeeded = true, () => rejected = true);
      $httpBackend.flush();

      expect(authenticationInitializer.reauthenticateUser.calls.count()).toBe(0);
      expect(rejected).toBe(false);
      expect(succeeded).toBe(true);
    });
  });

  describe('creating the config with "one" function', function () {
    it('missing url should create a default config with the base url', function () {
      let result = API.one();
      expect(result.config).toEqual({url: baseUrl});
    });

    it('single url should create a default config with the base url', function () {
      let result = API.one('foo');
      expect(result.config).toEqual({url: `${baseUrl}/foo`});
    });

    it('multiple calls to "one" should create a default config with the base url and build out the url', function () {
      let result = API.one('foo').one('bar');
      expect(result.config).toEqual({url: `${baseUrl}/foo/bar`});
    });

    it('should allow for multiple urls to be added to the url', function () {
      let result = API.one('foo', 'bar');
      expect(result.config).toEqual({url: `${baseUrl}/foo/bar`});
    });
  });

  describe('creating the  config with "all" function', function () {
    it('missing url should create a default config with the base url', function () {
      let result = API.all();
      expect(result.config).toEqual({url: baseUrl});
    });

    it('single url should create a default config with the base url', function () {
      let result = API.all('foo');
      expect(result.config).toEqual({url: `${baseUrl}/foo`});
    });

    it('multiple calls to "all" should create a default config with the base url and build out the url', function () {
      let result = API.all('foo').all('bar');
      expect(result.config).toEqual({url: `${baseUrl}/foo/bar`});
    });

    it('should allow for multiple urls to be added to the url', function () {
      let result = API.all('foo', 'bar');
      expect(result.config).toEqual({url: `${baseUrl}/foo/bar`});
    });
  });

  describe('creating the  config with mix of "one" and "all" function', function () {
    it('single url should create a default config with the base url', function () {
      let result = API.all('foo').one('bar');
      expect(result.config).toEqual({url: `${baseUrl}/foo/bar`});
    });

    it('multiple calls to "all" should create a default config with the base url and build out the url', function () {
      let result = API.one('foo').all('bar');
      expect(result.config).toEqual({url: `${baseUrl}/foo/bar`});
    });

    it('should allow for multiple urls to be added to the url', function () {
      let result = API.all('foo', 'bar').one('baz');
      expect(result.config).toEqual({url: `${baseUrl}/foo/bar/baz`});
    });
  });


  describe('creating multiple endpoints', function () {
    it('should not stomp on each other', function () {
      let first = API.one('bar');
      let second = API.one('foo');

      expect(first.config).toEqual({url: `${baseUrl}/bar`});
      expect(second.config).toEqual({url: `${baseUrl}/foo`});
    });
  });

  describe('create config with data', function () {
    it('should not alter the config if no data object passed', function () {
      let result = API.one('foo').data();
      expect(result.config).toEqual({url: `${baseUrl}/foo`});
    });

    it('should add data to the config if data object passed', function () {
      let data = {bar: 'baz'};
      let result = API.one('foo').data(data);
      expect(result.config).toEqual({url: `${baseUrl}/foo`, data: data});
    });
  });

  describe('create a config with params', function () {
    it('when no params are provided do not alter config', function () {
      let result = API.one('foo').withParams();
      expect(result.config).toEqual({url: `${baseUrl}/foo`});
    });

    it('when params are provided', function () {
      let result = API.one('foo').withParams({one: 1});
      expect(result.config).toEqual({url: `${baseUrl}/foo`, params: {one: 1} });
    });
  });

  describe('useCache()', function () {
    it('should set cache to "true" if no params set', function () {
      let result = API.one('foo').useCache();
      expect(result.config.cache).toBe(true);
    });

    it('should set cache to "false" if explicitly  set', function () {
      let result = API.one('foo').useCache(false);
      expect(result.config.cache).toBe(false);
    });

    it('should set cache to cache object if explicitly set', function () {
      let cacheObj = {count: 1};
      let result = API.one('foo').useCache(cacheObj);
      expect(result.config.cache).toBe(cacheObj);
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

      API.one('foo', 'bar').get({param1: 2});

      $httpBackend.flush();
    });

    it('should take a param object with multiple params', function () {
      $httpBackend.expectGET(`${baseUrl}/foo/bar?param1=2&param2=foo`).respond(200);

      API.one('foo', 'bar').get({param1: 2, param2: 'foo'});

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

      API.one('foo', 'bar').getList({param1: 2});

      $httpBackend.flush();
    });

    it('should take a param object with multiple params', function () {
      $httpBackend.expectGET(`${baseUrl}/foo/bar?param1=2&param2=foo`).respond(200);

      API.one('foo', 'bar').getList({param1: 2, param2: 'foo'});

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
      let data = {bar: 7};
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
      let data = {bar: 7};
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
      let params = {bar: 7};
      $httpBackend.expectDELETE(`${baseUrl}/foo?bar=7`).respond(200);

      API.one('foo').remove(params);

      $httpBackend.flush();
    });


  });
});
