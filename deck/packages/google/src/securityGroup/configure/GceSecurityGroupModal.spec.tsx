import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { UIRouterContext, UIRouterReact } from '@uirouter/react';
import React from 'react';

import { DeckRuntimeContext, SecurityGroupWriter, TaskReader } from '@spinnaker/core';

import {
  GceSecurityGroupModalComponent as GceSecurityGroupModal,
  initializeGceSecurityGroupForModal,
} from './GceSecurityGroupModal';

describe('GceSecurityGroupModal', () => {
  let runtimeServices: any;
  const application = {
    getDataSource: vi.fn(),
    name: 'my-app',
    securityGroups: { refresh: vi.fn() },
  };

  beforeEach(() => {
    runtimeServices = { securityGroupReader: { getAllSecurityGroups: () => Promise.resolve({}) } };
  });

  function renderModal(component: React.ReactElement) {
    const router = new UIRouterReact();
    return render(
      <UIRouterContext.Provider value={router}>
        <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>
          {component}
        </DeckRuntimeContext.Provider>
      </UIRouterContext.Provider>,
    );
  }

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

  function fillValidSecurityGroup(name = 'new-firewall'): void {
    fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: name } });
    fireEvent.change(screen.getByRole('textbox', { name: 'Source CIDRs' }), {
      target: { value: '10.0.0.0/8' },
    });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Start port 1' }), { target: { value: '443' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'End port 1' }), { target: { value: '443' } });
  }

  it('keeps create submit disabled until the global firewall inventory has loaded', async () => {
    let finishLoading: ((securityGroups: any) => void) | undefined;
    const getAllSecurityGroups = vi.fn().mockReturnValue(
      new Promise((resolve) => {
        finishLoading = resolve;
      }),
    );
    runtimeServices.securityGroupReader = { getAllSecurityGroups };
    renderModal(
      <GceSecurityGroupModal
        application={{ ...application, securityGroups: { data: [] } } as any}
        credentials="my-account"
      />,
    );
    fillValidSecurityGroup();

    expect(screen.getByRole('button', { name: 'Create' })).toBeDisabled();

    await act(async () => finishLoading?.(globalSecurityGroups()));

    await waitFor(() => expect(screen.getByRole('button', { name: 'Create' })).toBeEnabled());
  });

  (['create', 'clone'] as const).forEach((mode) => {
    it(`rejects a ${mode} name used in the same account on another network outside the application`, async () => {
      const getAllSecurityGroups = vi.fn().mockResolvedValue(globalSecurityGroups());
      runtimeServices.securityGroupReader = { getAllSecurityGroups };
      renderModal(
        <GceSecurityGroupModal
          application={{ ...application, securityGroups: { data: [] } } as any}
          credentials="my-account"
          mode={mode}
          securityGroup={mode === 'clone' ? validSecurityGroup({ name: 'source-firewall' }) : undefined}
        />,
      );
      fillValidSecurityGroup('existing-firewall');

      await waitFor(() => expect(getAllSecurityGroups).toHaveBeenCalled());

      expect(screen.getByRole('button', { name: mode === 'clone' ? 'Clone' : 'Create' })).toBeDisabled();
    });
  });

  it('permits a name used only in another account and permits editing the current firewall identity', async () => {
    const getAllSecurityGroups = vi.fn().mockResolvedValue(globalSecurityGroups());
    runtimeServices.securityGroupReader = { getAllSecurityGroups };
    const create = renderModal(<GceSecurityGroupModal application={application as any} credentials="my-account" />);
    fillValidSecurityGroup('cross-account-firewall');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Create' })).toBeEnabled());
    create.unmount();

    renderModal(
      <GceSecurityGroupModal application={application as any} mode="edit" securityGroup={validSecurityGroup()} />,
    );
    expect(screen.getByRole('button', { name: 'Update' })).toBeEnabled();
  });

  it('keeps create submit disabled when the global firewall inventory cannot be loaded', async () => {
    const getAllSecurityGroups = vi.fn().mockRejectedValue(new Error('inventory unavailable'));
    runtimeServices.securityGroupReader = { getAllSecurityGroups };
    renderModal(<GceSecurityGroupModal application={application as any} credentials="my-account" />);
    fillValidSecurityGroup();

    expect(await screen.findByText('Unable to validate firewall name.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create' })).toBeDisabled();
  });

  (['create', 'clone'] as const).forEach((mode) => {
    it(`waits for refreshed security groups before closing and navigating after ${mode}`, async () => {
      let finishRefresh: (() => void) | undefined;
      const refresh = vi.fn();
      const onNextRefresh = vi.fn().mockImplementation((callback: () => void) => {
        finishRefresh = callback;
        return () => undefined;
      });
      const closeModal = vi.fn();
      const stateService = {
        go: vi.fn(),
        includes: vi.fn().mockReturnValue(mode === 'clone'),
      };
      const task = { id: 'task' } as any;
      vi.spyOn(SecurityGroupWriter, 'upsertSecurityGroup').mockResolvedValue(task);
      vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue(task);
      renderModal(
        <GceSecurityGroupModal
          application={{ getDataSource: vi.fn(), name: 'my-app', securityGroups: { onNextRefresh, refresh } }}
          closeModal={closeModal}
          credentials="my-account"
          mode={mode}
          securityGroup={mode === 'clone' ? validSecurityGroup({ name: 'source-firewall' }) : undefined}
          stateService={stateService as any}
        />,
      );
      fillValidSecurityGroup(' new-firewall ');
      fireEvent.change(screen.getByRole('textbox', { name: 'Network' }), { target: { value: ' default ' } });

      await waitFor(() =>
        expect(screen.getByRole('button', { name: mode === 'clone' ? 'Clone' : 'Create' })).toBeEnabled(),
      );
      fireEvent.click(screen.getByRole('button', { name: mode === 'clone' ? 'Clone' : 'Create' }));
      await waitFor(() => expect(refresh).toHaveBeenCalled());

      expect(onNextRefresh.mock.invocationCallOrder[0]).toBeLessThan(refresh.mock.invocationCallOrder[0]);
      expect(closeModal).not.toHaveBeenCalled();
      expect(stateService.go).not.toHaveBeenCalled();

      finishRefresh?.();

      expect(closeModal).toHaveBeenCalled();
      expect(stateService.go).toHaveBeenCalledWith(mode === 'clone' ? '^.firewallDetails' : '.firewallDetails', {
        accountId: 'my-account',
        name: 'new-firewall',
        provider: 'gce',
        region: 'global',
        vpcId: 'default',
      });
    });
  });

  it('owns its refresh subscription through unmount', async () => {
    const unsubscribe = vi.fn();
    const callbacks: Array<() => void> = [];
    const onNextRefresh = vi.fn().mockImplementation((callback: () => void) => {
      callbacks.push(callback);
      return unsubscribe;
    });
    const refresh = vi.fn();
    const closeModal = vi.fn();
    const stateService = { go: vi.fn(), includes: vi.fn() };
    const task = { id: 'task' } as any;
    vi.spyOn(SecurityGroupWriter, 'upsertSecurityGroup').mockResolvedValue(task);
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue(task);
    const rendered = renderModal(
      <GceSecurityGroupModal
        application={{ getDataSource: vi.fn(), name: 'my-app', securityGroups: { onNextRefresh, refresh } }}
        closeModal={closeModal}
        credentials="my-account"
        stateService={stateService as any}
      />,
    );
    fillValidSecurityGroup();

    await waitFor(() => expect(screen.getByRole('button', { name: 'Create' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Create' }));
    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(onNextRefresh.mock.invocationCallOrder[0]).toBeLessThan(refresh.mock.invocationCallOrder[0]);

    rendered.unmount();
    callbacks[0]();

    expect(unsubscribe).toHaveBeenCalledTimes(1);
    expect(closeModal).not.toHaveBeenCalled();
    expect(stateService.go).not.toHaveBeenCalled();
  });

  it('submits multiple protocol and port-range rows using the GCE firewall operation contract', async () => {
    runtimeServices.securityGroupReader = { getAllSecurityGroups: vi.fn().mockResolvedValue({}) };
    const upsert = vi.spyOn(SecurityGroupWriter, 'upsertSecurityGroup').mockReturnValue(new Promise(() => undefined));
    renderModal(<GceSecurityGroupModal application={application as any} credentials="my-account" />);
    fillValidSecurityGroup('my-app-firewall');
    fireEvent.click(screen.getByRole('button', { name: /Add New Protocol and Port Range/ }));
    fireEvent.change(screen.getByRole('combobox', { name: 'Protocol 2' }), { target: { value: 'udp' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Start port 2' }), { target: { value: '7001' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'End port 2' }), { target: { value: '7002' } });
    fireEvent.click(screen.getByRole('button', { name: /Add New Protocol and Port Range/ }));
    fireEvent.change(screen.getByRole('combobox', { name: 'Protocol 3' }), { target: { value: 'icmp' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Start port 3' }), { target: { value: '' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'End port 3' }), { target: { value: '' } });

    await waitFor(() => expect(screen.getByRole('button', { name: 'Create' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Create' }));

    expect(upsert.mock.lastCall[0].allowed).toEqual([
      { ipProtocol: 'tcp', portRanges: ['443-443'] },
      { ipProtocol: 'udp', portRanges: ['7001-7002'] },
      { ipProtocol: 'icmp' },
    ]);
    expect(upsert.mock.lastCall[2]).toBe('Create');
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
    expect(initializeGceSecurityGroupForModal({ mode: 'edit', securityGroup: source }, application.name)).toEqual(
      expect.objectContaining({
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
    const upsert = vi.spyOn(SecurityGroupWriter, 'upsertSecurityGroup').mockReturnValue(new Promise(() => undefined));
    renderModal(<GceSecurityGroupModal application={application as any} mode="edit" securityGroup={source} />);

    fireEvent.click(screen.getByRole('button', { name: 'Update' }));

    expect(upsert.mock.lastCall[0]).toEqual(
      expect.objectContaining({ accountId: 'my-account', id: 'my-firewall', name: 'my-firewall' }),
    );
    expect(upsert.mock.lastCall[0].allowed).toEqual([
      { ipProtocol: 'tcp', portRanges: ['80-80'] },
      { ipProtocol: 'tcp', portRanges: ['443-444'] },
      { ipProtocol: 'icmp' },
    ]);
    expect(upsert.mock.lastCall[2]).toBe('Update');
  });

  it('preserves fetched firewall CIDRs from ipRangeRules without duplicating sourceRanges', () => {
    const source = {
      accountName: 'my-account',
      id: 'fetched-firewall',
      ipRangeRules: [
        { portRanges: [{ startPort: 443, endPort: 443 }], protocol: 'tcp', range: { ip: '10.0.0.0', cidr: '/8' } },
        { portRanges: [{ startPort: 53, endPort: 53 }], protocol: 'udp', range: { ip: '192.168.0.0', cidr: '/24' } },
      ],
      name: 'fetched-firewall',
      network: 'default',
      sourceRanges: ['10.0.0.0/8'],
    };
    expect(
      initializeGceSecurityGroupForModal({ mode: 'edit', securityGroup: source }, application.name).sourceRanges,
    ).toEqual(['10.0.0.0/8', '192.168.0.0/24']);
    const upsert = vi.spyOn(SecurityGroupWriter, 'upsertSecurityGroup').mockReturnValue(new Promise(() => undefined));
    renderModal(<GceSecurityGroupModal application={application as any} mode="edit" securityGroup={source} />);

    fireEvent.click(screen.getByRole('button', { name: 'Update' }));

    expect(upsert.mock.lastCall[0].sourceRanges).toEqual(['10.0.0.0/8', '192.168.0.0/24']);
  });

  it('clears cloned firewall identity and name but preserves its editable rules', async () => {
    const source = {
      accountId: 'my-account',
      id: 'source-firewall',
      ipIngressRules: [{ protocol: 'sctp', portRanges: [{ startPort: 5000, endPort: 5001 }] }],
      name: 'source-firewall',
      network: 'default',
      sourceRanges: ['10.0.0.0/8'],
    };
    expect(initializeGceSecurityGroupForModal({ mode: 'clone', securityGroup: source }, application.name)).toEqual(
      expect.objectContaining({
        id: undefined,
        ipIngress: [{ type: 'sctp', startPort: 5000, endPort: 5001 }],
        name: '',
      }),
    );
    runtimeServices.securityGroupReader = { getAllSecurityGroups: vi.fn().mockResolvedValue({}) };
    const upsert = vi.spyOn(SecurityGroupWriter, 'upsertSecurityGroup').mockReturnValue(new Promise(() => undefined));
    renderModal(<GceSecurityGroupModal application={application as any} mode="clone" securityGroup={source} />);
    fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'cloned-firewall' } });

    await waitFor(() => expect(screen.getByRole('button', { name: 'Clone' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Clone' }));

    expect(upsert.mock.lastCall[0]).toEqual(
      expect.objectContaining({
        allowed: [{ ipProtocol: 'sctp', portRanges: ['5000-5001'] }],
        id: undefined,
        name: 'cloned-firewall',
      }),
    );
    expect(upsert.mock.lastCall[2]).toBe('Clone');
  });
});
