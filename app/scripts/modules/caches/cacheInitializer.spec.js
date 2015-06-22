'use strict';

describe('Service: cacheInitializer', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var cacheInitializer;

  beforeEach(
    window.module(
      require('./cacheInitializer')
    )
  );

  beforeEach(
    window.inject(function (_cacheInitializer_) {
      cacheInitializer = _cacheInitializer_;
    })
  );

  it('should instantiate the controller', function () {
    expect(cacheInitializer).toBeDefined();
  });
});


