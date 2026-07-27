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
    const getValue = spyOn(CloudProviderRegistry, 'getValue').and.callFake((_provider: string, key: string) =>
      key === 'securityGroup.details' ? ReactSecurityGroupDetails : null,
    );

    const component = shallow(
      <StandaloneSecurityGroupDetails app={app} resolvedSecurityGroup={resolvedSecurityGroup} />,
    );

    expect(component.find(ReactSecurityGroupDetails).prop('app')).toBe(app);
    expect(component.find(ReactSecurityGroupDetails).prop('resolvedSecurityGroup')).toBe(resolvedSecurityGroup);
    expect(getValue.calls.allArgs()).toEqual([['kubernetes', 'securityGroup.details']]);
  });

  it('renders nothing when provider security group details config is missing', () => {
    const getValue = spyOn(CloudProviderRegistry, 'getValue').and.returnValue(null);

    const component = shallow(
      <StandaloneSecurityGroupDetails app={app} resolvedSecurityGroup={resolvedSecurityGroup} />,
    );

    expect(component.isEmptyRender()).toBe(true);
    expect(getValue.calls.allArgs()).toEqual([['kubernetes', 'securityGroup.details']]);
  });
});
