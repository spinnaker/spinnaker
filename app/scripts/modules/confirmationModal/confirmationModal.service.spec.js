'use strict';

describe('Service: confirmationModalService', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var confirmationModalService;

  beforeEach(
    window.module(
      require('./confirmationModal.service.js')
    )
  );

  beforeEach(
    window.inject(function (_confirmationModalService_) {
      confirmationModalService = _confirmationModalService_;
    })
  );

  it('should instantiate the controller', function () {
    expect(confirmationModalService).toBeDefined();
  });

});


