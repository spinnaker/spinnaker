import {
  IAmazonApplicationLoadBalancer,
  IAmazonHealth,
  IAmazonTargetGroupHealth,
  ITargetGroup,
} from '@spinnaker/amazon';
import { Application } from '@spinnaker/core';
import { mockHealth, mockInstance, mockLoadBalancer, mockLoadBalancerHealth } from '@spinnaker/mocks';
import {
  applyTargetGroupInfoToHealthMetric,
  buildTaskActions,
  extractHealthMetrics,
} from './titusInstanceDetailsUtils';

const targetGroup1 = {
  account: 'test',
  name: 'tg1',
  healthCheckPort: '8080',
  healthCheckProtocol: 'https',
  healthCheckPath: '/healthcheck',
};
const targetGroup2 = {
  ...targetGroup1,
  name: 'tg2',
  port: '7001',
  healthCheckPort: 'traffic-port',
};

const metricTg1 = {
  instanceId: '100.000.000.00',
  name: 'tg1',
  targetGroupName: 'tg1',
  healthState: 'Up',
  state: 'healthy',
};

const metricTg2 = {
  ...metricTg1,
  name: 'test-tg',
  targetGroupName: 'test-tg',
};

const metricTg3 = {
  ...metricTg1,
  name: 'tg2',
  targetGroupName: 'tg2',
};

const tgHealth = {
  state: 'Up',
  targetGroups: [metricTg1, metricTg2],
  type: 'TargetGroup',
};

describe('applyTargetGroupInfoToHealthMetric', () => {
  const metricGroups = [metricTg1, metricTg2, metricTg3] as IAmazonTargetGroupHealth[];
  const targetGroups = [targetGroup1, targetGroup2] as ITargetGroup[];

  it('should apply healthcheck info to health metrics', () => {
    const [result1, result2, result3] = applyTargetGroupInfoToHealthMetric(metricGroups, targetGroups, 'test');

    // Uses given healthcheck port
    expect(result1.healthCheckPath).toEqual(':8080/healthcheck');
    expect(result1.healthCheckProtocol).toEqual('https');

    // Target group is not found
    expect(result2.healthCheckPath).toBeUndefined();
    expect(result2.healthCheckProtocol).toBeUndefined();

    // Uses traffic port
    expect(result3.healthCheckPath).toEqual(':7001/healthcheck');
    expect(result3.healthCheckProtocol).toEqual('https');
  });
});

describe('extractHealthMetrics', () => {
  it('should extract unknown health states', () => {
    const lb = { ...mockLoadBalancer, targetGroups: [targetGroup1] } as IAmazonApplicationLoadBalancer;
    const health1 = [mockHealth, mockLoadBalancerHealth, { ...mockHealth, state: 'Unknown' }] as IAmazonHealth[];
    const health2 = [mockHealth, mockLoadBalancerHealth] as IAmazonHealth[];

    const result1 = extractHealthMetrics(health1, [lb], 'test');
    expect(result1.length).toEqual(2);

    const result2 = extractHealthMetrics(health2, [lb], 'test');
    expect(result2.length).toEqual(2);
  });

  it('should supplement TargetGroup health info', () => {
    const lb = { ...mockLoadBalancer, targetGroups: [targetGroup1, targetGroup2] } as IAmazonApplicationLoadBalancer;

    const health = [mockHealth, tgHealth] as IAmazonHealth[];

    const result = extractHealthMetrics(health, [lb], 'test');
    expect(result.length).toEqual(2);

    const tgs = result[1].targetGroups;
    expect(tgs[0].healthCheckPath).toEqual(':8080/healthcheck');
  });
});

describe('buildTaskActions', () => {
  it('should render required actions', () => {
    // Instance has Discovery health
    const actions1 = buildTaskActions(mockInstance, {} as Application);
    expect(actions1.length).toEqual(3);
    expect(actions1[2].label).toEqual('Disable in Discovery');

    const actions2 = buildTaskActions(
      { ...mockInstance, health: [{ ...mockHealth, state: 'OutOfService' }] },
      {} as Application,
    );
    expect(actions2.length).toEqual(3);
    expect(actions2[2].label).toEqual('Enable in Discovery');

    // Instance does not have Discovery health
    const actions3 = buildTaskActions({ ...mockInstance, health: [tgHealth as IAmazonHealth] }, {} as Application);
    expect(actions3.length).toEqual(2);

    // Instance has no health
    const actions4 = buildTaskActions({ ...mockInstance, health: [] }, {} as Application);
    expect(actions4.length).toEqual(2);
  });
});
