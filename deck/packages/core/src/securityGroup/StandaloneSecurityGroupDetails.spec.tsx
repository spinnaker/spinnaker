import { shallow } from 'enzyme';
import React from 'react';

import type { Application } from '../application';
import { CloudProviderRegistry } from '../cloudProvider';
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
  });

  it('renders a migration-required message for legacy template/controller-only security group details', () => {
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

    expect(component.dive().text()).toContain('Security group details for kubernetes must be migrated to React.');
  });

  it('renders nothing when provider security group details config is missing', () => {
    spyOn(CloudProviderRegistry, 'getValue').and.returnValue(null);

    const component = shallow(
      <StandaloneSecurityGroupDetails app={app} resolvedSecurityGroup={resolvedSecurityGroup} />,
    );

    expect(component.isEmptyRender()).toBe(true);
  });
});
