import type { IGceLoadBalancer } from '../domain/loadBalancer';

import { GceHttpLoadBalancerUtils } from './httpLoadBalancerUtils.service';

describe('GceHttpLoadBalancerUtils', () => {
  const utils = new GceHttpLoadBalancerUtils();

  it('treats EXTERNAL_MANAGED as part of the HTTP load balancer family', () => {
    const externalManaged = {
      account: 'account-a',
      listeners: [{ name: 'listener-443' }],
      loadBalancerType: 'EXTERNAL_MANAGED',
      provider: 'gce',
      region: 'us-central1',
      urlMapName: 'app-main',
    } as IGceLoadBalancer;

    expect(utils.isHttpLoadBalancer(externalManaged)).toBe(true);
    expect(utils.isRegionalHttpLoadBalancer(externalManaged)).toBe(true);
    expect(utils.isExternalHttpLoadBalancer(externalManaged)).toBe(true);
  });

  it('keeps global HTTP and INTERNAL_MANAGED classification unchanged', () => {
    const globalHttp = {
      account: 'account-a',
      listeners: [{ name: 'listener-80' }],
      loadBalancerType: 'HTTP',
      provider: 'gce',
      region: 'global',
      urlMapName: 'app-main',
    } as IGceLoadBalancer;
    const internalManaged = {
      account: 'account-a',
      listeners: [{ name: 'listener-internal' }],
      loadBalancerType: 'INTERNAL_MANAGED',
      provider: 'gce',
      region: 'europe-west1',
      urlMapName: 'app-internal',
    } as IGceLoadBalancer;

    expect(utils.isHttpLoadBalancer(globalHttp)).toBe(true);
    expect(utils.isRegionalHttpLoadBalancer(globalHttp)).toBe(false);

    expect(utils.isHttpLoadBalancer(internalManaged)).toBe(true);
    expect(utils.isRegionalHttpLoadBalancer(internalManaged)).toBe(true);
    expect(utils.isExternalHttpLoadBalancer(internalManaged)).toBe(false);
  });

  it('scopes regional listener normalization by account and region', () => {
    const loadBalancers = [
      {
        account: 'account-a',
        listeners: [{ name: 'listener-a' }],
        loadBalancerType: 'EXTERNAL_MANAGED',
        name: 'app-main (account-a/us-central1/EXTERNAL_MANAGED)',
        provider: 'gce',
        region: 'us-central1',
        urlMapName: 'app-main',
      },
      {
        account: 'account-a',
        listeners: [{ name: 'listener-b' }],
        loadBalancerType: 'EXTERNAL_MANAGED',
        name: 'app-main (account-a/europe-west1/EXTERNAL_MANAGED)',
        provider: 'gce',
        region: 'europe-west1',
        urlMapName: 'app-main',
      },
    ] as IGceLoadBalancer[];

    expect(
      utils.normalizeLoadBalancerNamesForAccount(['listener-a'], 'account-a', loadBalancers, 'us-central1'),
    ).toEqual(['app-main (account-a/us-central1/EXTERNAL_MANAGED)']);
    expect(
      utils.normalizeLoadBalancerNamesForAccount(['listener-b'], 'account-a', loadBalancers, 'europe-west1'),
    ).toEqual(['app-main (account-a/europe-west1/EXTERNAL_MANAGED)']);
  });

  it('maps global HTTP listener names to url map names without requiring region', () => {
    const loadBalancers = [
      {
        account: 'account-a',
        listeners: [{ name: 'listener-80' }, { name: 'listener-443' }],
        loadBalancerType: 'HTTP',
        name: 'app-main',
        provider: 'gce',
        region: 'global',
        urlMapName: 'app-main',
      },
    ] as IGceLoadBalancer[];

    expect(utils.normalizeLoadBalancerNamesForAccount(['listener-443'], 'account-a', loadBalancers)).toEqual([
      'app-main',
    ]);
  });
});
