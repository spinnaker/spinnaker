'use strict';

describe('dcosServerGroupConfigurationService', function() {

  var service;

  beforeEach(
    window.module(
      require('./configuration.service.js')
    )
  );

  beforeEach(window.inject(function (_dcosServerGroupConfigurationService_) {
    service = _dcosServerGroupConfigurationService_;
  }));

  describe('buildImageId', function () {

    it('buildImageId spec 1', function () {
      var image = {
        fromContext: true,
        cluster: 'dcos-test-test',
        pattern: '',
      };

      var expected = 'dcos-test-test ';
      var result = service.buildImageId(image);

      expect(result).toEqual(expected);
    });

    it('buildImageId spec 2', function () {
      var image = {
        fromTrigger: true,
        registry: 'test-registry.com',
        repository: 'test-repo',
      };

      var expected = 'test-registry.com/test-repo (Tag resolved at runtime)';
      var result = service.buildImageId(image);

      expect(result).toEqual(expected);
    });

    it('buildImageId spec 3', function () {
      var image = {
        registry: 'test-registry.com',
        repository: 'test-repo',
        tag: 'latest',
      };

      var expected = 'test-registry.com/test-repo:latest';
      var result = service.buildImageId(image);

      expect(result).toEqual(expected);
    });
  });
});
