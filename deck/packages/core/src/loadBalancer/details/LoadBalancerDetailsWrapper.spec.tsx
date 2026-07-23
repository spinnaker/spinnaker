import { mount } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';

import type { Application } from '../../application';
import { CloudProviderRegistry } from '../../cloudProvider';
import { LoadBalancerDetailsContent } from './LoadBalancerDetails';
import { LoadBalancerDetailsWrapper } from './LoadBalancerDetailsWrapper';
import { wrapWithRouter } from '../../utils/testUtils';

const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('LoadBalancerDetailsWrapper', () => {
  const app = { loadBalancers: {} } as Application;
  const loadBalancer = {
    accountId: 'test',
    name: 'lb-1',
    provider: 'aws',
    region: 'us-east-1',
    vpcId: 'vpc-1',
  };

  afterEach(() => {
    (CloudProviderRegistry.getValue as any).and?.callThrough?.();
  });

  it('renders React load balancer details when provider React config is available', async () => {
    const Actions = () => <button />;
    const Section = () => <div />;
    const useDetailsHook = () => ({ data: undefined, error: null, loading: true, refetch: () => Promise.resolve() });
    spyOn(CloudProviderRegistry, 'getValue').and.callFake((_provider: string, key: string) => {
      const values: { [key: string]: any } = {
        'loadBalancer.detailsActions': Actions,
        'loadBalancer.detailsSections': [Section],
        'loadBalancer.useDetailsHook': useDetailsHook,
      };
      return values[key] || null;
    });

    const component = mount(wrapWithRouter(<LoadBalancerDetailsWrapper app={app} loadBalancer={loadBalancer} />));
    await act(async () => {
      await flush();
    });
    component.update();

    expect(component.find(LoadBalancerDetailsContent).prop('app')).toBe(app);
    expect(component.find(LoadBalancerDetailsContent).prop('loadBalancer')).toBe(loadBalancer);
    expect(component.find(LoadBalancerDetailsContent).prop('Actions')).toBe(Actions);
    expect(component.find(LoadBalancerDetailsContent).prop('sections')).toEqual([Section]);
    expect(component.find(LoadBalancerDetailsContent).prop('useDetails')).toBe(useDetailsHook);

    component.unmount();
  });

  it('renders a migration-required message for legacy template/controller-only load balancer details', async () => {
    spyOn(CloudProviderRegistry, 'getValue').and.callFake((_provider: string, key: string) => {
      const values: { [key: string]: any } = {
        'loadBalancer.detailsActions': null,
        'loadBalancer.detailsController': 'legacyLoadBalancerDetailsCtrl',
        'loadBalancer.detailsSections': null,
        'loadBalancer.detailsTemplateUrl': 'legacy-load-balancer-details.html',
        'loadBalancer.useDetailsHook': null,
      };
      return values[key] || null;
    });
    const component = mount(wrapWithRouter(<LoadBalancerDetailsWrapper app={app} loadBalancer={loadBalancer} />));
    await act(async () => {
      await flush();
    });
    component.update();

    expect(component.text()).toContain('Load balancer details for aws must be migrated to React.');

    component.unmount();
  });

  it('renders nothing when provider load balancer details config is missing', async () => {
    spyOn(CloudProviderRegistry, 'getValue').and.returnValue(null);

    const component = mount(wrapWithRouter(<LoadBalancerDetailsWrapper app={app} loadBalancer={loadBalancer} />));
    await act(async () => {
      await flush();
    });
    component.update();

    expect(component.find(LoadBalancerDetailsWrapper).isEmptyRender()).toBe(true);

    component.unmount();
  });
});
