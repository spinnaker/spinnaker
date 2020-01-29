import { ISecurityGroup, ILoadBalancerUsage, IServerGroupUsage, IUsages } from 'core/domain';

export const mockLoadBalancerUsage: ILoadBalancerUsage = {
  name: 'deck-test-lb',
};

export const mockServerGroupUsage: IServerGroupUsage = {
  account: 'test',
  cloudProvider: 'aws',
  isDisabled: false,
  name: 'deck-sg',
  region: 'us-west-2',
};

export const mockUsages: IUsages = {
  loadBalancers: [mockLoadBalancerUsage],
  serverGroups: [mockServerGroupUsage, { ...mockServerGroupUsage, name: '2-deck-sg' }],
};

export const mockSecurityGroup: ISecurityGroup = {
  account: 'test',
  accountId: 'sg-1234',
  accountName: 'test',
  application: 'deck-test',
  cloudProvider: 'aws',
  detail: 'lb',
  id: '1234',
  moniker: {
    app: 'deck',
    cluster: 'deck-test',
    stack: 'test',
    sequence: 123,
  },
  name: 'deck-lb-sg',
  provider: 'aws',
  region: 'us-west-2',
  stack: 'datacenter',
  type: 'aws',
  usages: mockUsages,
  vpcId: 'vpc-00112233',
  vpcName: 'vpc0',
};
