import React from 'react';
import { mount as enzymeMount, shallow } from 'enzyme';

import { AccountService, ConfirmationModalService, DeckRuntimeContext, SecurityGroupWriter } from '@spinnaker/core';

import { GceSecurityGroupModal } from '../configure/GceSecurityGroupModal';
import { GceSecurityGroupActions, GceSecurityGroupDetails } from './GceSecurityGroupDetails';

describe('GceSecurityGroupDetails', () => {
  let runtimeServices: any;
  const RuntimeWrapper = ({ children }: React.PropsWithChildren<{}>) => (
    <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>{children}</DeckRuntimeContext.Provider>
  );
  const mount = (component: React.ReactElement) => enzymeMount(component, { wrappingComponent: RuntimeWrapper });

  beforeEach(() => {
    runtimeServices = {};
    spyOn(AccountService, 'challengeDestructiveActions').and.resolveTo(false);
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

  async function flush(): Promise<void> {
    await Promise.resolve();
    await Promise.resolve();
  }

  it('reloads the current firewall details after the security group data source refreshes', async () => {
    let refreshDetails: (() => void) | undefined;
    const app = {
      securityGroups: {
        onRefresh: jasmine.createSpy('onRefresh').and.callFake((_scope: any, callback: () => void) => {
          refreshDetails = callback;
          return jasmine.createSpy('unsubscribe');
        }),
      },
    };
    const reader = {
      getSecurityGroupDetails: jasmine
        .createSpy('getSecurityGroupDetails')
        .and.returnValues(
          Promise.resolve(details('first-firewall', 'before refresh')),
          Promise.resolve(details('first-firewall', 'after refresh')),
        ),
    };
    runtimeServices.securityGroupReader = reader;
    const wrapper = mount(<GceSecurityGroupDetails app={app as any} resolvedSecurityGroup={firstRoute} />);

    await flush();
    wrapper.update();
    expect(wrapper.text()).toContain('before refresh');
    expect(refreshDetails).toBeDefined();

    refreshDetails?.();
    await flush();
    wrapper.update();

    expect(reader.getSecurityGroupDetails).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('after refresh');
    wrapper.unmount();
  });

  it('clears prior details and actions on route change and ignores an out-of-order response', async () => {
    const secondRequest = deferred<any>();
    const thirdRequest = deferred<any>();
    const reader = {
      getSecurityGroupDetails: jasmine
        .createSpy('getSecurityGroupDetails')
        .and.returnValues(
          Promise.resolve(details('first-firewall', 'first details')),
          secondRequest.promise,
          thirdRequest.promise,
        ),
    };
    runtimeServices.securityGroupReader = reader;
    const app = { securityGroups: { onRefresh: () => jasmine.createSpy('unsubscribe') } };
    const wrapper = mount(<GceSecurityGroupDetails app={app as any} resolvedSecurityGroup={firstRoute} />);

    await flush();
    wrapper.update();
    expect(wrapper.find(GceSecurityGroupActions).length).toBe(1);
    expect(wrapper.text()).toContain('first details');

    wrapper.setProps({ resolvedSecurityGroup: { ...firstRoute, name: 'second-firewall' } });
    expect(wrapper.find(GceSecurityGroupActions).length).toBe(0);
    expect(wrapper.text()).not.toContain('first details');

    wrapper.setProps({ resolvedSecurityGroup: { ...firstRoute, name: 'third-firewall' } });
    secondRequest.resolve(details('second-firewall', 'stale second details'));
    await flush();
    wrapper.update();
    expect(wrapper.text()).not.toContain('stale second details');

    thirdRequest.resolve(details('third-firewall', 'current third details'));
    await flush();
    wrapper.update();
    expect(wrapper.text()).toContain('current third details');
    expect(wrapper.find(GceSecurityGroupActions).length).toBe(1);
    wrapper.unmount();
  });
});

describe('GceSecurityGroupActions', () => {
  const runtimeServices = {} as any;
  const RuntimeWrapper = ({ children }: React.PropsWithChildren<{}>) => (
    <DeckRuntimeContext.Provider value={{ services: runtimeServices }}>{children}</DeckRuntimeContext.Provider>
  );
  const mountActions = (component: React.ReactElement) => enzymeMount(component, { wrappingComponent: RuntimeWrapper });

  const app = {
    name: 'my-app',
    securityGroups: { refresh: jasmine.createSpy('refresh') },
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
    spyOn(GceSecurityGroupModal, 'show');
    spyOn(ConfirmationModalService, 'confirm');
    spyOn(SecurityGroupWriter, 'deleteSecurityGroup').and.returnValue(Promise.resolve({} as any));
    const wrapper = mountActions(
      <GceSecurityGroupActions
        app={app as any}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroup={securityGroup}
      />,
    );
    const items = wrapper.find('button[data-action]');

    expect(items.map((item) => item.text())).toEqual(['Edit Inbound Rules', 'Clone Firewall', 'Delete Firewall']);
    items.at(0).prop('onClick')({} as any);
    items.at(1).prop('onClick')({} as any);
    items.at(2).prop('onClick')({} as any);

    const firewallWithIdentity = jasmine.objectContaining({
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
      jasmine.objectContaining({
        account: 'my-account',
        buttonText: 'Delete my-firewall',
        header: 'Really delete my-firewall?',
        taskMonitorConfig: jasmine.objectContaining({ application: app as any, title: 'Deleting my-firewall' }),
      }),
    );

    const confirmation = (ConfirmationModalService.confirm as jasmine.Spy).calls.mostRecent().args[0];
    confirmation.submitMethod();
    expect(SecurityGroupWriter.deleteSecurityGroup).toHaveBeenCalledWith(
      firewallWithIdentity,
      app as any,
      jasmine.objectContaining({ cloudProvider: 'gce', securityGroupName: 'my-firewall' }),
    );
  });

  it('disables host-project shared-VPC actions and explains why they are read-only', () => {
    const wrapper = mountActions(
      <GceSecurityGroupActions
        app={app as any}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroup={{ ...securityGroup, id: 'host-project/my-firewall' }}
      />,
    );

    expect(wrapper.find('button[data-action]').length).toBe(3);
    wrapper.find('button[data-action]').forEach((item) => expect(item.prop('disabled')).toBe(true));
    expect(wrapper.find('.shared-vpc-warning').text()).toContain(
      'You cannot modify shared VPC host project firewall rules.',
    );
  });
});
