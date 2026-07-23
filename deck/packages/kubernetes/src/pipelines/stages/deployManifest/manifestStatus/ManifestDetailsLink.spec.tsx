import { shallow } from 'enzyme';
import React from 'react';

import { AccountService } from '@spinnaker/core';

import { ManifestDetailsLinkComponent } from './ManifestDetailsLink';

describe('Kubernetes ManifestDetailsLink', () => {
  it('builds its link through the injected state service', async () => {
    spyOn(AccountService, 'getAccountDetails').and.returnValue(
      Promise.resolve({ spinnakerKindMap: { Deployment: 'serverGroups' } } as any) as any,
    );
    const href = jasmine.createSpy('href').and.returnValue('#/manifest');
    const component = shallow(
      <ManifestDetailsLinkComponent
        {...({ router: {}, stateParams: {}, stateService: { href } } as any)}
        accountId="test-account"
        linkName="Manifest"
        manifest={{ manifest: { kind: 'Deployment', metadata: { annotations: {}, name: 'test-v001' } } } as any}
      />,
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
});
