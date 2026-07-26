import { TaskExecutor, TaskMonitor } from '@spinnaker/core';

import {
  EditScheduledActionsModal,
  buildScheduledActionsJob,
  isScheduledActionValid,
} from './EditScheduledActionsModal';

describe('EditScheduledActionsModal', () => {
  const serverGroup = { name: 'deck-main-v001', account: 'test', region: 'us-east-1' } as any;

  it('validates recurrence and capacity rows', () => {
    expect(isScheduledActionValid({ recurrence: '', minSize: 1 })).toBe(false);
    expect(isScheduledActionValid({ recurrence: '0 12 * * *' })).toBe(false);
    expect(isScheduledActionValid({ recurrence: '0 12 * * *', minSize: 4, maxSize: 2 })).toBe(false);
    expect(isScheduledActionValid({ recurrence: '0 12 * * *', minSize: 2, desiredCapacity: 1 })).toBe(false);
    expect(isScheduledActionValid({ recurrence: '0 12 * * *', minSize: 1, maxSize: 4, desiredCapacity: 5 })).toBe(
      false,
    );
    expect(isScheduledActionValid({ recurrence: '0 12 * * *', desiredCapacity: 3 })).toBe(true);
  });

  it('rejects non-finite, non-integer, and negative capacities', () => {
    const fields = ['minSize', 'maxSize', 'desiredCapacity'] as const;
    const invalidValues = [NaN, Infinity, -Infinity, 1.5, -1];

    fields.forEach((field) =>
      invalidValues.forEach((value) =>
        expect(isScheduledActionValid({ recurrence: '0 12 * * *', [field]: value })).toBe(false),
      ),
    );
  });

  it('builds a complete scheduled-actions replacement job', () => {
    const actions = [
      {
        recurrence: '0 12 * * *',
        minSize: 1,
        maxSize: 4,
        desiredCapacity: 2,
        scheduledActionName: 'cached-name',
        startTime: '2026-07-12T12:00:00Z',
        endTime: '2026-07-13T12:00:00Z',
        time: '2026-07-12T12:00:00Z',
      },
    ];

    expect(buildScheduledActionsJob(serverGroup, actions)).toEqual({
      type: 'upsertAsgScheduledActions',
      asgs: [{ asgName: 'deck-main-v001', region: 'us-east-1' }],
      scheduledActions: [{ recurrence: '0 12 * * *', minSize: 1, maxSize: 4, desiredCapacity: 2 }],
      credentials: 'test',
    });
  });

  it('projects cached scheduled actions onto editable fields at initialization', () => {
    spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({ result: Promise.resolve() } as any);
    const application = { name: 'deck', serverGroups: { refresh: jasmine.createSpy('refresh') } } as any;
    const modal = new EditScheduledActionsModal({
      application,
      serverGroup: {
        ...serverGroup,
        scheduledActions: [
          {
            recurrence: '0 12 * * *',
            minSize: 1,
            maxSize: 4,
            desiredCapacity: 2,
            scheduledActionName: 'cached-name',
            startTime: '2026-07-12T12:00:00Z',
            endTime: '2026-07-13T12:00:00Z',
            time: '2026-07-12T12:00:00Z',
          },
        ],
      },
      closeModal: jasmine.createSpy('closeModal'),
      dismissModal: jasmine.createSpy('dismissModal'),
    } as any) as any;

    expect(modal.state.scheduledActions).toEqual([
      { recurrence: '0 12 * * *', minSize: 1, maxSize: 4, desiredCapacity: 2 },
    ]);
  });

  it('adds, edits, removes, and submits capacity rows', () => {
    spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({ result: Promise.resolve() } as any);
    const execute = spyOn(TaskExecutor, 'executeTask').and.returnValue(Promise.resolve({} as any));
    const application = { name: 'deck', serverGroups: { refresh: jasmine.createSpy('refresh') } } as any;
    const modal = new EditScheduledActionsModal({
      application,
      serverGroup: { ...serverGroup, scheduledActions: [{ recurrence: 'old', minSize: 1 }] },
      closeModal: jasmine.createSpy('closeModal'),
      dismissModal: jasmine.createSpy('dismissModal'),
    } as any) as any;
    modal.setState = (update: any) => {
      const next = typeof update === 'function' ? update(modal.state) : update;
      modal.state = { ...modal.state, ...next };
    };

    modal.addScheduledAction();
    modal.updateScheduledAction(1, 'recurrence', '0 12 * * *');
    modal.updateScheduledAction(1, 'desiredCapacity', 3);
    modal.removeScheduledAction(0);
    modal.state.taskMonitor = { submit: (method: () => any) => method() };
    modal.submit();

    expect(execute).toHaveBeenCalledWith({
      application,
      description: 'Update Scheduled Actions for deck-main-v001',
      job: [
        jasmine.objectContaining({
          scheduledActions: [{ recurrence: '0 12 * * *', minSize: undefined, maxSize: undefined, desiredCapacity: 3 }],
        }),
      ],
    });
  });
});
