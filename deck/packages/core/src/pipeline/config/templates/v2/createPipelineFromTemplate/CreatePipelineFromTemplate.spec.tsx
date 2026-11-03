import { UIRouterReact } from '@uirouter/react';
import { render, screen, waitFor } from '@testing-library/react';
import { setupUser } from '../../../../../utils/testUtils/userEvent';
import React from 'react';

import { ApplicationModelBuilder } from '../../../../../application/applicationModel.builder';
import type { IApplicationSummary } from '../../../../../application';
import { ApplicationReader } from '../../../../../application';
import { SETTINGS } from '../../../../../config';
import type { IPipeline, IPipelineTemplateV2 } from '../../../../../domain';
import { PipelineConfigService } from '../../../../config/services/PipelineConfigService';
import { CreatePipelineFromTemplateComponent } from './CreatePipelineFromTemplate';

describe('CreatePipelineFromTemplate', () => {
  const pipelineTemplatesEnabled = SETTINGS.feature.pipelineTemplates;

  afterEach(() => {
    SETTINGS.feature.pipelineTemplates = pipelineTemplatesEnabled;
  });

  it('opens a pipeline saved through the rendered create modal using the injected router', async () => {
    SETTINGS.feature.pipelineTemplates = true;
    const user = setupUser();
    const router = new UIRouterReact();
    const go = vi.spyOn(router.stateService, 'go').mockResolvedValue(undefined);
    const applicationSummary = { name: 'test-app' } as IApplicationSummary;
    const application = ApplicationModelBuilder.createApplicationForTests('test-app', {
      key: 'pipelineConfigs',
      lazy: true,
      defaultData: [] as IPipeline[],
    });
    vi.spyOn(ApplicationReader, 'listApplications').mockResolvedValue([applicationSummary]);
    vi.spyOn(ApplicationReader, 'getApplication').mockResolvedValue(application);
    vi.spyOn(PipelineConfigService, 'savePipeline').mockResolvedValue();
    vi.spyOn(application.pipelineConfigs, 'activate');
    vi.spyOn(application.pipelineConfigs, 'refresh')
      .mockResolvedValueOnce([])
      .mockImplementation(() => {
        application.pipelineConfigs.data = [{ id: 'pipeline-id', name: 'Created Pipeline' } as IPipeline];
        return Promise.resolve(application.pipelineConfigs.data);
      });
    const template = {
      id: 'template-id',
      metadata: { name: 'Template', owner: 'owner@example.com' },
      pipeline: {},
      schema: 'v2',
      updateTs: '0',
      variables: [],
    } as IPipelineTemplateV2;

    const view = render(
      <CreatePipelineFromTemplateComponent
        closeModalCallback={vi.fn()}
        template={template}
        router={router}
        stateParams={{}}
        stateService={router.stateService}
      />,
    );

    await user.click(await screen.findByRole('combobox'));
    await user.click(screen.getByText('test-app'));
    await user.click(screen.getByRole('button', { name: 'Continue' }));
    expect(await screen.findByRole('heading', { name: 'Create New Pipeline' })).toBeVisible();
    await user.type(screen.getByRole('textbox'), 'Created Pipeline');
    await user.click(screen.getByRole('button', { name: 'Continue' }));

    await waitFor(() =>
      expect(go).toHaveBeenCalledWith('home.applications.application.pipelines.pipelineConfig', {
        application: 'test-app',
        pipelineId: 'pipeline-id',
        new: 1,
      }),
    );
    expect(application.pipelineConfigs.activate).toHaveBeenCalledTimes(1);
    view.unmount();
    router.dispose();
  });
});
