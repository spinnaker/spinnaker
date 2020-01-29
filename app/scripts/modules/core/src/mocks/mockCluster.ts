import { ICluster } from 'core/domain';
import { mockServerGroup } from 'core/mocks';

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
