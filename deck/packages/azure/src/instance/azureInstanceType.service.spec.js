import { AzureInstanceTypeService } from './azureInstanceType.service';

describe('Service: InstanceType', function () {
  it('instantiates the service directly', function () {
    expect(new AzureInstanceTypeService()).toBeDefined();
  });
});
