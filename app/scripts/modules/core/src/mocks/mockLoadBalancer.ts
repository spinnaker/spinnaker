import { ILoadBalancer } from 'core/domain';
import { mockInstance, mockInstanceCounts, mockMoniker } from 'core/mocks';

export const mockLoadBalancer: ILoadBalancer = {
  account: 'test',
  cloudProvider: 'aws',
  detail: 'testing',
  healthState: 'Up',
  instanceCounts: mockInstanceCounts,
  instances: [mockInstance],
  loadBalancerType: 'application',
  moniker: mockMoniker,
  name: 'deck-test',
  provider: 'aws',
  region: 'us-west-1',
  securityGroups: ['sg-12345678', 'sg-9876543'],
  serverGroups: [],
  stack: 'test',
  type: 'aws',
  vpcId: 'vpc-123',
  vpcName: 'test=vpc',
};
