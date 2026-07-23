import { shallow } from 'enzyme';
import React from 'react';

import type { Application } from '../../application';
import { CloudProviderRegistry } from '../../cloudProvider';
import { StandaloneInstanceDetails } from './StandaloneInstanceDetails';

describe('StandaloneInstanceDetails', () => {
  const app = { isStandalone: true } as Application;
  const instance = {
    account: 'test',
    instanceId: 'i-123',
    noApplication: true,
    provider: 'kubernetes',
    region: 'us-east-1',
  };

  afterEach(() => {
    (CloudProviderRegistry.getValue as any).and?.callThrough?.();
  });

  it('renders provider React instance details when configured', () => {
    const ReactInstanceDetails = () => <div className="react-instance-details" />;
    spyOn(CloudProviderRegistry, 'getValue').and.callFake((_provider: string, key: string) =>
      key === 'instance.details' ? ReactInstanceDetails : null,
    );

    const component = shallow(<StandaloneInstanceDetails app={app} instance={instance} />);

    expect(component.find(ReactInstanceDetails).prop('app')).toBe(app);
    expect(component.find(ReactInstanceDetails).prop('instance')).toBe(instance);
  });

  it('renders a migration-required message for legacy template/controller-only instance details', () => {
    spyOn(CloudProviderRegistry, 'getValue').and.callFake((_provider: string, key: string) => {
      const values: { [key: string]: any } = {
        'instance.details': null,
        'instance.detailsController': 'legacyInstanceDetailsCtrl',
        'instance.detailsTemplateUrl': 'legacy-instance-details.html',
      };
      return values[key] || null;
    });

    const component = shallow(<StandaloneInstanceDetails app={app} instance={instance} />);

    expect(component.dive().text()).toContain('Instance details for kubernetes must be migrated to React.');
  });

  it('renders nothing when provider instance details config is missing', () => {
    spyOn(CloudProviderRegistry, 'getValue').and.returnValue(null);

    const component = shallow(<StandaloneInstanceDetails app={app} instance={instance} />);

    expect(component.isEmptyRender()).toBe(true);
  });
});
