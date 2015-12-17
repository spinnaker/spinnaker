'use strict';

describe('Service: dataConverters ', function () {

  var service;

  beforeEach(
    window.module(
      require('./dataConverter.service')
    )
  );

  beforeEach(window.inject(function(dataConverterService) {
    service = dataConverterService;
  }));

  describe('keyValueToEqualList', function() {

    it ('returns an empty string for null objects', function () {
      expect(service.keyValueToEqualList(null)).toEqual('');
    });

    it ('returns an empty string for empty objects', function () {
      expect(service.keyValueToEqualList({})).toEqual('');
    });

    it ('converts text and numeric values', function () {
      expect(service.keyValueToEqualList({a: 2, b: 'c'})).toEqual('a=2\nb=c');
    });
  });

  describe('equalListToKeyValue', function() {

    it ('returns an empty object for null strings', function () {
      expect(service.equalListToKeyValue(null)).toEqual({});
    });

    it ('returns an empty object for blank strings', function () {
      expect(service.equalListToKeyValue('')).toEqual({});
    });

    it ('converts text and numeric values to strings', function () {
      expect(service.equalListToKeyValue('a=2\nb=c')).toEqual({a: '2', b: 'c'});
    });
  });

});
