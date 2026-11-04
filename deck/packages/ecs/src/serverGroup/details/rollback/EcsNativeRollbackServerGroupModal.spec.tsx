import { render, screen, waitFor } from '@testing-library/react';
import React from 'react';

import { AccountService, DeckRuntimeContext, TaskExecutor } from '@spinnaker/core';

import type { IEcsTaskDefinitionRevision } from './EcsNativeRollbackServerGroupModal';
import {
  EcsNativeRollbackServerGroupModal,
  validateEcsNativeRollbackValues,
} from './EcsNativeRollbackServerGroupModal';

const current: IEcsTaskDefinitionRevision = {
  taskDefinitionArn: 'arn:aws:ecs:us-west-2:1:task-definition/myapp:7',
  family: 'myapp',
  revision: 7,
  containerImages: ['registry/myapp:7'],
  current: true,
};
const previous: IEcsTaskDefinitionRevision = {
  ...current,
  taskDefinitionArn: current.taskDefinitionArn.replace(':7', ':6'),
  revision: 6,
  containerImages: ['registry/myapp:6'],
  current: false,
};
const older: IEcsTaskDefinitionRevision = {
  ...current,
  taskDefinitionArn: current.taskDefinitionArn.replace(':7', ':5'),
  revision: 5,
  containerImages: [],
  current: false,
};

describe('validateEcsNativeRollbackValues', () => {
  const eligible = [previous.taskDefinitionArn, older.taskDefinitionArn];

  it('requires a task definition', () => {
    expect(validateEcsNativeRollbackValues({}, eligible).taskDefinition).toContain('Select a task definition');
  });

  it('rejects a task definition that is not an eligible rollback target, such as the running revision', () => {
    expect(
      validateEcsNativeRollbackValues({ taskDefinition: current.taskDefinitionArn }, eligible).taskDefinition,
    ).toContain('eligible');
  });

  it('accepts an eligible earlier revision', () => {
    expect(validateEcsNativeRollbackValues({ taskDefinition: previous.taskDefinitionArn }, eligible)).toEqual({});
  });
});

describe('<EcsNativeRollbackServerGroupModal />', () => {
  const application = { name: 'app', serverGroups: { refresh: vi.fn() } } as any;
  const buildServerGroup = (taskDefinitionRevisions: IEcsTaskDefinitionRevision[] = [current, previous, older]) =>
    ({ account: 'test-account', region: 'us-west-2', name: 'myapp', taskDefinitionRevisions } as any);

  const renderModal = (serverGroup = buildServerGroup()) => {
    const ref = React.createRef<EcsNativeRollbackServerGroupModal>();
    render(
      <DeckRuntimeContext.Provider value={{ services: {} } as any}>
        <EcsNativeRollbackServerGroupModal
          application={application}
          closeModal={vi.fn()}
          dismissModal={vi.fn()}
          ref={ref}
          serverGroup={serverGroup}
        />
      </DeckRuntimeContext.Provider>,
    );
    return ref;
  };

  const expectedJob = (taskDefinition: string, reason?: string) => ({
    type: 'ecsNativeRollbackServerGroup',
    cloudProvider: 'ecs-native',
    credentials: 'test-account',
    region: 'us-west-2',
    serverGroupName: 'myapp',
    taskDefinition,
    forceNewDeployment: true,
    reason,
  });

  beforeEach(() => {
    // An account that does not challenge destructive actions hides the verification input, which
    // counts as verified; the modal then only depends on the chosen revision.
    vi.spyOn(AccountService, 'challengeDestructiveActions').mockResolvedValue(false);
    vi.spyOn(TaskExecutor, 'executeTask').mockReturnValue(new Promise(() => {}) as any);
  });

  afterEach(() => vi.restoreAllMocks());

  it('renders the rollback title and keeps Submit disabled until a target revision is chosen', () => {
    renderModal();
    expect(screen.getByText('Rollback myapp')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Submit' })).toBeDisabled();
  });

  it('warns and disables Submit when the running revision is the only one', () => {
    renderModal(buildServerGroup([current]));
    expect(screen.getByText('No earlier task-definition revisions are available to roll back to.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Submit' })).toBeDisabled();
  });

  it('submits an ecs-native rollback job pinned to the chosen revision with a forced new deployment', async () => {
    const ref = renderModal();
    await waitFor(() => expect(ref.current.state.verified).toBe(true));
    const submit = vi.spyOn(ref.current.state.taskMonitor, 'submit').mockImplementation((run) => run() as any);

    (ref.current as any).submit({ taskDefinition: previous.taskDefinitionArn, reason: 'image 7 regressed' });

    expect(submit).toHaveBeenCalledTimes(1);
    expect(TaskExecutor.executeTask).toHaveBeenCalledExactlyOnceWith({
      job: [expectedJob(previous.taskDefinitionArn, 'image 7 regressed')],
      application,
      description: `Rollback myapp to ${previous.taskDefinitionArn}`,
    });
  });

  it('never submits the running revision or a revision the service does not have', async () => {
    const ref = renderModal();
    await waitFor(() => expect(ref.current.state.verified).toBe(true));
    const submit = vi.spyOn(ref.current.state.taskMonitor, 'submit');

    (ref.current as any).submit({ taskDefinition: current.taskDefinitionArn });
    (ref.current as any).submit({ taskDefinition: 'arn:aws:ecs:us-west-2:1:task-definition/other:1' });
    (ref.current as any).submit({});

    expect(submit).not.toHaveBeenCalled();
    expect(TaskExecutor.executeTask).not.toHaveBeenCalled();
  });

  it('does not submit before the user verification has passed', () => {
    const ref = renderModal();
    const submit = vi.spyOn(ref.current.state.taskMonitor, 'submit');

    (ref.current as any).submit({ taskDefinition: previous.taskDefinitionArn });

    expect(ref.current.state.verified).toBe(false);
    expect(submit).not.toHaveBeenCalled();
  });
});
