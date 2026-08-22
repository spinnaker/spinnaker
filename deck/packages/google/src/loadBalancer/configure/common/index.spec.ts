import {
  buildGceLoadBalancerJobs,
  GCE_LOAD_BALANCER_CAPABILITIES,
  GceLoadBalancerDataController,
  normalizeGceLoadBalancerCommand,
  useGceLoadBalancerData,
} from '.';

describe('GCE load balancer foundation exports', () => {
  it('exposes the shared editor boundary', () => {
    expect(buildGceLoadBalancerJobs).toBeDefined();
    expect(GCE_LOAD_BALANCER_CAPABILITIES).toBeDefined();
    expect(GceLoadBalancerDataController).toBeDefined();
    expect(normalizeGceLoadBalancerCommand).toBeDefined();
    expect(useGceLoadBalancerData).toBeDefined();
  });
});
