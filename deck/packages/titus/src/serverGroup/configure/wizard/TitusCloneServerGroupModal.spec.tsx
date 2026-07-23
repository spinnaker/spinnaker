import { TaskMonitor } from '@spinnaker/core';

import { TitusCloneServerGroupModalComponent } from './TitusCloneServerGroupModal';

describe('TitusCloneServerGroupModal', () => {
  it('navigates to the created server group through its injected state service', () => {
    spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({
      dismiss: jasmine.createSpy('dismiss'),
      result: Promise.resolve(),
    } as any);
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
});
