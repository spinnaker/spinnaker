import { fireEvent, render, screen } from '@testing-library/react';
import { setupUser } from '../../../../utils/testUtils/userEvent';
import React from 'react';

import { ApplicationModelBuilder } from '../../../../application';
import { SETTINGS } from '../../../../config/settings';
import type { INotification, IPipeline, IStage } from '../../../../domain';
import type { IStageConfigProps } from '../common';
import { ManualJudgmentStageConfig } from './ManualJudgmentStageConfig';

describe('<ManualJudgmentStageConfig />', () => {
  const createProps = (stageOverrides: Partial<IStage> = {}) => {
    const stage = {
      name: 'Manual Judgment',
      refId: '1',
      requisiteStageRefIds: [],
      type: 'manualJudgment',
      ...stageOverrides,
    } as IStage;

    return {
      application: ApplicationModelBuilder.createApplicationForTests('app'),
      pipeline: { application: 'app', id: 'pipeline-id', name: 'Pipeline', stages: [stage] } as IPipeline,
      stage,
      stageFieldUpdated: vi.fn(),
      updateStage: vi.fn(),
      updateStageField: vi.fn().mockImplementation((changes: Partial<IStage>) => Object.assign(stage, changes)),
    } as IStageConfigProps;
  };

  afterEach(() => SETTINGS.resetToOriginal());

  it('sets legacy defaults without marking the stage dirty', () => {
    const props = createProps();

    render(<ManualJudgmentStageConfig {...props} />);

    expect(props.stage.notifications).toEqual([]);
    expect(props.stage.judgmentInputs).toEqual([]);
    expect(props.stage.failPipeline).toBe(true);
    expect(props.updateStageField).not.toHaveBeenCalled();
    expect(props.stageFieldUpdated).not.toHaveBeenCalled();
  });

  it('preserves an existing false failPipeline default', () => {
    const props = createProps({ failPipeline: false });

    render(<ManualJudgmentStageConfig {...props} />);

    expect(props.stage.failPipeline).toBe(false);
  });

  it('updates instructions through updateStageField', () => {
    const props = createProps();
    const { container } = render(<ManualJudgmentStageConfig {...props} />);

    fireEvent.change(container.querySelector('textarea'), { target: { value: 'Approve this deploy' } });

    expect(props.updateStageField).toHaveBeenCalledWith({ instructions: 'Approve this deploy' });
  });

  it('renders auth controls only when auth is enabled and updates their fields', async () => {
    const user = setupUser();
    SETTINGS.authEnabled = false;
    const first = render(<ManualJudgmentStageConfig {...createProps()} />);
    expect(first.container.querySelectorAll('input[type="checkbox"]')).toHaveLength(1);
    first.unmount();

    SETTINGS.authEnabled = true;
    const props = createProps();
    const { container } = render(<ManualJudgmentStageConfig {...props} />);
    const checkboxes = container.querySelectorAll('input[type="checkbox"]');

    await user.click(checkboxes[0]);
    await user.click(checkboxes[1]);

    expect(props.updateStageField).toHaveBeenCalledWith({ propagateAuthenticationContext: true });
    expect(props.updateStageField).toHaveBeenCalledWith({ preventSelfApproval: true });
  });

  it('adds, edits, and removes judgment inputs', async () => {
    const user = setupUser();
    const props = createProps({ judgmentInputs: [{ value: 'yes' }] });
    const { container, rerender } = render(<ManualJudgmentStageConfig {...props} />);

    await user.click(screen.getByRole('button', { name: /Add judgment input/i }));
    expect(props.updateStageField).toHaveBeenCalledWith({ judgmentInputs: [{ value: 'yes' }, {}] });

    rerender(<ManualJudgmentStageConfig {...props} stage={props.stage} />);
    fireEvent.change(container.querySelectorAll('input[type="text"]')[1], { target: { value: 'later' } });
    expect(props.updateStageField).toHaveBeenCalledWith({ judgmentInputs: [{ value: 'yes' }, { value: 'later' }] });

    rerender(<ManualJudgmentStageConfig {...props} stage={props.stage} />);
    await user.click(screen.getAllByText('Remove')[0]);
    expect(props.updateStageField).toHaveBeenCalledWith({ judgmentInputs: [{ value: 'later' }] });
  });

  it('clears notifications when send notifications is toggled off', async () => {
    const user = setupUser();
    const props = createProps({ sendNotifications: true, notifications: [{ type: 'email' }] });
    render(<ManualJudgmentStageConfig {...props} />);

    const sendNotifications = screen.getByText('Send Notifications').closest('.form-group');
    await user.click(sendNotifications.querySelector('input[type="checkbox"]'));

    expect(props.updateStageField).toHaveBeenCalledWith({ sendNotifications: undefined, notifications: [] });
  });

  it('renders stage Manual Judgment notifications and updates them through updateStageField', async () => {
    const user = setupUser();
    const notifications: INotification[] = [
      {
        type: 'email',
        address: 'team@example.com',
        level: 'stage',
        when: ['manualJudgment'],
      },
    ];
    const props = createProps({ sendNotifications: true, notifications });
    render(<ManualJudgmentStageConfig {...props} />);

    expect(screen.getByText('Email')).toBeVisible();
    expect(screen.getByText('team@example.com')).toBeVisible();
    expect(screen.getByText('This stage is awaiting judgment')).toBeVisible();
    expect(screen.queryByRole('checkbox', { name: 'Send notifications for this stage' })).not.toBeInTheDocument();
    await user.click(screen.getByText('Remove'));

    expect(props.updateStageField).toHaveBeenCalledWith({ notifications: [] });
  });
});
