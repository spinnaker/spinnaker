import { IAsg, IServerGroup } from 'core/domain';
import { ICapacity } from 'core/serverGroup';
import { mockInstance, mockInstanceCounts, mockMoniker } from 'core/mocks';

export const mockAsg: IAsg = {
  minSize: 2,
  maxSize: 2,
  desiredCapacity: 2,
  tags: [],
};

export const mockCapacity: ICapacity = {
  min: 1,
  max: 1,
  desired: 1,
};

export const mockServerGroup: IServerGroup = {
  account: 'test',
  app: 'deck',
  buildInfo: {
    version: '2.1140.0',
    commit: '1234567',
    jenkins: {
      name: 'SPINNAKER-deck',
      number: '3590',
      host: 'https://host.net/',
    },
  },
  capacity: mockCapacity,
  category: 'test',
  cloudProvider: 'aws',
  cluster: 'deck-test',
  createdTime: 1578697860003,
  detachedInstances: [],
  instanceCounts: mockInstanceCounts,
  instances: [mockInstance],
  instanceType: 'm5.large',
  isDisabled: false,
  loadBalancers: ['lb-123'],
  moniker: mockMoniker,
  name: 'deck-test-123',
  region: 'us-west-2',
  runningExecutions: [],
  runningTasks: [],
  securityGroups: ['sg-12345678', 'sg-abcdefgh'],
  serverGroupManagers: [],
  stack: 'test',
  subnetType: 'ipv4',
  type: 'aws',
  vpcId: 'vpc-1234abcd',
  vpcName: 'test-vpc',
};
