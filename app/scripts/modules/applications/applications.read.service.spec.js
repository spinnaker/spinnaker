'use strict';

describe('Service: applicationReader', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var applicationReader;

  beforeEach(
    window.module(
      require('./applications.read.service')
    )
  );

  beforeEach(
    window.inject(function (_applicationReader_) {
      applicationReader = _applicationReader_;
    })
  );

  it('should instantiate the controller', function () {
    expect(applicationReader).toBeDefined();
  });
});


