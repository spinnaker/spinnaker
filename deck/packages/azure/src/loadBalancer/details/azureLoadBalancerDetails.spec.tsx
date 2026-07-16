import { UISref } from '@uirouter/react';
import { shallow } from 'enzyme';
import React from 'react';

import { CollapsibleSection } from '@spinnaker/core';

import {
  AzureLoadBalancerDetailsSection,
  AzureLoadBalancerFirewallsSection,
  azureLoadBalancerDetailsSections,
  loadAzureLoadBalancerDetails,
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
