import { render, screen, waitFor } from '@testing-library/react';
import React from 'react';

import { AccountService } from '@spinnaker/core';

import { ManifestDetailsLinkComponent } from './ManifestDetailsLink';

describe('Kubernetes ManifestDetailsLink', () => {
  const renderLink = (manifest: any, accountId = 'test-account', href = vi.fn()) =>
    render(
      <ManifestDetailsLinkComponent
        {...({ router: {}, stateParams: {}, stateService: { href } } as any)}
        accountId={accountId}
        linkName="Manifest"
        manifest={manifest}
      />,
    );

  it('uses the "name" parameter (not "serverGroupManager") for Deployment manifests', async () => {
    vi.spyOn(AccountService, 'getAccountDetails').mockReturnValue(
      Promise.resolve({ spinnakerKindMap: { Deployment: 'serverGroupManagers' } } as any) as any,
    );
    const href = vi.fn().mockReturnValue('#/manifest');
    renderLink(
      { manifest: { kind: 'Deployment', metadata: { annotations: {}, name: 'test-v001' } } },
      'test-account',
      href,
    );

    const link = await screen.findByRole('link', { name: 'Manifest' });
    expect(href).toHaveBeenCalledWith('home.applications.application.insight.clusters.serverGroupManager', {
      accountId: 'test-account',
      provider: 'kubernetes',
      reg: '',
      region: '_',
      name: 'deployment test-v001',
    });
    expect(link).toHaveAttribute('href', '#/manifest');
  });

  it('builds its link through the injected state service for a ReplicaSet manifest', async () => {
    vi.spyOn(AccountService, 'getAccountDetails').mockReturnValue(
      Promise.resolve({ spinnakerKindMap: { Deployment: 'serverGroups' } } as any) as any,
    );
    const href = vi.fn().mockReturnValue('#/manifest');
    renderLink(
      { manifest: { kind: 'Deployment', metadata: { annotations: {}, name: 'test-v001' } } },
      'test-account',
      href,
    );

    const link = await screen.findByRole('link', { name: 'Manifest' });
    expect(href).toHaveBeenCalledWith('home.applications.application.insight.clusters.serverGroup', {
      accountId: 'test-account',
      provider: 'kubernetes',
      reg: '',
      region: '_',
      serverGroup: 'deployment test-v001',
    });
    expect(link).toHaveAttribute('href', '#/manifest');
  });

  it('uses the "kubernetesResource" parameter for unmapped kinds', async () => {
    vi.spyOn(AccountService, 'getAccountDetails').mockReturnValue(
      Promise.resolve({ spinnakerKindMap: {} } as any) as any,
    );
    const href = vi.fn().mockReturnValue('#/manifest');
    renderLink(
      {
        manifest: {
          kind: 'ConfigMap',
          metadata: { annotations: { 'artifact.spinnaker.io/location': 'my-namespace' }, name: 'my-configmap' },
        },
      },
      'test-account',
      href,
    );

    await screen.findByRole('link', { name: 'Manifest' });
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
    renderLink(
      { manifest: { kind: 'Namespace', metadata: { annotations: {}, name: 'my-namespace' } } },
      'test-account',
      href,
    );

    await screen.findByRole('link', { name: 'Manifest' });
    expect(href).toHaveBeenCalledWith(
      'home.applications.application.insight.clusters.kubernetesResource',
      expect.objectContaining({ region: 'my-namespace' }),
    );
  });

  it('does not render a link when the manifest is missing', () => {
    const getAccountDetails = vi
      .spyOn(AccountService, 'getAccountDetails')
      .mockReturnValue(Promise.resolve({ spinnakerKindMap: {} } as any) as any);
    const href = vi.fn();
    renderLink({ manifest: null }, 'test-account', href);

    expect(getAccountDetails).not.toHaveBeenCalled();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('does not render a link when the generated URL is empty', async () => {
    vi.spyOn(AccountService, 'getAccountDetails').mockReturnValue(
      Promise.resolve({ spinnakerKindMap: { Deployment: 'serverGroupManagers' } } as any) as any,
    );
    const href = vi.fn().mockReturnValue('');
    renderLink(
      { manifest: { kind: 'Deployment', metadata: { annotations: {}, name: 'test-v001' } } },
      'test-account',
      href,
    );

    await waitFor(() => expect(href).toHaveBeenCalled());
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
});
