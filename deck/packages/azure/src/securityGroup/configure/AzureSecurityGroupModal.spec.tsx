import { AccountService, NetworkReader, TaskMonitor } from '@spinnaker/core';
import { shallow } from 'enzyme';
import React from 'react';
import { Modal } from 'react-bootstrap';

import {
  addSecurityRule,
  AzureSecurityGroupModalComponent as AzureSecurityGroupModal,
  getAzureVnetSelectValue,
  initializeAzureSecurityGroupForModal,
  isAzureSecurityGroupValid,
  moveSecurityRule,
  normalizeAzureSecurityGroupForSubmit,
  removeSecurityRule,
} from './AzureSecurityGroupModal';
import { AzureSecurityGroupWriter } from '../securityGroup.write.service';

describe('AzureSecurityGroupModal', () => {
  function buildModal(props: any, state: any = {}): any {
    const modal = Object.create(AzureSecurityGroupModal.prototype);
    modal.props = {
      app: { name: 'fnord', securityGroups: { refresh: jasmine.createSpy('refresh') } },
      closeModal: jasmine.createSpy('closeModal'),
      dismissModal: jasmine.createSpy('dismissModal'),
      mode: 'create',
      ...props,
    };
    modal.state = {
      accounts: [],
      regions: [],
      securityGroup: {
        name: 'fnord-sg',
        accountId: 'test-account',
        region: 'westus',
        securityRules: [],
      },
      selectedSubnets: [],
      selectedVnets: [],
      taskMonitor: { submit: jasmine.createSpy('submit').and.callFake((method: () => any) => method()) },
      ...state,
    };
    modal.setState = (updater: any, callback?: () => void) => {
      const nextState = typeof updater === 'function' ? updater(modal.state, modal.props) : updater;
      modal.state = { ...modal.state, ...nextState };
      if (callback) {
        callback();
      }
    };
    return modal;
  }

  it('normalizes multiple and single destination ports and source CIDRs for submit', () => {
    const command = normalizeAzureSecurityGroupForSubmit({
      name: 'fnord-sg',
      securityRules: [
        {
          name: 'allow-web',
          destinationPorts: ['80', '443'],
          sourceCidrs: ['10.0.0.0/24', '192.168.0.0/24'],
        },
        {
          name: 'allow-ssh',
          destinationPorts: ['22'],
          sourceCidrs: ['*'],
        },
      ],
    } as any);

    expect(command.type).toBe('upsertSecurityGroup');
    expect(command.securityRules[0].destinationPortRanges).toEqual(['80', '443']);
    expect(command.securityRules[0].destinationPortRange).toBeUndefined();
    expect(command.securityRules[0].sourceAddressPrefixes).toEqual(['10.0.0.0/24', '192.168.0.0/24']);
    expect(command.securityRules[0].sourceAddressPrefix).toBeUndefined();
    expect(command.securityRules[1].destinationPortRange).toBe('22');
    expect(command.securityRules[1].destinationPortRanges).toBeUndefined();
    expect(command.securityRules[1].sourceAddressPrefix).toBe('*');
    expect(command.securityRules[1].sourceAddressPrefixes).toBeUndefined();
  });

  it('preserves single destination port and source CIDR when plural arrays are empty', () => {
    const command = normalizeAzureSecurityGroupForSubmit({
      name: 'fnord-sg',
      securityRules: [
        {
          name: 'allow-web',
          destinationPortRange: '443',
          destinationPortRanges: [],
          sourceAddressPrefix: '*',
          sourceAddressPrefixes: [],
        },
      ],
    } as any);

    expect(command.securityRules[0].destinationPortRange).toBe('443');
    expect(command.securityRules[0].destinationPortRanges).toBeUndefined();
    expect(command.securityRules[0].sourceAddressPrefix).toBe('*');
    expect(command.securityRules[0].sourceAddressPrefixes).toBeUndefined();
  });

  it('normalizes legacy details model fields for submit', () => {
    const command = normalizeAzureSecurityGroupForSubmit({
      name: 'fnord-sg',
      securityRules: [
        {
          name: 'allow-web',
          destinationPortRangeModel: '80,443',
          sourceAddressPrefixModel: '10.0.0.0/24,192.168.0.0/24',
        },
      ],
    } as any);

    expect(command.securityRules[0].destinationPortRanges).toEqual(['80', '443']);
    expect(command.securityRules[0].sourceAddressPrefixes).toEqual(['10.0.0.0/24', '192.168.0.0/24']);
  });

  it('initializes edit rules from legacy details model fields', () => {
    const initialized = initializeAzureSecurityGroupForModal(
      {
        mode: 'edit',
        securityGroup: {
          accountId: 'test-account',
          name: 'fnord-sg',
          region: 'westus',
          securityRules: [
            {
              name: 'allow-web',
              destinationPortRangeModel: '443',
              sourceAddressPrefixModel: '10.0.0.0/24',
            },
          ],
        },
      } as any,
      'fnord',
    );

    expect(initialized.securityRules[0].destinationPorts).toEqual(['443']);
    expect(initialized.securityRules[0].destPortRanges).toBe('443');
    expect(initialized.securityRules[0].sourceCidrs).toEqual(['10.0.0.0/24']);
    expect(initialized.securityRules[0].sourceIPCIDRRanges).toBe('10.0.0.0/24');
  });

  it('starts create mode without rules and includes Azure-required wildcard fields on added rules', () => {
    const initialized = initializeAzureSecurityGroupForModal(
      { mode: 'create', credentials: 'test-account' } as any,
      'fnord',
    );
    const added = addSecurityRule(initialized.securityRules);

    expect(initialized.securityRules).toEqual([]);
    expect(added[0].destinationPorts).toEqual(['*']);
    expect(added[0].destinationPortRange).toBe('*');
    expect(added[0].destinationPortRanges).toEqual([]);
    expect(added[0].destPortRanges).toBe('*');
    expect(added[0].sourceAddressPrefix).toBe('*');
    expect(added[0].sourceAddressPrefixes).toEqual([]);
    expect(added[0].sourceIPCIDRRanges).toBe('*');
    expect(added[0].sourcePortRange).toBe('*');
    expect(added[0].destinationAddressPrefix).toBe('*');
  });

  it('initializes create mode from provider defaults and existing selected network fields', () => {
    const selectedVnet = { name: 'vnet-a', resourceGroup: 'rg-a', subnets: [{ name: 'subnet-a' }] };
    const initialized = initializeAzureSecurityGroupForModal(
      {
        mode: 'create',
        credentials: 'test-account',
        region: 'westus',
        securityGroup: { selectedVnet, selectedSubnet: { name: 'subnet-a' } },
      } as any,
      'fnord',
    );

    expect(initialized.accountId).toBe('test-account');
    expect(initialized.credentials).toBe('test-account');
    expect(initialized.region).toBe('westus');
    expect(initialized.selectedVnet).toBe(selectedVnet as any);
    expect(initialized.vnet).toBe('vnet-a');
    expect(initialized.vnetResourceGroup).toBe('rg-a');
    expect(initialized.subnet).toBe('subnet-a');
  });

  it('derives create and clone names from the application and detail fields', () => {
    const created = initializeAzureSecurityGroupForModal(
      {
        mode: 'create',
        credentials: 'test-account',
        securityGroup: { description: 'web ingress' },
      } as any,
      'fnord',
    );
    const cloned = initializeAzureSecurityGroupForModal(
      {
        mode: 'clone',
        securityGroup: {
          accountId: 'test-account',
          description: 'clone me',
          id: 'source-id',
          name: 'fnord-api',
          region: 'westus',
        },
      } as any,
      'fnord',
    );
    spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({
      dismiss: () => null,
      result: Promise.resolve(),
    } as any);
    const modal = new AzureSecurityGroupModal({
      app: { name: 'fnord', securityGroups: { refresh: jasmine.createSpy('refresh') } },
      closeModal: jasmine.createSpy('closeModal'),
      dismissModal: jasmine.createSpy('dismissModal'),
      mode: 'create',
      securityGroup: created,
    } as any) as any;
    modal.setState = (updater: any, callback?: () => void) => {
      const nextState = typeof updater === 'function' ? updater(modal.state, modal.props) : updater;
      modal.state = { ...modal.state, ...nextState };
      if (callback) {
        callback();
      }
    };

    modal.updateField('detail', 'web');

    expect(created.name).toBe('fnord');
    expect(created.description).toBe('web ingress');
    expect(modal.state.securityGroup.detail).toBe('web');
    expect(modal.state.securityGroup.name).toBe('fnord-web');
    expect(cloned.detail).toBe('api');
    expect(cloned.name).toBe('fnord-api');
    expect(cloned.description).toBe('clone me');
    expect(cloned.id).toBeUndefined();
  });

  it('requires create and clone details and rejects duplicate generated names', () => {
    const application = {
      name: 'fnord',
      securityGroups: {
        data: [{ accountId: 'test-account', name: 'fnord-web', region: 'westus' }],
      },
    } as any;
    const securityGroup = {
      accountId: 'test-account',
      detail: 'web',
      name: 'fnord-web',
      region: 'westus',
      securityRules: [{ destinationPorts: ['443'], sourceCidrs: ['*'] }],
    } as any;

    expect(isAzureSecurityGroupValid({ ...securityGroup, detail: '' }, 'create', application)).toBe(false);
    expect(isAzureSecurityGroupValid(securityGroup, 'create', application)).toBe(false);
    expect(
      isAzureSecurityGroupValid({ ...securityGroup, detail: 'api', name: 'fnord-api' }, 'clone', application),
    ).toBe(true);
  });

  it('adds, removes, and reorders rules while keeping priorities normalized', () => {
    const first = { name: 'first', priority: 100 };
    const second = { name: 'second', priority: 101 };

    const added = addSecurityRule([first]);
    expect(added.length).toBe(2);
    expect(added[1].priority).toBe(101);

    const moved = moveSecurityRule([first, second], 0, 1);
    expect(moved.map((rule) => rule.name)).toEqual(['second', 'first']);
    expect(moved.map((rule) => rule.priority)).toEqual([100, 101]);

    const removed = removeSecurityRule([first, second], 0);
    expect(removed).toEqual([{ name: 'second', priority: 100 } as any]);
  });

  it('submits create edit and clone modes through the Azure security group writer', () => {
    spyOn(AzureSecurityGroupWriter, 'upsertSecurityGroup').and.returnValue(Promise.resolve({} as any));

    ['create', 'edit', 'clone'].forEach((mode) => {
      const modal = buildModal({ mode });
      modal.submit();
    });

    expect(AzureSecurityGroupWriter.upsertSecurityGroup).toHaveBeenCalledTimes(3);
    expect(AzureSecurityGroupWriter.upsertSecurityGroup.calls.argsFor(0)[2]).toBe('Create');
    expect(AzureSecurityGroupWriter.upsertSecurityGroup.calls.argsFor(1)[2]).toBe('Update');
    expect(AzureSecurityGroupWriter.upsertSecurityGroup.calls.argsFor(2)[2]).toBe('Clone');
  });

  it('loads regions and Azure VNets for the selected account and region', async () => {
    spyOn(AccountService, 'getRegionsForAccount').and.returnValue(Promise.resolve([{ name: 'westus' }]) as any);
    spyOn(NetworkReader, 'listNetworks').and.returnValue(
      Promise.resolve({
        azure: [
          {
            account: 'test-account',
            name: 'vnet-a',
            region: 'westus',
            resourceGroup: 'rg-a',
            subnets: [{ name: 'subnet-a' }],
          },
          { account: 'other-account', name: 'vnet-b', region: 'westus', resourceGroup: 'rg-b', subnets: [] },
        ],
      }) as any,
    );
    spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({
      dismiss: () => null,
      result: Promise.resolve(),
    } as any);
    const modal = new AzureSecurityGroupModal({
      app: { name: 'fnord', securityGroups: { refresh: jasmine.createSpy('refresh') } },
      closeModal: jasmine.createSpy('closeModal'),
      credentials: 'test-account',
      dismissModal: jasmine.createSpy('dismissModal'),
      mode: 'create',
    } as any) as any;
    modal.setState = (updater: any, callback?: () => void) => {
      const nextState = typeof updater === 'function' ? updater(modal.state, modal.props) : updater;
      modal.state = { ...modal.state, ...nextState };
      if (callback) {
        callback();
      }
    };

    modal.accountUpdated();
    await Promise.resolve();
    await Promise.resolve();

    expect(modal.state.regions).toEqual([{ name: 'westus' }]);
    expect(modal.state.securityGroup.region).toBe('westus');
    expect(modal.state.selectedVnets.map((vnet: any) => getAzureVnetSelectValue(vnet))).toEqual(['vnet-a|rg-a']);
  });

  it('clears stale VNet and subnet fields when account or region reloads networks', async () => {
    spyOn(NetworkReader, 'listNetworks').and.returnValue(Promise.resolve({ azure: [] }) as any);
    spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({
      dismiss: () => null,
      result: Promise.resolve(),
    } as any);
    const modal = new AzureSecurityGroupModal({
      app: { name: 'fnord', securityGroups: { refresh: jasmine.createSpy('refresh') } },
      closeModal: jasmine.createSpy('closeModal'),
      dismissModal: jasmine.createSpy('dismissModal'),
      mode: 'create',
      securityGroup: {
        accountId: 'test-account',
        credentials: 'test-account',
        name: 'fnord-sg',
        region: 'westus',
        selectedSubnet: { name: 'old-subnet' },
        selectedVnet: { name: 'old-vnet', resourceGroup: 'old-rg' },
        subnet: 'old-subnet',
        vnet: 'old-vnet',
        vnetResourceGroup: 'old-rg',
        vpcId: 'old-vnet',
      },
    } as any) as any;
    modal.setState = (updater: any, callback?: () => void) => {
      const nextState = typeof updater === 'function' ? updater(modal.state, modal.props) : updater;
      modal.state = { ...modal.state, ...nextState };
      if (callback) {
        callback();
      }
    };

    modal.vnetUpdated();
    await Promise.resolve();

    expect(modal.state.securityGroup.selectedVnet).toBeNull();
    expect(modal.state.securityGroup.selectedSubnet).toBeNull();
    expect(modal.state.securityGroup.vnet).toBeNull();
    expect(modal.state.securityGroup.vnetResourceGroup).toBeNull();
    expect(modal.state.securityGroup.subnet).toBeNull();
    expect(modal.state.securityGroup.vpcId).toBeNull();
    expect(modal.state.selectedSubnets).toEqual([]);
  });

  it('updates vpcId when selecting a new VNet', () => {
    spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({
      dismiss: () => null,
      result: Promise.resolve(),
    } as any);
    const selectedVnet = { name: 'new-vnet', resourceGroup: 'new-rg', subnets: [{ name: 'new-subnet' }] };
    const modal = new AzureSecurityGroupModal({
      app: { name: 'fnord', securityGroups: { refresh: jasmine.createSpy('refresh') } },
      closeModal: jasmine.createSpy('closeModal'),
      dismissModal: jasmine.createSpy('dismissModal'),
      mode: 'clone',
      securityGroup: {
        accountId: 'test-account',
        name: 'fnord-sg',
        region: 'westus',
        selectedVnet: { name: 'old-vnet', resourceGroup: 'old-rg' },
        vnet: 'old-vnet',
        vnetResourceGroup: 'old-rg',
        vpcId: 'old-vnet',
      },
    } as any) as any;
    modal.state = { ...modal.state, selectedVnets: [selectedVnet] };
    modal.setState = (updater: any, callback?: () => void) => {
      const nextState = typeof updater === 'function' ? updater(modal.state, modal.props) : updater;
      modal.state = { ...modal.state, ...nextState };
      if (callback) {
        callback();
      }
    };

    modal.selectedVnetChanged(getAzureVnetSelectValue(selectedVnet as any));

    expect(modal.state.securityGroup.selectedVnet).toBe(selectedVnet as any);
    expect(modal.state.securityGroup.vnet).toBe('new-vnet');
    expect(modal.state.securityGroup.vnetResourceGroup).toBe('new-rg');
    expect(modal.state.securityGroup.vpcId).toBe('new-vnet');
  });

  it('does not render identity and location controls while editing inbound rules', () => {
    const modal = buildModal(
      { mode: 'edit' },
      {
        securityGroup: {
          accountId: 'test-account',
          name: 'fnord-sg',
          region: 'westus',
          securityRules: [{ name: 'allow-web', destinationPorts: ['80'], sourceCidrs: ['*'] }],
        },
      },
    );
    const wrapper = shallow(<div>{modal.render()}</div>);
    const body = shallow(<div>{wrapper.find(Modal.Body).prop('children')}</div>);
    const labels = body.find('label').map((label) => label.text());

    expect(labels).not.toContain('Account');
    expect(labels).not.toContain('Region');
    expect(labels).not.toContain('VNet');
    expect(labels).not.toContain('Subnet');
    expect(body.text()).toContain('Inbound Rules');
  });

  it('rejects missing or invalid source CIDRs and destination ports before submit', () => {
    const validSecurityGroup = {
      accountId: 'test-account',
      name: 'fnord-sg',
      region: 'westus',
      securityRules: [{ name: 'allow-web', destinationPorts: ['80', '443-445'], sourceCidrs: ['*', '10.0.0.0/24'] }],
    } as any;

    expect(isAzureSecurityGroupValid(validSecurityGroup)).toBe(true);
    expect(
      isAzureSecurityGroupValid({
        ...validSecurityGroup,
        securityRules: [{ destinationPorts: ['80'], sourceCidrs: ['0.0.0.0/0'] }],
      }),
    ).toBe(true);
    expect(
      isAzureSecurityGroupValid({
        ...validSecurityGroup,
        securityRules: [{ destinationPorts: [], sourceCidrs: ['*'] }],
      }),
    ).toBe(false);
    expect(
      isAzureSecurityGroupValid({
        ...validSecurityGroup,
        securityRules: [{ destinationPorts: ['99999'], sourceCidrs: ['*'] }],
      }),
    ).toBe(false);
    expect(
      isAzureSecurityGroupValid({
        ...validSecurityGroup,
        securityRules: [{ destinationPorts: ['443-80'], sourceCidrs: ['*'] }],
      }),
    ).toBe(false);
    expect(
      isAzureSecurityGroupValid({
        ...validSecurityGroup,
        securityRules: [{ destinationPorts: ['80'], sourceCidrs: ['10.0.0.999/24'] }],
      }),
    ).toBe(false);
    expect(
      isAzureSecurityGroupValid({
        ...validSecurityGroup,
        securityRules: [
          {
            destinationPortRangeModel: '443',
            destinationPorts: [],
            sourceAddressPrefixModel: '10.0.0.0/24',
            sourceCidrs: ['*'],
          },
        ],
      }),
    ).toBe(false);
    expect(
      isAzureSecurityGroupValid({
        ...validSecurityGroup,
        securityRules: [
          {
            destinationPortRangeModel: '443',
            destinationPorts: ['443'],
            sourceAddressPrefixModel: '10.0.0.0/24',
            sourceCidrs: [],
          },
        ],
      }),
    ).toBe(false);
  });

  it('waits for refreshed security groups and navigates to new details after create or clone completes', () => {
    spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({
      dismiss: () => null,
      result: Promise.resolve(),
    } as any);
    const state = { go: jasmine.createSpy('go'), includes: jasmine.createSpy('includes').and.returnValue(false) };
    const onNextRefresh = jasmine
      .createSpy('onNextRefresh')
      .and.callFake((_scope: any, callback: () => void) => callback());
    const closeModal = jasmine.createSpy('closeModal');
    const modal = new AzureSecurityGroupModal({
      app: { name: 'fnord', securityGroups: { onNextRefresh, refresh: jasmine.createSpy('refresh') } },
      closeModal,
      credentials: 'test-account',
      dismissModal: jasmine.createSpy('dismissModal'),
      mode: 'clone',
      securityGroup: { accountId: 'test-account', name: 'fnord-sg', region: 'westus', vpcId: 'vnet-1' },
      stateService: state,
    } as any) as any;

    modal.onTaskComplete();

    expect(onNextRefresh).toHaveBeenCalled();
    expect(closeModal).toHaveBeenCalled();
    expect(state.go).toHaveBeenCalledWith('.firewallDetails', {
      accountId: 'test-account',
      name: 'fnord-sg',
      provider: 'azure',
      region: 'westus',
      vpcId: 'vnet-1',
    });
  });
});
