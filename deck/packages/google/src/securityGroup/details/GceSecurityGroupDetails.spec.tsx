import type { Mock } from 'vitest';
import React from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';

import { AccountService, ConfirmationModalService, DeckRuntimeContext, SecurityGroupWriter } from '@spinnaker/core';

import { GceSecurityGroupModal } from '../configure/GceSecurityGroupModal';
import { GceSecurityGroupActions, GceSecurityGroupDetails } from './GceSecurityGroupDetails';

describe('GceSecurityGroupDetails', () => {
  let runtimeServices: any;
  const RuntimeWrapper = ({ children }: React.PropsWithChildren<{}>) => (
    <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>{children}</DeckRuntimeContext.Provider>
  );
  const renderWithRuntime = (component: React.ReactElement) => render(<RuntimeWrapper>{component}</RuntimeWrapper>);

  beforeEach(() => {
    runtimeServices = {};
    vi.spyOn(AccountService, 'challengeDestructiveActions').mockResolvedValue(false);
  });

  const firstRoute = {
    accountId: 'my-account',
    name: 'first-firewall',
    provider: 'gce',
    region: 'global',
    vpcId: 'default',
  };

  function details(name: string, description: string): any {
    return {
      accountName: 'my-account',
      description,
      id: name,
      inboundRules: [{ protocol: 'tcp', portRanges: [{ startPort: 443, endPort: 443 }] }],
      name,
      network: 'default',
      sourceRanges: ['10.0.0.0/8'],
    };
  }

  function deferred<T>(): { promise: Promise<T>; resolve: (value: T) => void } {
    let resolve!: (value: T) => void;
    return {
      promise: new Promise<T>((promiseResolve) => {
        resolve = promiseResolve;
      }),
      resolve,
    };
  }

  it('reloads the current firewall details after the security group data source refreshes', async () => {
    let refreshDetails: (() => void) | undefined;
    const app = {
      securityGroups: {
        onRefresh: vi.fn().mockImplementation((callback: () => void) => {
          refreshDetails = callback;
          return vi.fn();
        }),
      },
    };
    const reader = {
      getSecurityGroupDetails: vi
        .fn()
        .mockReturnValueOnce(Promise.resolve(details('first-firewall', 'before refresh')))
        .mockReturnValueOnce(Promise.resolve(details('first-firewall', 'after refresh'))),
    };
    runtimeServices.securityGroupReader = reader;
    const rendered = renderWithRuntime(<GceSecurityGroupDetails app={app as any} resolvedSecurityGroup={firstRoute} />);

    await waitFor(() => expect(screen.getByText('before refresh')).toBeInTheDocument());
    expect(refreshDetails).toBeDefined();

    await act(async () => refreshDetails?.());

    expect(reader.getSecurityGroupDetails).toHaveBeenCalledTimes(2);
    await waitFor(() => expect(screen.getByText('after refresh')).toBeInTheDocument());
    rendered.unmount();
  });

  it('clears prior details and actions on route change and ignores an out-of-order response', async () => {
    const secondRequest = deferred<any>();
    const thirdRequest = deferred<any>();
    const reader = {
      getSecurityGroupDetails: vi
        .fn()
        .mockReturnValueOnce(Promise.resolve(details('first-firewall', 'first details')))
        .mockReturnValueOnce(secondRequest.promise)
        .mockReturnValueOnce(thirdRequest.promise),
    };
    runtimeServices.securityGroupReader = reader;
    const app = { securityGroups: { onRefresh: () => vi.fn() } };
    const rendered = renderWithRuntime(<GceSecurityGroupDetails app={app as any} resolvedSecurityGroup={firstRoute} />);

    await waitFor(() => expect(screen.getByText('first details')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: /Firewall Actions/ })).toBeInTheDocument();

    rendered.rerender(
      <RuntimeWrapper>
        <GceSecurityGroupDetails app={app as any} resolvedSecurityGroup={{ ...firstRoute, name: 'second-firewall' }} />
      </RuntimeWrapper>,
    );
    expect(screen.queryByRole('button', { name: /Firewall Actions/ })).not.toBeInTheDocument();
    expect(screen.queryByText('first details')).not.toBeInTheDocument();

    rendered.rerender(
      <RuntimeWrapper>
        <GceSecurityGroupDetails app={app as any} resolvedSecurityGroup={{ ...firstRoute, name: 'third-firewall' }} />
      </RuntimeWrapper>,
    );
    await act(async () => secondRequest.resolve(details('second-firewall', 'stale second details')));
    expect(screen.queryByText('stale second details')).not.toBeInTheDocument();

    await act(async () => thirdRequest.resolve(details('third-firewall', 'current third details')));
    await waitFor(() => expect(screen.getByText('current third details')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: /Firewall Actions/ })).toBeInTheDocument();
    rendered.unmount();
  });
});

