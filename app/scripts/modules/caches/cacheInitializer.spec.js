'use strict';

describe('Service: cacheInitializer', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var cacheInitializer;

  beforeEach(
    module('spinnaker.caches.initializer')
  );

  beforeEach(
    inject(function (_cacheInitializer_) {
      cacheInitializer = _cacheInitializer_;
    })
  );

  it('should instantiate the controller', function () {
    expect(cacheInitializer).toBeDefined();
  });
});


