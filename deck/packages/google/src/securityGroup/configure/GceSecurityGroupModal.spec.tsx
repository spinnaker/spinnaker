import React from 'react';
import { mount as enzymeMount } from 'enzyme';

import { DeckRuntimeContext, SecurityGroupWriter, SubmitButton } from '@spinnaker/core';

import { GceSecurityGroupModalComponent as GceSecurityGroupModal } from './GceSecurityGroupModal';

describe('GceSecurityGroupModal', () => {
  let runtimeServices: any;
  const RuntimeWrapper = ({ children }: React.PropsWithChildren<{}>) => (
    <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>{children}</DeckRuntimeContext.Provider>
  );
  const mount = (component: React.ReactElement) => enzymeMount(component, { wrappingComponent: RuntimeWrapper });

  beforeEach(() => {
    runtimeServices = { securityGroupReader: { getAllSecurityGroups: () => Promise.resolve({}) } };
  });

  const application = {
    name: 'my-app',
    securityGroups: { refresh: jasmine.createSpy('refresh') },
  };

  function validSecurityGroup(overrides: any = {}): any {
    return {
      accountId: 'my-account',
      accountName: 'my-account',
      credentials: 'my-account',
      ipIngress: [{ type: 'tcp', startPort: 443, endPort: 443 }],
      name: 'existing-firewall',
      network: 'default',
      sourceRanges: ['10.0.0.0/8'],
      sourceTags: [],
      targetTags: [],
      ...overrides,
    };
  }

  function globalSecurityGroups(): any {
    return {
      'my-account': {
        gce: {
          global: [{ name: 'existing-firewall', vpcId: 'other-network' }],
        },
      },
      'other-account': {
        gce: {
          global: [{ name: 'cross-account-firewall', vpcId: 'default' }],
        },
      },
    };
  }

  async function flush(): Promise<void> {
    await Promise.resolve();
    await Promise.resolve();
  }

  it('keeps create submit disabled until the global firewall inventory has loaded', async () => {
    let finishLoading: ((securityGroups: any) => void) | undefined;
    const getAllSecurityGroups = jasmine.createSpy('getAllSecurityGroups').and.returnValue(
      new Promise((resolve) => {
        finishLoading = resolve;
      }),
    );
    runtimeServices.securityGroupReader = { getAllSecurityGroups };
    const wrapper = mount(
      <GceSecurityGroupModal
        application={{ ...application, securityGroups: { data: [] } } as any}
        credentials="my-account"
      />,
    );
    wrapper.setState({ securityGroup: validSecurityGroup({ name: 'new-firewall' }) } as any);

    expect(wrapper.find(SubmitButton).prop('isDisabled')).toBe(true);

    finishLoading?.(globalSecurityGroups());
    await flush();
    wrapper.update();

    expect(wrapper.find(SubmitButton).prop('isDisabled')).toBe(false);
  });

  (['create', 'clone'] as const).forEach((mode) => {
    it(`rejects a ${mode} name used in the same account on another network outside the application`, async () => {
      const getAllSecurityGroups = jasmine
        .createSpy('getAllSecurityGroups')
        .and.returnValue(Promise.resolve(globalSecurityGroups()));
      runtimeServices.securityGroupReader = { getAllSecurityGroups };
      const wrapper = mount(
        <GceSecurityGroupModal
          application={{ ...application, securityGroups: { data: [] } } as any}
          credentials="my-account"
          mode={mode}
          securityGroup={mode === 'clone' ? validSecurityGroup({ name: 'source-firewall' }) : undefined}
        />,
      );
      wrapper.setState({ securityGroup: validSecurityGroup() } as any);

      await flush();
      wrapper.update();

      expect(getAllSecurityGroups).toHaveBeenCalled();
      expect(wrapper.find(SubmitButton).prop('isDisabled')).toBe(true);
    });
  });

  it('permits a name used only in another account and permits editing the current firewall identity', async () => {
    const getAllSecurityGroups = jasmine
      .createSpy('getAllSecurityGroups')
      .and.returnValue(Promise.resolve(globalSecurityGroups()));
    runtimeServices.securityGroupReader = { getAllSecurityGroups };
    const createWrapper = mount(<GceSecurityGroupModal application={application as any} credentials="my-account" />);
    createWrapper.setState({ securityGroup: validSecurityGroup({ name: 'cross-account-firewall' }) } as any);
    const editWrapper = mount(
      <GceSecurityGroupModal application={application as any} mode="edit" securityGroup={validSecurityGroup()} />,
    );

    await flush();
    createWrapper.update();
    editWrapper.update();

    expect(createWrapper.find(SubmitButton).prop('isDisabled')).toBe(false);
    expect(editWrapper.find(SubmitButton).prop('isDisabled')).toBe(false);
  });

  it('keeps create submit disabled when the global firewall inventory cannot be loaded', async () => {
    const getAllSecurityGroups = jasmine
      .createSpy('getAllSecurityGroups')
      .and.returnValue(Promise.reject(new Error('inventory unavailable')));
    runtimeServices.securityGroupReader = { getAllSecurityGroups };
    const wrapper = mount(<GceSecurityGroupModal application={application as any} credentials="my-account" />);
    wrapper.setState({ securityGroup: validSecurityGroup({ name: 'new-firewall' }) } as any);

    await flush();
    wrapper.update();

    expect(wrapper.find(SubmitButton).prop('isDisabled')).toBe(true);
    expect(wrapper.find('.security-group-validation-error').text()).toContain('Unable to validate firewall name');
  });

  (['create', 'clone'] as const).forEach((mode) => {
    it(`waits for refreshed security groups before closing and navigating after ${mode}`, () => {
      let finishRefresh: (() => void) | undefined;
      const refresh = jasmine.createSpy('refresh');
      const onNextRefresh = jasmine
        .createSpy('onNextRefresh')
        .and.callFake((_scope: any, callback: () => void) => (finishRefresh = callback));
      const closeModal = jasmine.createSpy('closeModal');
      const state = {
        go: jasmine.createSpy('go'),
        includes: jasmine.createSpy('includes').and.returnValue(mode === 'clone'),
      };
      const modal = new GceSecurityGroupModal({
        application: { name: 'my-app', securityGroups: { onNextRefresh, refresh } },
        closeModal,
        credentials: 'my-account',
        mode,
        router: {},
        securityGroup: mode === 'clone' ? validSecurityGroup({ name: 'source-firewall' }) : undefined,
        stateParams: {},
        stateService: state,
      } as any);
      (modal.state as any).securityGroup = validSecurityGroup({ name: ' new-firewall ', network: ' default ' });

      (modal as any).onTaskComplete();

      expect(onNextRefresh).toHaveBeenCalled();
      expect(onNextRefresh.calls.first().invocationOrder).toBeLessThan(refresh.calls.first().invocationOrder);
      expect(closeModal).not.toHaveBeenCalled();
      expect(state.go).not.toHaveBeenCalled();

      finishRefresh?.();

      expect(closeModal).toHaveBeenCalled();
      expect(state.go).toHaveBeenCalledWith(mode === 'clone' ? '^.firewallDetails' : '.firewallDetails', {
        accountId: 'my-account',
        name: 'new-firewall',
        provider: 'gce',
        region: 'global',
        vpcId: 'default',
      });
    });
  });

  it('submits multiple protocol and port-range rows using the GCE firewall operation contract', () => {
    const upsert = spyOn(SecurityGroupWriter, 'upsertSecurityGroup').and.returnValue(new Promise(() => {}));
    const modal = new GceSecurityGroupModal({ application: application as any, credentials: 'my-account' } as any);
    (modal.state as any).securityGroup = {
      ...(modal.state as any).securityGroup,
      name: 'my-app-firewall',
      network: 'default',
      sourceRanges: ['10.0.0.0/8'],
      ipIngress: [
        { type: 'tcp', startPort: 443, endPort: 443 },
        { type: 'udp', startPort: 7001, endPort: 7002 },
        { type: 'icmp' },
      ],
    };

    (modal as any).submit();

    expect(upsert.calls.mostRecent().args[0].allowed).toEqual([
      { ipProtocol: 'tcp', portRanges: ['443-443'] },
      { ipProtocol: 'udp', portRanges: ['7001-7002'] },
      { ipProtocol: 'icmp' },
    ]);
    expect(upsert.calls.mostRecent().args[2]).toBe('Create');
  });

  it('loads and normalizes all inbound rules for edit while retaining firewall identity', () => {
    const source = {
      accountId: 'my-account',
      accountName: 'my-account',
      credentials: 'my-account',
      id: 'my-firewall',
      ipIngressRules: [
        {
          protocol: 'tcp',
          portRanges: [
            { startPort: 80, endPort: 80 },
            { startPort: 443, endPort: 444 },
          ],
        },
        { protocol: 'icmp', portRanges: [] },
      ],
      name: 'my-firewall',
      network: 'default',
      region: 'global',
      sourceRanges: ['10.0.0.0/8'],
      sourceTags: '[backend, jobs]',
      targetTags: '[web, api]',
    };
    const upsert = spyOn(SecurityGroupWriter, 'upsertSecurityGroup').and.returnValue(new Promise(() => {}));
    const modal = new GceSecurityGroupModal({
      application: application as any,
      mode: 'edit',
      securityGroup: source,
    } as any);

    expect((modal.state as any).securityGroup).toEqual(
      jasmine.objectContaining({
        accountId: 'my-account',
        credentials: 'my-account',
        id: 'my-firewall',
        ipIngress: [
          { type: 'tcp', startPort: 80, endPort: 80 },
          { type: 'tcp', startPort: 443, endPort: 444 },
          { type: 'icmp' },
        ],
        name: 'my-firewall',
        sourceRanges: ['10.0.0.0/8'],
        sourceTags: ['backend', 'jobs'],
        targetTags: ['web', 'api'],
      }),
    );

    (modal as any).submit();

    expect(upsert.calls.mostRecent().args[0]).toEqual(
      jasmine.objectContaining({
        accountId: 'my-account',
        id: 'my-firewall',
        name: 'my-firewall',
      }),
    );
    expect(upsert.calls.mostRecent().args[0].allowed).toEqual([
      { ipProtocol: 'tcp', portRanges: ['80-80'] },
      { ipProtocol: 'tcp', portRanges: ['443-444'] },
      { ipProtocol: 'icmp' },
    ]);
    expect(upsert.calls.mostRecent().args[2]).toBe('Update');
  });

  it('preserves fetched firewall CIDRs from ipRangeRules without duplicating sourceRanges', () => {
    const upsert = spyOn(SecurityGroupWriter, 'upsertSecurityGroup').and.returnValue(new Promise(() => {}));
    const modal = new GceSecurityGroupModal({
      application: application as any,
      mode: 'edit',
      securityGroup: {
        accountName: 'my-account',
        id: 'fetched-firewall',
        ipRangeRules: [
          {
            portRanges: [{ startPort: 443, endPort: 443 }],
            protocol: 'tcp',
            range: { ip: '10.0.0.0', cidr: '/8' },
          },
          {
            portRanges: [{ startPort: 53, endPort: 53 }],
            protocol: 'udp',
            range: { ip: '192.168.0.0', cidr: '/24' },
          },
        ],
        name: 'fetched-firewall',
        network: 'default',
        sourceRanges: ['10.0.0.0/8'],
      },
    } as any);

    expect((modal.state as any).securityGroup.sourceRanges).toEqual(['10.0.0.0/8', '192.168.0.0/24']);

    (modal as any).submit();

    expect(upsert.calls.mostRecent().args[0].sourceRanges).toEqual(['10.0.0.0/8', '192.168.0.0/24']);
  });

  it('clears cloned firewall identity and name but preserves its editable rules', () => {
    const upsert = spyOn(SecurityGroupWriter, 'upsertSecurityGroup').and.returnValue(new Promise(() => {}));
    const modal = new GceSecurityGroupModal({
      application: application as any,
      mode: 'clone',
      securityGroup: {
        accountId: 'my-account',
        id: 'source-firewall',
        ipIngressRules: [{ protocol: 'sctp', portRanges: [{ startPort: 5000, endPort: 5001 }] }],
        name: 'source-firewall',
        network: 'default',
        sourceRanges: ['10.0.0.0/8'],
      },
    } as any);

    expect((modal.state as any).securityGroup).toEqual(
      jasmine.objectContaining({
        id: undefined,
        ipIngress: [{ type: 'sctp', startPort: 5000, endPort: 5001 }],
        name: '',
      }),
    );

    (modal.state as any).securityGroup.name = 'cloned-firewall';
    (modal as any).submit();

    expect(upsert.calls.mostRecent().args[0]).toEqual(
      jasmine.objectContaining({
        allowed: [{ ipProtocol: 'sctp', portRanges: ['5000-5001'] }],
        id: undefined,
        name: 'cloned-firewall',
      }),
    );
    expect(upsert.calls.mostRecent().args[2]).toBe('Clone');
  });
});
