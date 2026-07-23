import { TaskExecutor, TaskMonitor } from '@spinnaker/core';

import { ModifyScalingProcessesModal, buildScalingProcessJobs } from './ModifyScalingProcessesModal';

describe('ModifyScalingProcessesModal', () => {
  const serverGroup = { name: 'deck-main-v001', account: 'test', region: 'us-east-1' } as any;

  it('creates separate resume and suspend jobs for changed processes only', () => {
    const initial = [
      { name: 'Launch', enabled: true },
      { name: 'Terminate', enabled: false },
      { name: 'HealthCheck', enabled: true },
    ] as any;
    const edited = [
      { name: 'Launch', enabled: false },
      { name: 'Terminate', enabled: true },
      { name: 'HealthCheck', enabled: true },
    ] as any;

    expect(buildScalingProcessJobs(initial, edited, serverGroup, 'maintenance')).toEqual([
      {
        type: 'modifyScalingProcess',
        action: 'resume',
        processes: ['Terminate'],
        asgName: 'deck-main-v001',
        regions: ['us-east-1'],
        credentials: 'test',
        cloudProvider: 'aws',
        reason: 'maintenance',
      },
      {
        type: 'modifyScalingProcess',
        action: 'suspend',
        processes: ['Launch'],
        asgName: 'deck-main-v001',
        regions: ['us-east-1'],
        credentials: 'test',
        cloudProvider: 'aws',
        reason: 'maintenance',
      },
    ]);
  });

  it('does not create jobs when process state is unchanged', () => {
    const processes = [{ name: 'Launch', enabled: true }] as any;

    expect(buildScalingProcessJobs(processes, [{ ...processes[0] }], serverGroup)).toEqual([]);
  });

  it('submits the changed-state diff through the task monitor', () => {
    spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({ result: Promise.resolve() } as any);
    const execute = spyOn(TaskExecutor, 'executeTask').and.returnValue(Promise.resolve({} as any));
    const application = { name: 'deck', serverGroups: { refresh: jasmine.createSpy('refresh') } } as any;
    const modal = new ModifyScalingProcessesModal({
      application,
      serverGroup: {
        ...serverGroup,
        asg: { suspendedProcesses: [] },
      },
      closeModal: jasmine.createSpy('closeModal'),
      dismissModal: jasmine.createSpy('dismissModal'),
    } as any) as any;
    modal.state.processes[0].enabled = false;
    modal.state.reason = 'maintenance';
    modal.state.taskMonitor = { submit: (method: () => any) => method() };

    modal.submit();

    expect(execute).toHaveBeenCalledWith({
      application,
      description: 'Update Auto Scaling Processes for deck-main-v001',
      job: [jasmine.objectContaining({ action: 'suspend', processes: ['Launch'], reason: 'maintenance' })],
    });
  });
});
