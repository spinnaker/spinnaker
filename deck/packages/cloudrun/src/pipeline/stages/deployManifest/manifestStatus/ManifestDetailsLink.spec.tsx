import { render, screen } from '@testing-library/react';
import React from 'react';

import { AccountService } from '@spinnaker/core';

import { ManifestDetailsLinkComponent } from './ManifestDetailsLink';

describe('Cloud Run ManifestDetailsLink', () => {
  it('builds its link through the injected state service', async () => {
    vi.spyOn(AccountService, 'getAccountDetails').mockReturnValue(
      Promise.resolve({ spinnakerKindMap: { Service: 'unclassified' } } as any) as any,
    );
    const href = vi.fn().mockReturnValue('#/manifest');
    render(
      <ManifestDetailsLinkComponent
        {...({ router: {}, stateParams: {}, stateService: { href } } as any)}
        accountId="test-account"
        linkName="Manifest"
        manifest={{ manifest: { kind: 'Service', metadata: { annotations: {}, name: 'test-service' } } } as any}
      />,
    );

    const link = await screen.findByRole('link', { name: 'Manifest' });

    expect(href).toHaveBeenCalledWith('home.applications.application.insight.clusters.cloudrunResource', {
      accountId: 'test-account',
      cloudrunResource: 'service test-service',
      provider: 'cloudrun',
      reg: '',
      region: '_',
    });
    expect(link).toHaveAttribute('href', '#/manifest');
  });
});
