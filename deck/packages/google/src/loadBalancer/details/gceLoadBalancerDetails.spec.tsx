import React from 'react';
import { shallow } from 'enzyme';

import { CloudProviderRegistry, ManagedMenuItem } from '@spinnaker/core';

import { GceLoadBalancerChoiceModal } from '../configure/choice/GceLoadBalancerChoiceModal';
import { GceLoadBalancerActions } from './gceLoadBalancerDetails';

describe('GceLoadBalancerActions', () => {
  const app = { name: 'fnord' } as any;
  const loadBalancer = {
    account: 'account-a',
    instances: [],
    loadBalancerType: 'INTERNAL_MANAGED',
    name: 'fnord-main',
    region: 'europe-west1',
  } as any;

  it('hides write actions when the Google provider is disabled', () => {
    spyOn(CloudProviderRegistry, 'isDisabled').and.returnValue(true);

    const wrapper = shallow(<GceLoadBalancerActions app={app} loadBalancer={loadBalancer} />);

    expect(wrapper.isEmptyRender()).toBe(true);
  });

  it('opens the current load balancer in edit mode through managed-resource gating', () => {
    spyOn(CloudProviderRegistry, 'isDisabled').and.returnValue(false);
    const show = spyOn(GceLoadBalancerChoiceModal, 'show').and.returnValue(Promise.resolve() as any);

    const wrapper = shallow(<GceLoadBalancerActions app={app} loadBalancer={loadBalancer} />);
    const edit = wrapper.find(ManagedMenuItem).filterWhere((item) => item.prop('children') === 'Edit Load Balancer');

    expect(edit.prop('application')).toBe(app);
    expect(edit.prop('resource')).toBe(loadBalancer);
    edit.prop('onClick')();
    expect(show).toHaveBeenCalledOnceWith({
      app,
      application: app,
      forPipelineConfig: false,
      isNew: false,
      loadBalancer,
      mode: 'edit',
    } as any);
  });

  it('keeps delete behind managed-resource gating and disables it while instances are attached', () => {
    spyOn(CloudProviderRegistry, 'isDisabled').and.returnValue(false);

    const editable = shallow(<GceLoadBalancerActions app={app} loadBalancer={loadBalancer} />);
    const managedDelete = editable
      .find(ManagedMenuItem)
      .filterWhere((item) => item.prop('children') === 'Delete Load Balancer');
    expect(managedDelete.prop('application')).toBe(app);
    expect(managedDelete.prop('resource')).toBe(loadBalancer);

    const attached = shallow(
      <GceLoadBalancerActions app={app} loadBalancer={{ ...loadBalancer, instances: [{ name: 'instance-a' }] }} />,
    );
    expect(
      attached.find(ManagedMenuItem).filterWhere((item) => item.prop('children') === 'Delete Load Balancer'),
    ).toHaveSize(0);
    expect(attached.find('li.disabled').text()).toContain('Delete Load Balancer');
  });
});
