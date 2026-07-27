import { TitusCloneServerGroupModalComponent } from './TitusCloneServerGroupModal';

describe('TitusCloneServerGroupModal', () => {
  it('navigates to the created server group through its injected state service', () => {
    const stateService = {
      go: jasmine.createSpy('go'),
      includes: jasmine.createSpy('includes').and.callFake((state: string) => state === '**.clusters.serverGroup'),
    };
    const modal = new TitusCloneServerGroupModalComponent({
      application: { name: 'fnord' },
      command: {
        credentials: 'test-account',
        region: 'us-east-1',
        viewState: { requiresTemplateSelection: true },
      },
      dismissModal: jasmine.createSpy('dismissModal'),
      stateService,
    } as any) as any;
    modal.state = {
      taskMonitor: {
        task: {
          execution: {
            stages: [
              {
                context: { 'deploy.server.groups': { 'us-east-1': 'fnord-main-v042' } },
                type: 'cloneServerGroup',
              },
            ],
          },
        },
      },
    };

    modal.onApplicationRefresh();

    expect(stateService.go).toHaveBeenCalledWith('^.serverGroup', {
      accountId: 'test-account',
      provider: 'titus',
      region: 'us-east-1',
      serverGroup: 'fnord-main-v042',
    });
  });

  it('owns its refresh subscription across replacement and unmount', () => {
    const firstUnsubscribe = jasmine.createSpy('firstUnsubscribe');
    const secondUnsubscribe = jasmine.createSpy('secondUnsubscribe');
    const callbacks: Array<() => void> = [];
    const onNextRefresh = jasmine.createSpy('onNextRefresh').and.callFake((callback: () => void) => {
      callbacks.push(callback);
      return callbacks.length === 1 ? firstUnsubscribe : secondUnsubscribe;
    });
    const refresh = jasmine.createSpy('refresh');
    const stateService = { go: jasmine.createSpy('go'), includes: jasmine.createSpy('includes') };
    const modal = new TitusCloneServerGroupModalComponent({
      application: { name: 'fnord', serverGroups: { onNextRefresh, refresh } },
      command: { credentials: 'test-account', region: 'us-east-1', viewState: { requiresTemplateSelection: true } },
      dismissModal: jasmine.createSpy('dismissModal'),
      stateService,
    } as any) as any;

    modal.onTaskComplete();

    expect(onNextRefresh.calls.first().invocationOrder).toBeLessThan(refresh.calls.first().invocationOrder);

    modal.onTaskComplete();

    expect(firstUnsubscribe).toHaveBeenCalledTimes(1);

    modal.componentWillUnmount();
    callbacks[1]();

    expect(secondUnsubscribe).toHaveBeenCalledTimes(1);
    expect(modal.refreshUnsubscribe).toBeUndefined();
    expect(stateService.go).not.toHaveBeenCalled();
  });
});
