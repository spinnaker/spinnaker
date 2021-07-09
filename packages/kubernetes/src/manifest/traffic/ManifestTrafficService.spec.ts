import { ManifestTrafficService } from '../../manifest/traffic/ManifestTrafficService';

describe('Service: ManifestTrafficService', () => {
  it('will not disable an already disabled server group', () => {
    const canDisable = ManifestTrafficService.canDisableServerGroup({
      disabled: true,
      serverGroupManagers: [],
      loadBalancers: ['service my-service'],
    } as any);
    expect(canDisable).toEqual(false);
  });

  it('will not disable a server group without load balancers', () => {
    const canDisable = ManifestTrafficService.canDisableServerGroup({
      disabled: false,
      serverGroupManagers: [],
      loadBalancers: [],
    } as any);
    expect(canDisable).toEqual(false);
  });

  it('will not disable a managed server group', () => {
    const canDisable = ManifestTrafficService.canDisableServerGroup({
      disabled: false,
      serverGroupManagers: ['deployment my-deployment'],
      loadBalancers: ['service my-service'],
    } as any);
    expect(canDisable).toEqual(false);
  });
});
