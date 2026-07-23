import { UISref } from '@uirouter/react';
import { mount, shallow } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';
import { BehaviorSubject } from 'rxjs';

import { CollapsibleSection, LoadBalancerReader } from '@spinnaker/core';

import {
  AzureLoadBalancerDetailsSection,
  AzureLoadBalancerFirewallsSection,
  azureLoadBalancerDetailsSections,
  loadAzureLoadBalancerDetails,
  useAzureLoadBalancerDetails,
} from './azureLoadBalancerDetails';

describe('AzureLoadBalancerDetails', () => {
  function buildApp(loadBalancers: any[]) {
    return {
      loadBalancers: {
        data: loadBalancers,
      },
    } as any;
  }

  const params = {
    name: 'fnord-frontend',
    accountId: 'test-account',
    region: 'westus',
    provider: 'azure',
  } as any;

  function deferred<T>() {
    let resolve: (value: T) => void;
    const promise = new Promise<T>((promiseResolve) => {
      resolve = promiseResolve;
    });
    return { promise, resolve: resolve! };
  }

  it('does not refetch details when a rerender recreates callbacks and route params', async () => {
    const summary = {
      account: 'test-account',
      name: 'fnord-frontend',
      provider: 'azure',
      region: 'westus',
    } as any;
    const status$ = new BehaviorSubject({
      status: 'FETCHED',
      loaded: true,
      lastRefresh: 1,
      data: [summary],
    });
    const app = {
      getDataSource: jasmine.createSpy('getDataSource').and.returnValue({
        status$,
        refresh: jasmine.createSpy('refresh'),
      }),
    } as any;
    const getLoadBalancerDetails = spyOn(LoadBalancerReader.prototype, 'getLoadBalancerDetails').and.returnValue(
      new Promise(() => undefined),
    );

    function TestComponent({ renderCount }: { renderCount: number }) {
      useAzureLoadBalancerDetails({
        app,
        loadBalancerParams: { ...params },
        autoClose: () => undefined,
      } as any);
      return <span>{renderCount}</span>;
    }

    let wrapper: any;
    await act(async () => {
      wrapper = mount(<TestComponent renderCount={0} />);
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    await act(async () => {
      wrapper.setProps({ renderCount: 1 });
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(getLoadBalancerDetails).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it('does not let an older route request overwrite newer load balancer details', async () => {
    const oldSummary = { account: 'test-account', name: 'old-lb', provider: 'azure', region: 'westus' } as any;
    const newSummary = { account: 'test-account', name: 'new-lb', provider: 'azure', region: 'westus' } as any;
    const status$ = new BehaviorSubject({
      status: 'FETCHED',
      loaded: true,
      lastRefresh: 1,
      data: [oldSummary, newSummary],
    });
    const app = {
      getDataSource: jasmine.createSpy('getDataSource').and.returnValue({
        status$,
        refresh: jasmine.createSpy('refresh'),
      }),
    } as any;
    const oldRequest = deferred<any[]>();
    const newRequest = deferred<any[]>();
    const getLoadBalancerDetails = spyOn(LoadBalancerReader.prototype, 'getLoadBalancerDetails').and.returnValues(
      oldRequest.promise,
      newRequest.promise,
    );

    function TestComponent({ name }: { name: string }) {
      const result = useAzureLoadBalancerDetails({
        app,
        loadBalancerParams: { ...params, name },
        autoClose: () => undefined,
      } as any);
      return <span>{result.data?.name || ''}</span>;
    }

    let wrapper: any;
    await act(async () => {
      wrapper = mount(<TestComponent name="old-lb" />);
      await Promise.resolve();
    });
    await act(async () => {
      wrapper.setProps({ name: 'new-lb' });
      await Promise.resolve();
    });

    expect(getLoadBalancerDetails).toHaveBeenCalledTimes(2);

    await act(async () => {
      newRequest.resolve([{ name: 'new-lb' }]);
      await newRequest.promise;
    });
    wrapper.update();
    expect(wrapper.text()).toBe('new-lb');

    await act(async () => {
      oldRequest.resolve([{ name: 'old-lb' }]);
      await oldRequest.promise;
    });
    wrapper.update();
    expect(wrapper.text()).toBe('new-lb');
    wrapper.unmount();
  });

  it('stops loading when a data source error invalidates an active details request', async () => {
    const summary = { account: 'test-account', name: 'fnord-frontend', provider: 'azure', region: 'westus' } as any;
    const status$ = new BehaviorSubject({
      status: 'FETCHED',
      loaded: true,
      lastRefresh: 1,
      data: [summary],
      error: null,
    });
    const app = {
      getDataSource: jasmine.createSpy('getDataSource').and.returnValue({
        status$,
        refresh: jasmine.createSpy('refresh'),
      }),
    } as any;
    const request = deferred<any[]>();
    const getLoadBalancerDetails = spyOn(LoadBalancerReader.prototype, 'getLoadBalancerDetails').and.returnValue(
      request.promise,
    );

    function TestComponent() {
      const result = useAzureLoadBalancerDetails({
        app,
        loadBalancerParams: params,
        autoClose: () => undefined,
      } as any);
      return <span data-loading={result.loading}>{result.data?.name || ''}</span>;
    }

    let wrapper: any;
    await act(async () => {
      wrapper = mount(<TestComponent />);
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    wrapper.update();
    expect(getLoadBalancerDetails).toHaveBeenCalledTimes(1);
    expect(wrapper.find('span').prop('data-loading')).toBe(true);

    await act(async () => {
      status$.next({
        status: 'ERROR',
        loaded: true,
        lastRefresh: 2,
        data: [summary],
        error: new Error('load balancer data source failed'),
      });
      await Promise.resolve();
    });
    wrapper.update();
    expect(wrapper.find('span').prop('data-loading')).toBe(false);

    await act(async () => {
      request.resolve([{ name: 'fnord-frontend' }]);
      await request.promise;
    });
    wrapper.update();
    expect(wrapper.text()).toBe('');
    wrapper.unmount();
  });

  it('does not update state when a details request resolves after unmount', async () => {
    const summary = { account: 'test-account', name: 'fnord-frontend', provider: 'azure', region: 'westus' } as any;
    const status$ = new BehaviorSubject({
      status: 'FETCHED',
      loaded: true,
      lastRefresh: 1,
      data: [summary],
    });
    const app = {
      getDataSource: jasmine.createSpy('getDataSource').and.returnValue({
        status$,
        refresh: jasmine.createSpy('refresh'),
      }),
    } as any;
    const request = deferred<any[]>();
    spyOn(LoadBalancerReader.prototype, 'getLoadBalancerDetails').and.returnValue(request.promise);

    function TestComponent() {
      useAzureLoadBalancerDetails({ app, loadBalancerParams: params, autoClose: () => undefined } as any);
      return null;
    }

    let wrapper: any;
    await act(async () => {
      wrapper = mount(<TestComponent />);
      await Promise.resolve();
    });
    const consoleError = spyOn(console, 'error');
    wrapper.unmount();

    await act(async () => {
      request.resolve([{ name: 'fnord-frontend' }]);
      await request.promise;
    });

    const errors = consoleError.calls.allArgs().flat().map(String).join(' ');
    expect(errors).not.toContain('state update on an unmounted component');
  });

  it('loads matching summary details and preserves legacy Azure detail fields', async () => {
    const summary = {
      account: 'test-account',
      name: 'fnord-frontend',
      provider: 'azure',
      region: 'westus',
      loadBalancerType: 'APPLICATION_GATEWAY',
      serverGroups: [],
    };
    const otherSummary = { ...summary, account: 'other-account' };
    const details = [
      { name: 'other-lb', securityGroups: ['sg-other'] },
      {
        name: 'fnord-frontend',
        createdTime: 1710000000000,
        dnsName: 'fnord.example.com',
        securityGroups: ['sg-2', 'sg-1'],
      },
    ];
    const loadBalancerReader = {
      getLoadBalancerDetails: jasmine.createSpy('getLoadBalancerDetails').and.returnValue(Promise.resolve(details)),
    };
    const securityGroupReader = {
      getApplicationSecurityGroup: jasmine.createSpy('getApplicationSecurityGroup').and.callFake(
        (_app: any, account: string, region: string, id: string) =>
          ({
            'sg-1': { id: 'sg-1', name: 'z-firewall', account, region },
            'sg-2': { id: 'sg-2', name: 'a-firewall', account, region },
          }[id]),
      ),
    };
    const autoClose = jasmine.createSpy('autoClose');

    const loadBalancer = await loadAzureLoadBalancerDetails({
      app: buildApp([otherSummary, summary]),
      loadBalancerParams: params,
      loadBalancerReader: loadBalancerReader as any,
      securityGroupReader: securityGroupReader as any,
      autoClose,
    });

    expect(loadBalancerReader.getLoadBalancerDetails).toHaveBeenCalledWith(
      'azure',
      'test-account',
      'westus',
      'fnord-frontend',
    );
    expect(securityGroupReader.getApplicationSecurityGroup).toHaveBeenCalledWith(
      jasmine.anything(),
      'test-account',
      'westus',
      'sg-2',
    );
    expect(autoClose).not.toHaveBeenCalled();
    expect(loadBalancer).toBe(summary as any);
    expect(loadBalancer.elb).toBe(details[1] as any);
    expect(loadBalancer.account).toBe('test-account');
    expect(loadBalancer.loadBalancerType).toBe('Application Gateway');
    expect(loadBalancer.securityGroups).toEqual([
      { id: 'sg-2', name: 'a-firewall', account: 'test-account', region: 'westus' },
      { id: 'sg-1', name: 'z-firewall', account: 'test-account', region: 'westus' },
    ] as any);
  });

  it('closes the details panel when no matching summary exists', async () => {
    const loadBalancerReader = {
      getLoadBalancerDetails: jasmine.createSpy('getLoadBalancerDetails'),
    };
    const autoClose = jasmine.createSpy('autoClose');

    const loadBalancer = await loadAzureLoadBalancerDetails({
      app: buildApp([{ name: 'fnord-frontend', account: 'test-account', region: 'eastus', provider: 'azure' }]),
      loadBalancerParams: params,
      loadBalancerReader: loadBalancerReader as any,
      securityGroupReader: {} as any,
      autoClose,
    });

    expect(loadBalancer).toBeUndefined();
    expect(autoClose).toHaveBeenCalled();
    expect(loadBalancerReader.getLoadBalancerDetails).not.toHaveBeenCalled();
  });

  it('loads matching details from fresh load balancer data instead of stale app data', async () => {
    const staleSummary = { name: 'fnord-frontend', account: 'test-account', region: 'eastus', provider: 'azure' };
    const freshSummary = { name: 'fnord-frontend', account: 'test-account', region: 'westus', provider: 'azure' };
    const details = [{ name: 'fnord-frontend' }];
    const loadBalancerReader = {
      getLoadBalancerDetails: jasmine.createSpy('getLoadBalancerDetails').and.returnValue(Promise.resolve(details)),
    };
    const autoClose = jasmine.createSpy('autoClose');

    const loadBalancer = await loadAzureLoadBalancerDetails({
      app: buildApp([staleSummary]),
      loadBalancers: [freshSummary],
      loadBalancerParams: params,
      loadBalancerReader: loadBalancerReader as any,
      securityGroupReader: {} as any,
      autoClose,
    } as any);

    expect(loadBalancer).toBe(freshSummary as any);
    expect(autoClose).not.toHaveBeenCalled();
    expect(loadBalancerReader.getLoadBalancerDetails).toHaveBeenCalledWith(
      'azure',
      'test-account',
      'westus',
      'fnord-frontend',
    );
  });

  it('registers health checks separately from listeners', () => {
    expect(azureLoadBalancerDetailsSections.map((Section) => Section.name)).toEqual([
      'AzureLoadBalancerDetailsSection',
      'AzureLoadBalancerStatusSection',
      'AzureLoadBalancerListenersSection',
      'AzureLoadBalancerFirewallsSection',
      'AzureLoadBalancerHealthChecksSection',
    ]);
  });

  it('renders legacy server group navigation links', () => {
    const loadBalancer = {
      account: 'test-account',
      loadBalancerType: 'Azure Load Balancer',
      region: 'westus',
      serverGroups: [{ name: 'fnord-v001', account: 'test-account', region: 'westus', isDisabled: false }],
    };

    const wrapper = shallow(<AzureLoadBalancerDetailsSection loadBalancer={loadBalancer as any} />);
    const sectionContent = shallow(<div>{wrapper.find(CollapsibleSection).prop('children')}</div>);
    const link = sectionContent.find(UISref);

    expect(link.prop('to')).toBe('^.serverGroup');
    expect(link.prop('params')).toEqual({
      region: 'westus',
      accountId: 'test-account',
      serverGroup: 'fnord-v001',
      provider: 'azure',
    });
  });

  it('renders legacy firewall navigation links', () => {
    const loadBalancer = {
      account: 'test-account',
      provider: 'azure',
      region: 'westus',
      vpcId: 'vnet-1',
      securityGroups: [{ id: 'sg-1', name: 'frontend-firewall' }],
    };

    const wrapper = shallow(<AzureLoadBalancerFirewallsSection loadBalancer={loadBalancer as any} />);
    const sectionContent = shallow(<div>{wrapper.find(CollapsibleSection).prop('children')}</div>);
    const link = sectionContent.find(UISref);

    expect(link.prop('to')).toBe('^.firewallDetails');
    expect(link.prop('params')).toEqual({
      name: 'frontend-firewall',
      accountId: 'test-account',
      region: 'westus',
      vpcId: 'vnet-1',
      provider: 'azure',
    });
  });
});
