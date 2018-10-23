import { load } from 'js-yaml';
import { ManifestTrafficService } from 'kubernetes/v2/manifest/traffic/ManifestTrafficService';

describe('Service: ManifestTrafficService', () => {
  it('will not disable an already disabled server group', () => {
    const canDisable = ManifestTrafficService.canDisableServerGroup({
      disabled: true,
      serverGroupManagers: [],
      manifest: load(`
        kind: ReplicaSet
        metadata: 
          annotations:
            traffic.spinnaker.io/load-balancers: '[\"service my-service\"]'
        `),
    } as any);
    expect(canDisable).toEqual(false);
  });

  it('will not disable a server group without load balancers', () => {
    ['', "traffic.spinnaker.io/load-balancers: '[]'"].forEach(annotation => {
      const canDisable = ManifestTrafficService.canDisableServerGroup({
        disabled: false,
        serverGroupManagers: [],
        manifest: load(`
        kind: ReplicaSet
        metadata: 
          annotations:
            ${annotation}
        `),
      } as any);
      expect(canDisable).toEqual(false);
    });
  });

  it('will not disable a managed server group', () => {
    const canDisable = ManifestTrafficService.canDisableServerGroup({
      disabled: false,
      serverGroupManagers: ['deployment my-deployment'],
      manifest: load(`
        kind: ReplicaSet
        metadata: 
          annotations:
            traffic.spinnaker.io/load-balancers: '[\"service my-service\"]'
        `),
    } as any);
    expect(canDisable).toEqual(false);
  });
});
