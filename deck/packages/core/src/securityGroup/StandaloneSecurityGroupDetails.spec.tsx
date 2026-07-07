import { shallow } from 'enzyme';
import React from 'react';

import type { Application } from '../application';
import { CloudProviderRegistry } from '../cloudProvider';
import { AngularJSAdapter } from '../reactShims';
import { StandaloneSecurityGroupDetails } from './StandaloneSecurityGroupDetails';

describe('StandaloneSecurityGroupDetails', () => {
  const app = { isStandalone: true } as Application;
  const resolvedSecurityGroup = {
    accountId: 'test',
    name: 'sg-123',
    provider: 'kubernetes',
    region: 'us-east-1',
    vpcId: null,
  };

  afterEach(() => {
    (CloudProviderRegistry.getValue as any).and?.callThrough?.();
  });

  it('renders provider React security group details when configured', () => {
    const ReactSecurityGroupDetails = () => <div className="react-security-group-details" />;
    spyOn(CloudProviderRegistry, 'getValue').and.callFake((_provider: string, key: string) =>
      key === 'securityGroup.details' ? ReactSecurityGroupDetails : null,
    );

    const component = shallow(
      <StandaloneSecurityGroupDetails app={app} resolvedSecurityGroup={resolvedSecurityGroup} />,
    );

    expect(component.find(ReactSecurityGroupDetails).prop('app')).toBe(app);
    expect(component.find(ReactSecurityGroupDetails).prop('resolvedSecurityGroup')).toBe(resolvedSecurityGroup);
    expect(component.find(AngularJSAdapter).exists()).toBe(false);
  });

  it('falls back to legacy Angular security group details when no React details are configured', () => {
    spyOn(CloudProviderRegistry, 'getValue').and.callFake((_provider: string, key: string) => {
      const values: { [key: string]: any } = {
        'securityGroup.details': null,
        'securityGroup.detailsController': 'legacySecurityGroupDetailsCtrl',
        'securityGroup.detailsTemplateUrl': 'legacy-security-group-details.html',
      };
      return values[key] || null;
    });

    const component = shallow(
      <StandaloneSecurityGroupDetails app={app} resolvedSecurityGroup={resolvedSecurityGroup} />,
    );
    const adapter = component.find(AngularJSAdapter);

    expect(adapter.prop('templateUrl')).toBe('legacy-security-group-details.html');
    expect(adapter.prop('controller')).toBe('legacySecurityGroupDetailsCtrl as ctrl');
    expect(adapter.prop('locals')).toEqual(jasmine.objectContaining({ app, resolvedSecurityGroup }));
  });
});
