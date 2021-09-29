import type { IInstance } from '@spinnaker/core';
import { mockHealth } from './mockHealth';

export const mockInstance: IInstance = {
  account: 'test',
  availabilityZone: 'us-west-2a',
  cloudProvider: 'aws',
  hasHealthStatus: true,
  health: [mockHealth],
  healthState: 'Up',
  id: '1-123abc',
  launchTime: 1233457896,
  name: 'test-instance',
  vpcId: 'vpc-123',
  zone: 'us-west-2a',
};
