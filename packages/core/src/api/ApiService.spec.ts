import { IHttpClientImplementation } from './ApiService';
import { SETTINGS } from '../config/settings';
import { makeRequestBuilderConfig, RequestBuilder } from './ApiService';

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
});
