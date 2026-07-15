import { shallow } from 'enzyme';
import React from 'react';

import type { Application } from '../../application';
import { CloudProviderRegistry } from '../../cloudProvider';
import { AngularJSAdapter } from '../../reactShims';
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
    expect(component.find(AngularJSAdapter).exists()).toBe(false);
  });

  it('falls back to legacy Angular instance details when no React details are configured', () => {
    spyOn(CloudProviderRegistry, 'getValue').and.callFake((_provider: string, key: string) => {
      const values: { [key: string]: any } = {
        'instance.details': null,
        'instance.detailsController': 'legacyInstanceDetailsCtrl',
        'instance.detailsTemplateUrl': 'legacy-instance-details.html',
      };
      return values[key] || null;
    });

    const component = shallow(<StandaloneInstanceDetails app={app} instance={instance} />);
    const adapter = component.find(AngularJSAdapter);

    expect(adapter.prop('templateUrl')).toBe('legacy-instance-details.html');
    expect(adapter.prop('controller')).toBe('legacyInstanceDetailsCtrl as ctrl');
    expect(adapter.prop('locals')).toEqual(jasmine.objectContaining({ app, instance }));
  });
});
