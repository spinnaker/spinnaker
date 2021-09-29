import type { ICluster } from '@spinnaker/core';
import { mockServerGroup } from './mockServerGroup';

export const mockAwsCluster: ICluster = {
  account: 'test',
  application: 'deck',
  category: 'test',
  cloudProvider: 'aws',
  healthCheckType: 'EC2',

  name: 'deck',
  securityGroups: ['sg-123'],
  serverGroups: [mockServerGroup],
};
