import type { IAmazonAsg, IAmazonLaunchTemplate, IAmazonServerGroup, ISuspendedProcess } from '@spinnaker/amazon';

import { mockLaunchTemplate } from './mockAmazonLaunchTemplate';
import { mockInstanceCounts } from './../mockInstanceCounts';

export const mockAmazonAsg: IAmazonAsg = {
  minSize: 2,
  maxSize: 2,
  desiredCapacity: 2,
  tags: [],
  availabilityZones: ['a', 'b', 'c'],
  vpczoneIdentifier: '',
  enabledMetrics: [],
  suspendedProcesses: [],
  defaultCooldown: 300,
  healthCheckType: 'EC2',
  healthCheckGracePeriod: 0,
  terminationPolicies: ['Default'],
};

export const createMockAmazonAsg = (
  tags?: any[],
  azs?: string[],
  suspendedProcesses?: ISuspendedProcess[],
): IAmazonAsg => ({
  ...mockAmazonAsg,
  tags: tags || [],
  availabilityZones: azs || ['a', 'b', 'c'],
  suspendedProcesses: suspendedProcesses || [],
});

export const createMockAmazonServerGroupWithLc = (
  launchConfig?: any,
  tags?: any,
  asg?: IAmazonAsg,
): IAmazonServerGroup => ({
  account: 'test',
  app: 'deck',
  tags: tags ? tags : [],
  category: 'test',
  cloudProvider: 'aws',
  cluster: 'deck-test',
  createdTime: 1578697860003,
  instances: [],
  instanceType: 't3.micro',
  loadBalancers: ['lb-123'],
  name: 'deck-test-123',
  region: 'us-west-2',
  securityGroups: ['sg-abcdefgh'],
  stack: 'test',
  subnetType: 'ipv4',
  type: 'aws',
  vpcId: 'vpc-1234abcd',
  vpcName: 'test-vpc',
  instanceCounts: mockInstanceCounts,

  image: 'test-image',
  targetGroups: [],
  asg: asg || mockAmazonAsg,
  launchConfig: launchConfig,
});

export const createMockAmazonServerGroupWithLt = (lt?: IAmazonLaunchTemplate): IAmazonServerGroup => ({
  account: 'test',
  app: 'deck',
  category: 'test',
  cloudProvider: 'aws',
  cluster: 'deck-test',
  createdTime: 1578697860003,
  instances: [],
  instanceType: 't3.micro',
  loadBalancers: ['lb-123'],
  name: 'deck-test-123',
  region: 'us-west-2',
  securityGroups: ['sg-abcdefgh'],
  stack: 'test',
  subnetType: 'ipv4',
  type: 'aws',
  vpcId: 'vpc-1234abcd',
  vpcName: 'test-vpc',
  instanceCounts: mockInstanceCounts,

  image: 'test-image',
  targetGroups: [],
  asg: mockAmazonAsg,
  launchTemplate: lt || mockLaunchTemplate,
});
