import { shallow } from 'enzyme';
import React from 'react';

import { AccountService } from '@spinnaker/core';

import { ManifestDetailsLinkComponent } from './ManifestDetailsLink';

describe('Cloud Run ManifestDetailsLink', () => {
  it('builds its link through the injected state service', async () => {
    spyOn(AccountService, 'getAccountDetails').and.returnValue(
      Promise.resolve({ spinnakerKindMap: { Service: 'unclassified' } } as any) as any,
    );
    const href = jasmine.createSpy('href').and.returnValue('#/manifest');
    const component = shallow(
      <ManifestDetailsLinkComponent
        {...({ router: {}, stateParams: {}, stateService: { href } } as any)}
        accountId="test-account"
        linkName="Manifest"
        manifest={{ manifest: { kind: 'Service', metadata: { annotations: {}, name: 'test-service' } } } as any}
      />,
    );

    await Promise.resolve();
    component.update();

    expect(href).toHaveBeenCalledWith('home.applications.application.insight.clusters.cloudrunResource', {
      accountId: 'test-account',
      cloudrunResource: 'service test-service',
      provider: 'cloudrun',
      reg: '',
      region: '_',
    });
    expect(component.find('a').prop('href')).toBe('#/manifest');
  });
});
