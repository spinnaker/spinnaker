import { TaskMonitor } from '@spinnaker/core';

import { AzureLoadBalancerModalComponent } from './loadBalancer/configure/AzureLoadBalancerModal';
import { AzureCloneServerGroupModalComponent } from './serverGroup/configure/wizard/AzureCloneServerGroupModal';

describe('Azure routed modal consumers', () => {
  beforeEach(() => {
    spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({
      dismiss: jasmine.createSpy('dismiss'),
      result: Promise.resolve(),
    } as any);
  });

  function stateService(includedState: string) {
    return {
      go: jasmine.createSpy('go'),
      includes: jasmine.createSpy('includes').and.callFake((state: string) => state === includedState),
    };
  }

  it('navigates from the load balancer modal through its injected state service', () => {
    const state = stateService('**.loadBalancerDetails');
    const modal = new AzureLoadBalancerModalComponent({
      app: { defaultCredentials: {}, defaultRegions: {}, name: 'fnord' },
      closeModal: jasmine.createSpy('closeModal'),
      dismissModal: jasmine.createSpy('dismissModal'),
      isNew: true,
      stateService: state,
    } as any) as any;
    modal.mounted = true;
    modal.state = {
      loadBalancer: { credentials: 'test-account', name: 'fnord-main', region: 'westus' },
    };

    modal.onApplicationRefresh();

    expect(state.go).toHaveBeenCalledWith('^.loadBalancerDetails', {
      accountId: 'test-account',
      name: 'fnord-main',
      provider: 'azure',
      region: 'westus',
    });
  });

  it('navigates from the clone server group modal through its injected state service', () => {
    const state = stateService('**.clusters.cluster.serverGroup');
    const modal = new AzureCloneServerGroupModalComponent({
      application: { name: 'fnord' },
      command: { viewState: { requiresTemplateSelection: true } },
      dismissModal: jasmine.createSpy('dismissModal'),
      stateService: state,
    } as any) as any;
    modal.state = {
      command: { credentials: 'test-account', region: 'westus' },
      taskMonitor: {
        task: {
          execution: {
            stages: [
              {
                context: { 'deploy.server.groups': { westus: 'fnord-main-v042' } },
                type: 'cloneServerGroup',
              },
            ],
          },
        },
      },
    };

    modal.onApplicationRefresh();

    expect(state.go).toHaveBeenCalledWith('^.^.serverGroup', {
      accountId: 'test-account',
      provider: 'azure',
      region: 'westus',
      serverGroup: 'fnord-main-v042',
    });
  });
});
