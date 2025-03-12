import type { ISubnet } from '@spinnaker/core';

export const mockSubnet: ISubnet = {
  availabilityZone: 'us-west-1b',
  id: 'subnet-1234',
  name: 'test-subnet',
  account: 'test',
  region: 'us-west-1',
  type: 'aws',
  label: 'label',
  purpose: 'testing subnets',
  deprecated: false,
  vpcId: 'vpc-1234',
};
