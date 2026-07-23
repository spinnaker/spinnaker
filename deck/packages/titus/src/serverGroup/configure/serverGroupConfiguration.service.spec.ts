import { getTitusServerGroupConfigurationService } from './serverGroupConfiguration.service';

describe('getTitusServerGroupConfigurationService', () => {
  it('does not invoke explicit dependencies while creating the service', () => {
    const refreshCaches = jasmine.createSpy('refreshCaches');
    const getLoadBalancers = jasmine.createSpy('getLoadBalancers');
    const getAllSecurityGroups = jasmine.createSpy('getAllSecurityGroups');

    expect(() =>
      getTitusServerGroupConfigurationService(
        { refreshCaches } as any,
        { getLoadBalancers } as any,
        { getAllSecurityGroups } as any,
      ),
    ).not.toThrow();
    expect(refreshCaches).not.toHaveBeenCalled();
    expect(getLoadBalancers).not.toHaveBeenCalled();
    expect(getAllSecurityGroups).not.toHaveBeenCalled();
  });
});