describe('GceSecurityGroupActions', () => {
  const runtimeServices = {} as any;
  const RuntimeWrapper = ({ children }: React.PropsWithChildren<{}>) => (
    <DeckRuntimeContext.Provider value={{ services: runtimeServices }}>{children}</DeckRuntimeContext.Provider>
  );
  const renderActions = (component: React.ReactElement) => render(<RuntimeWrapper>{component}</RuntimeWrapper>);

  const app = {
    name: 'my-app',
    securityGroups: { refresh: vi.fn() },
  };
  const resolvedSecurityGroup = {
    accountId: 'my-account',
    name: 'my-firewall',
    provider: 'gce',
    region: 'global',
    vpcId: 'default',
  };
  const securityGroup = {
    id: 'my-firewall',
    ipIngressRules: [{ protocol: 'tcp', portRanges: [{ startPort: 443, endPort: 443 }] }],
    name: 'my-firewall',
    network: 'default',
    sourceRanges: ['10.0.0.0/8'],
    type: 'gce',
  };

  it('opens edit and clone modals and confirms deletion with complete firewall identity', () => {
    vi.spyOn(GceSecurityGroupModal, 'show').mockReturnValue(undefined);
    vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(undefined);
    vi.spyOn(SecurityGroupWriter, 'deleteSecurityGroup').mockReturnValue(Promise.resolve({} as any));
    const rendered = renderActions(
      <GceSecurityGroupActions
        app={app as any}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroup={securityGroup}
      />,
    );
    const items = Array.from(rendered.container.querySelectorAll<HTMLButtonElement>('button[data-action]'));

    expect(items.map((item) => item.textContent?.trim())).toEqual([
      'Edit Inbound Rules',
      'Clone Firewall',
      'Delete Firewall',
    ]);
    items.forEach((item) => fireEvent.click(item));

    const firewallWithIdentity = expect.objectContaining({
      accountId: 'my-account',
      id: 'my-firewall',
      name: 'my-firewall',
      region: 'global',
      vpcId: 'default',
    });
    expect(GceSecurityGroupModal.show).toHaveBeenCalledWith(
      {
        application: app as any,
        mode: 'edit',
        securityGroup: firewallWithIdentity,
      },
      runtimeServices,
    );
    expect(GceSecurityGroupModal.show).toHaveBeenCalledWith(
      {
        application: app as any,
        mode: 'clone',
        securityGroup: firewallWithIdentity,
      },
      runtimeServices,
    );
    expect(ConfirmationModalService.confirm).toHaveBeenCalledWith(
      expect.objectContaining({
        account: 'my-account',
        buttonText: 'Delete my-firewall',
        header: 'Really delete my-firewall?',
        taskMonitorConfig: expect.objectContaining({ application: app as any, title: 'Deleting my-firewall' }),
      }),
    );

    const confirmation = (ConfirmationModalService.confirm as Mock).mock.lastCall[0];
    confirmation.submitMethod();
    expect(SecurityGroupWriter.deleteSecurityGroup).toHaveBeenCalledWith(
      firewallWithIdentity,
      app as any,
      expect.objectContaining({ cloudProvider: 'gce', securityGroupName: 'my-firewall' }),
    );
  });

  it('disables host-project shared-VPC actions and explains why they are read-only', () => {
    const rendered = renderActions(
      <GceSecurityGroupActions
        app={app as any}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroup={{ ...securityGroup, id: 'host-project/my-firewall' }}
      />,
    );

    const items = Array.from(rendered.container.querySelectorAll<HTMLButtonElement>('button[data-action]'));
    expect(items).toHaveLength(3);
    items.forEach((item) => expect(item).toBeDisabled());
    expect(screen.getByText('You cannot modify shared VPC host project firewall rules.')).toBeInTheDocument();
  });
});
