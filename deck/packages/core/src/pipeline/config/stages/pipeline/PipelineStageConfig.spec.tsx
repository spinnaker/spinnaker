import type { Mock } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { setupUser } from '../../../../utils/testUtils/userEvent';
import React from 'react';

import { ApplicationReader } from '../../../../application/service/ApplicationReader';
import { PipelineConfigService } from '../../services/PipelineConfigService';
import { PipelineStageConfig } from './PipelineStageConfig';
import type { IPipeline, IStage } from '../../../../domain';

describe('PipelineStageConfig', () => {
  const flush = async () => {
    await Promise.resolve();
    await new Promise((resolve) => setTimeout(resolve, 0));
    await Promise.resolve();
  };

  beforeEach(() => {
    vi.spyOn(ApplicationReader, 'listApplications').mockReturnValue(Promise.resolve([{ name: 'app' }]) as any);
  });

  it('uses a searchable virtualized application selector for static application values', async () => {
    const user = setupUser();
    vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockReturnValue(320);
    (ApplicationReader.listApplications as Mock).mockReturnValue(
      Promise.resolve([{ name: 'app' }, { name: 'zzz-app' }]) as any,
    );
    const parentPipeline = { id: 'parent-pipeline', parameterConfig: [], stages: [] } as IPipeline;
    const stage = { application: 'app' } as IStage;
    const updateStageField = vi.fn();
    vi.spyOn(PipelineConfigService, 'getPipelinesForApplication').mockReturnValue(Promise.resolve([]) as any);

    render(
      <PipelineStageConfig
        application={{ name: 'app' } as any}
        pipeline={parentPipeline}
        stage={stage}
        updateStageField={updateStageField}
      />,
    );

    await waitFor(() => expect(ApplicationReader.listApplications).toHaveBeenCalled());
    const applicationSelect = screen.getAllByRole('combobox')[0];
    await user.click(applicationSelect);
    await user.click(await screen.findByText('zzz-app'));

    expect(updateStageField).toHaveBeenCalledWith({ application: 'zzz-app' });
  });

  it('keeps option parameter SpeL values editable', async () => {
    const parentPipeline = { id: 'parent-pipeline', parameterConfig: [], stages: [] } as IPipeline;
    const childPipeline = {
      id: 'child-pipeline',
      name: 'Child Pipeline',
      parameterConfig: [
        {
          default: null,
          hasOptions: true,
          name: 'choice',
          options: [{ value: 'one' }, { value: 'two' }],
        },
      ],
    } as IPipeline;
    const stage = {
      application: 'app',
      pipeline: 'child-pipeline',
      pipelineParameters: { choice: '${ trigger.properties.choice }' },
    } as IStage;
    const updateStageField = vi.fn();
    vi.spyOn(PipelineConfigService, 'getPipelinesForApplication').mockReturnValue(
      Promise.resolve([childPipeline]) as any,
    );

    render(
      <PipelineStageConfig
        application={{ name: 'app' } as any}
        pipeline={parentPipeline}
        stage={stage}
        updateStageField={updateStageField}
      />,
    );

    const parameterInput = await screen.findByDisplayValue('${ trigger.properties.choice }');
    fireEvent.change(parameterInput, { target: { value: '${ parameters.choice }' } });
    await flush();

    expect(updateStageField).toHaveBeenCalledWith({ pipelineParameters: { choice: '${ parameters.choice }' } });
  });
});
