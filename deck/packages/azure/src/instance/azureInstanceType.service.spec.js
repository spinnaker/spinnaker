'use strict';

describe('Service: InstanceType', function () {
  beforeEach(function () {
    window.module(require('./azureInstanceType.service').name);
  });

  beforeEach(
    window.inject(function (_azureInstanceTypeService_) {
      this.azureInstanceTypeService = _azureInstanceTypeService_;
    }),
  );

  it('should instantiate the controller', function () {
    expect(this.azureInstanceTypeService).toBeDefined();
  });
});
