import { TaskExecutor } from '@spinnaker/core';

import { ModifyWarmPoolModal, buildWarmPoolJob } from './ModifyWarmPoolModal';

describe('ModifyWarmPoolModal', () => {
  const serverGroup = { name: 'deck-main-v001', account: 'test', region: 'us-east-1' } as any;

  it('builds a delete job when disabled', () => {
    const state = { enabled: false, minSize: 0, maxGroupPreparedCapacity: -1, poolState: 'Stopped' } as any;

    expect(buildWarmPoolJob(state, serverGroup)).toEqual({
      type: 'modifyWarmPool',
      action: 'delete',
      asgName: 'deck-main-v001',
      regions: ['us-east-1'],
      credentials: 'test',
      cloudProvider: 'aws',
      reason: undefined,
    });
  });

  it('builds an upsert job with the configured fields when enabled', () => {
    const state = {
      enabled: true,
      minSize: 2,
      maxGroupPreparedCapacity: 10,
      poolState: 'Running',
      reuseOnScaleIn: true,
      reason: 'maintenance',
    } as any;

    expect(buildWarmPoolJob(state, serverGroup)).toEqual({
      type: 'modifyWarmPool',
      action: 'upsert',
      minSize: 2,
      maxGroupPreparedCapacity: 10,
      poolState: 'Running',
      reuseOnScaleIn: true,
      asgName: 'deck-main-v001',
      regions: ['us-east-1'],
      credentials: 'test',
      cloudProvider: 'aws',
      reason: 'maintenance',
    });
  });

  it('submits the built job through the task monitor', () => {
    const execute = spyOn(TaskExecutor, 'executeTask').and.returnValue(Promise.resolve({} as any));
    const application = { name: 'deck', serverGroups: { refresh: jasmine.createSpy('refresh') } } as any;
    const modal = new ModifyWarmPoolModal({
      application,
      serverGroup: { ...serverGroup, asg: {} },
      closeModal: jasmine.createSpy('closeModal'),
      dismissModal: jasmine.createSpy('dismissModal'),
    } as any) as any;
    modal.state.enabled = true;
    modal.state.minSize = 3;
    modal.state.poolState = 'Stopped';
    modal.state.taskMonitor = { submit: (method: () => any) => method() };

    modal.submit();

    expect(execute).toHaveBeenCalledWith({
      application,
      description: 'Update Warm Pool for deck-main-v001',
      job: [jasmine.objectContaining({ action: 'upsert', minSize: 3, poolState: 'Stopped' })],
    });
  });
});
