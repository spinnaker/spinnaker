import { render, screen } from '@testing-library/react';
import { setupUser } from '../../../../utils/testUtils/userEvent';
import React from 'react';

import { ApplicationModelBuilder } from '../../../../application';
import type { IPipeline, IStage } from '../../../../domain';
import { EditPreconditionModal } from '../../preconditions/EditPreconditionModal';
import type { IPrecondition } from '../../preconditions/preconditionTypes';
import type { IStageConfigProps } from '../common';
import { CheckPreconditionsStageConfig } from './CheckPreconditionsStageConfig';

describe('<CheckPreconditionsStageConfig />', () => {
  const createProps = (stageOverrides: Partial<IStage> = {}, pipelineOverrides: Partial<IPipeline> = {}) => {
    const parentStage = {
      name: 'Bake',
      refId: '1',
      requisiteStageRefIds: [],
      type: 'bake',
    } as IStage;
    const stage = {
      name: 'Check Preconditions',
      refId: '2',
      requisiteStageRefIds: ['1'],
      type: 'checkPreconditions',
      ...stageOverrides,
    } as IStage;
    const pipeline = {
      application: 'app',
      id: 'pipeline-id',
      name: 'Pipeline',
      stages: [parentStage, stage],
      strategy: false,
      ...pipelineOverrides,
    } as IPipeline;
    const application = ApplicationModelBuilder.createApplicationForTests('app');

    return {
      application,
      pipeline,
      stage,
      stageFieldUpdated: vi.fn(),
      updateStage: vi.fn(),
      updateStageField: vi.fn().mockImplementation((changes: Partial<IStage>) => Object.assign(stage, changes)),
    } as IStageConfigProps;
  };

  it('passes an empty precondition list without mutating the stage during render', () => {
    const props = createProps();

    render(<CheckPreconditionsStageConfig {...props} />);

    expect(props.stage.preconditions).toBeUndefined();
    expect(screen.getByRole('button', { name: /Add Precondition/ })).toBeVisible();
    expect(props.updateStageField).not.toHaveBeenCalled();
    expect(props.stageFieldUpdated).not.toHaveBeenCalled();
  });

  it('renders the precondition list with exact application, strategy, preconditions, and upstream stages', async () => {
    const user = setupUser();
    const preconditions: IPrecondition[] = [
      { context: { expression: '${true}' }, failPipeline: true, type: 'expression' },
    ];
    const props = createProps({ preconditions }, { strategy: true });
    const show = vi.spyOn(EditPreconditionModal, 'show').mockRejectedValue(new Error('dismissed'));
    render(<CheckPreconditionsStageConfig {...props} />);

    expect(screen.getByText('${true}').closest('.precondition-details')).toHaveTextContent('Expression: ${true}');
    await user.click(screen.getByRole('button', { name: /Add Precondition/ }));

    expect(show).toHaveBeenCalledExactlyOnceWith({
      application: props.application,
      precondition: undefined,
      strategy: true,
      upstreamStages: [props.pipeline.stages[0]],
    });
  });

  it('updates stage preconditions through updateStageField', async () => {
    const user = setupUser();
    const props = createProps({ preconditions: [] });
    const updatedPreconditions: IPrecondition[] = [
      { context: { expression: '${true}' }, failPipeline: true, type: 'expression' },
    ];
    vi.spyOn(EditPreconditionModal, 'show').mockResolvedValue(updatedPreconditions[0]);
    render(<CheckPreconditionsStageConfig {...props} />);

    await user.click(screen.getByRole('button', { name: /Add Precondition/ }));

    expect(props.updateStageField).toHaveBeenCalledWith({ preconditions: updatedPreconditions });
  });
});
