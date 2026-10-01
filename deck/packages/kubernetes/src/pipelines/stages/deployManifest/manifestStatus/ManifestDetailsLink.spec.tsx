import { shallow } from 'enzyme';
import React from 'react';

import { AccountService } from '@spinnaker/core';

import { ManifestDetailsLinkComponent } from './ManifestDetailsLink';

describe('Kubernetes ManifestDetailsLink', () => {
  const render = (manifest: any, accountId = 'test-account', href = vi.fn()) => {
    const component = shallow(
      <ManifestDetailsLinkComponent
        {...({ router: {}, stateParams: {}, stateService: { href } } as any)}
        accountId={accountId}
        linkName="Manifest"
        manifest={manifest}
      />,
    );
    return component;
  };

  it('uses the "name" parameter (not "serverGroupManager") for Deployment manifests', async () => {
    vi.spyOn(AccountService, 'getAccountDetails').mockReturnValue(
      Promise.resolve({ spinnakerKindMap: { Deployment: 'serverGroupManagers' } } as any) as any,
    );
    const href = vi.fn().mockReturnValue('#/manifest');
    const component = render(
      { manifest: { kind: 'Deployment', metadata: { annotations: {}, name: 'test-v001' } } },
      'test-account',
      href,
    );

    await Promise.resolve();
    component.update();

    expect(href).toHaveBeenCalledWith('home.applications.application.insight.clusters.serverGroupManager', {
      accountId: 'test-account',
      provider: 'kubernetes',
      reg: '',
      region: '_',
      name: 'deployment test-v001',
    });
    expect(component.find('a').prop('href')).toBe('#/manifest');
  });

  it('builds its link through the injected state service for a ReplicaSet manifest', async () => {
    vi.spyOn(AccountService, 'getAccountDetails').mockReturnValue(
      Promise.resolve({ spinnakerKindMap: { Deployment: 'serverGroups' } } as any) as any,
    );
    const href = vi.fn().mockReturnValue('#/manifest');
    const component = render(
      { manifest: { kind: 'Deployment', metadata: { annotations: {}, name: 'test-v001' } } },
      'test-account',
      href,
    );

    await Promise.resolve();
    component.update();

    expect(href).toHaveBeenCalledWith('home.applications.application.insight.clusters.serverGroup', {
      accountId: 'test-account',
      provider: 'kubernetes',
      reg: '',
      region: '_',
      serverGroup: 'deployment test-v001',
    });
    expect(component.find('a').prop('href')).toBe('#/manifest');
  });

  it('uses the "kubernetesResource" parameter for unmapped kinds', async () => {
    vi.spyOn(AccountService, 'getAccountDetails').mockReturnValue(
      Promise.resolve({ spinnakerKindMap: {} } as any) as any,
    );
    const href = vi.fn().mockReturnValue('#/manifest');
    const component = render(
      {
        manifest: {
          kind: 'ConfigMap',
          metadata: { annotations: { 'artifact.spinnaker.io/location': 'my-namespace' }, name: 'my-configmap' },
        },
      },
      'test-account',
      href,
    );

    await Promise.resolve();
    component.update();

    expect(href).toHaveBeenCalledWith('home.applications.application.insight.clusters.kubernetesResource', {
      accountId: 'test-account',
      provider: 'kubernetes',
      reg: 'my-namespace',
      region: 'my-namespace',
      kubernetesResource: 'configmap my-configmap',
    });
  });

  it('uses the resource name as region for an unmapped Namespace manifest', async () => {
    vi.spyOn(AccountService, 'getAccountDetails').mockReturnValue(
      Promise.resolve({ spinnakerKindMap: {} } as any) as any,
    );
    const href = vi.fn().mockReturnValue('#/manifest');
    const component = render(
      { manifest: { kind: 'Namespace', metadata: { annotations: {}, name: 'my-namespace' } } },
      'test-account',
      href,
    );

    await Promise.resolve();
    component.update();

    expect(href).toHaveBeenCalledWith(
      'home.applications.application.insight.clusters.kubernetesResource',
      expect.objectContaining({ region: 'my-namespace' }),
    );
  });

  it('does not render a link when the manifest is missing', () => {
    vi.spyOn(AccountService, 'getAccountDetails').mockReturnValue(
      Promise.resolve({ spinnakerKindMap: {} } as any) as any,
    );
    const href = vi.fn();
    const component = render({ manifest: null }, 'test-account', href);

    expect(component.find('a').exists()).toBeFalsy();
  });

  it('does not render a link when the generated URL is empty', async () => {
    vi.spyOn(AccountService, 'getAccountDetails').mockReturnValue(
      Promise.resolve({ spinnakerKindMap: { Deployment: 'serverGroupManagers' } } as any) as any,
    );
    const href = vi.fn().mockReturnValue('');
    const component = render(
      { manifest: { kind: 'Deployment', metadata: { annotations: {}, name: 'test-v001' } } },
      'test-account',
      href,
    );

    await Promise.resolve();
    component.update();

    expect(component.find('a').exists()).toBeFalsy();
  });
});
