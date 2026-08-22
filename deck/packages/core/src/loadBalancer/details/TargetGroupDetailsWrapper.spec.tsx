import { shallow } from 'enzyme';
import React from 'react';

import type { Application } from '../../application';
import { CloudProviderRegistry } from '../../cloudProvider';
import { TargetGroupDetails } from './TargetGroupDetailsWrapper';

describe('TargetGroupDetails', () => {
  const app = {} as Application;
  const targetGroup = {
    accountId: 'test',
    loadBalancerName: 'lb-1',
    name: 'tg-1',
    provider: 'aws',
    region: 'us-east-1',
    vpcId: 'vpc-1',
  };
  const props = {
    accountId: targetGroup.accountId,
    app,
    name: targetGroup.name,
    provider: targetGroup.provider,
    targetGroup,
  };

  afterEach(() => {
    (CloudProviderRegistry.getValue as any).and?.callThrough?.();
  });

  it('renders provider React target group details when configured', () => {
    const ReactTargetGroupDetails = () => <div className="react-target-group-details" />;
    const getValue = spyOn(CloudProviderRegistry, 'getValue').and.callFake((_provider: string, key: string) =>
      key === 'loadBalancer.targetGroupDetails' ? ReactTargetGroupDetails : null,
    );

    const component = shallow(<TargetGroupDetails {...props} />);

    expect(component.find(ReactTargetGroupDetails).props()).toEqual(props);
    expect(getValue.calls.allArgs()).toEqual([['aws', 'loadBalancer.targetGroupDetails']]);
  });

  it('renders nothing when provider target group details config is missing', () => {
    const getValue = spyOn(CloudProviderRegistry, 'getValue').and.returnValue(null);

    const component = shallow(<TargetGroupDetails {...props} />);

    expect(component.isEmptyRender()).toBe(true);
    expect(getValue.calls.allArgs()).toEqual([['aws', 'loadBalancer.targetGroupDetails']]);
  });
});
