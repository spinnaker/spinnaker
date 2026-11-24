import { act, cleanup, render, screen } from '@testing-library/react';
import ace from 'ace-builds';
import React from 'react';

import { EditPipelineJsonModal } from './EditPipelineJsonModal';
import type { IPipeline } from '../../../../domain';
import { setupUser } from '../../../../utils/testUtils/userEvent';

describe('EditPipelineJsonModal', () => {
  const pipeline = (): IPipeline => ({
    application: 'app',
    id: 'pipeline-id',
    name: 'Pipeline',
    stages: [],
    triggers: [],
    parameterConfig: [],
    notifications: [],
    limitConcurrent: true,
    keepWaitingPipelines: false,
  });

  const updatePipeline = async (edited: any) => {
    const user = setupUser();
    const original = pipeline();
    const closeModal = vi.fn();
    const { container } = render(
      <EditPipelineJsonModal pipeline={original} plan={null} closeModal={closeModal} dismissModal={vi.fn()} />,
    );
    const editor = ace.edit(container.querySelector('.ace-editor') as HTMLElement);
    act(() => {
      editor.setValue(JSON.stringify(edited));
    });
    await user.click(screen.getByRole('button', { name: /Update Pipeline/ }));
    return { closeModal, original };
  };

  afterEach(cleanup);

  it('rejects triggers that are not objects', async () => {
    const { closeModal, original } = await updatePipeline({ ...pipeline(), triggers: ['x'] });

    expect(screen.getByText('Error: The triggers property must be a list of objects.')).toBeVisible();
    expect(closeModal).not.toHaveBeenCalled();
    expect(original.triggers).toEqual([]);
  });

  it('rejects stages that are not a list', async () => {
    const { closeModal, original } = await updatePipeline({ ...pipeline(), stages: 'x' });

    expect(screen.getByText('Error: The stages property must be a list of objects.')).toBeVisible();
    expect(closeModal).not.toHaveBeenCalled();
    expect(original.stages).toEqual([]);
  });
});
