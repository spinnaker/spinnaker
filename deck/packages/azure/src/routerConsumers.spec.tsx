import { AzureLoadBalancerModalComponent } from './loadBalancer/configure/AzureLoadBalancerModal';
import { AzureCloneServerGroupModalComponent } from './serverGroup/configure/wizard/AzureCloneServerGroupModal';

describe('Azure routed modal consumers', () => {
  function stateService(includedState: string) {
    return {
      go: vi.fn(),
      includes: vi.fn().mockImplementation((state: string) => state === includedState),
    };
  }

  it('navigates from the load balancer modal through its injected state service', () => {
    const state = stateService('**.loadBalancerDetails');
    const modal = new AzureLoadBalancerModalComponent({
      app: { defaultCredentials: {}, defaultRegions: {}, name: 'fnord' },
      closeModal: vi.fn(),
      dismissModal: vi.fn(),
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
      dismissModal: vi.fn(),
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
