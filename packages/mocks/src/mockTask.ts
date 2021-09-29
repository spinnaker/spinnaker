import type { IOrchestratedItem, ITask, ITaskStep, ITaskVariable } from '@spinnaker/core';

export const mockOrchestratedItem: IOrchestratedItem = {
  getValueFor: () => {},
  originalStatus: 'SUCCEEDED',
  status: 'SUCCEEDED',
  failureMessage: '',
  isBuffered: true,
  isCompleted: true,
  isRunning: false,
  isFailed: false,
  isStopped: false,
  isActive: false,
  hasNotStarted: false,
  isCanceled: false,
  isSuspended: false,
  isPaused: false,
  runningTime: '',
  startTime: 1580755347844,
  endTime: 1580755347846,
  runningTimeInMs: 40000,
};

type ITaskStatus = 'TERMINAL' | 'NOT_STARTED' | 'CANCELED' | 'RUNNING' | 'SUCCEEDED';
export const createMockOrchestratedItem = (status: ITaskStatus): IOrchestratedItem => {
  if (status === 'TERMINAL') {
    return {
      ...mockOrchestratedItem,
      status,
      originalStatus: status,
      isFailed: true,
      isCompleted: false,
      failureMessage: 'Failed to complete task',
    };
  }

  if (status === 'NOT_STARTED') {
    return {
      ...mockOrchestratedItem,
      status,
      isCompleted: false,
      isBuffered: false,
      hasNotStarted: true,
      startTime: null,
      endTime: null,
    };
  }

  if (status === 'CANCELED') {
    return {
      ...mockOrchestratedItem,
      status,
      originalStatus: status,
      isCompleted: false,
      isCanceled: true,
    };
  }

  if (status === 'RUNNING') {
    return {
      ...mockOrchestratedItem,
      status,
      originalStatus: status,
      isCompleted: false,
      isRunning: true,
      endTime: null,
    };
  }
};

export const createMockTaskStep = (status: ITaskStatus): ITaskStep => ({
  name: 'test',
  ...createMockOrchestratedItem(status),
});

export const createMockTask = (status: ITaskStatus, steps?: ITaskStep[], variables?: ITaskVariable[]): ITask => ({
  application: 'testapp',
  id: '123-456',
  name: 'running',
  steps: steps || [createMockTaskStep(status)],
  variables: variables || ([] as ITaskVariable[]),
  startTime: 1580755347844,
  endTime: 1580755347846,
  execution: {
    application: 'testapp',
    status: status || 'SUCCEEDED',
  },
  history: {},
  ...createMockOrchestratedItem(status),
});
