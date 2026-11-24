import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import { ShowPipelineTemplateJsonModal } from './ShowPipelineTemplateJsonModal';
import type { IPipeline, IPipelineTemplateV2 } from '../../../../domain';
import { PipelineTemplateV2Service } from '../../templates/v2/pipelineTemplateV2.service';

describe('<ShowPipelineTemplateJsonModal />', () => {
  const mockPipeline: Partial<IPipeline> = {
    keepWaitingPipelines: false,
    lastModifiedBy: 'anonymous',
    limitConcurrent: true,
    stages: [{ name: 'Find Image from Cluster', refId: '1', requisiteStageRefIds: [], type: 'findImage' }],
  };

  const mockTemplate: IPipelineTemplateV2 = PipelineTemplateV2Service.createPipelineTemplate(
    mockPipeline as IPipeline,
    'example@example.com',
  );

  beforeEach(() => vi.useFakeTimers());

  afterEach(() => {
    cleanup();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('dismisses modal with close button', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const dismissModal = vi.fn();
    render(<ShowPipelineTemplateJsonModal template={mockTemplate} dismissModal={dismissModal} />);
    await user.click(screen.getByRole('button', { name: 'Close' }));
    expect(dismissModal).toHaveBeenCalled();
  });

  it('updates template json with user input', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const { container } = render(<ShowPipelineTemplateJsonModal template={mockTemplate} />);

    const mockTemplateMetadata = {
      description: 'mock-template-description',
      name: 'mock-template-name',
      owner: 'mock-template-owner',
    };

    await user.clear(screen.getByRole('textbox', { name: 'Name' }));
    await user.type(screen.getByRole('textbox', { name: 'Name' }), mockTemplateMetadata.name);
    await user.clear(screen.getByRole('textbox', { name: 'Description' }));
    await user.type(screen.getByRole('textbox', { name: 'Description' }), mockTemplateMetadata.description);
    await user.clear(screen.getByRole('textbox', { name: 'Owner' }));
    await user.type(screen.getByRole('textbox', { name: 'Owner' }), mockTemplateMetadata.owner);

    const copyCommand = (container.querySelector('textarea[tabindex="-1"]') as HTMLTextAreaElement).value;
    const template = JSON.parse(copyCommand.match(/^echo '(.*)' \| spin pipeline-templates save$/s)[1]);
    expect(template.metadata).toEqual(expect.objectContaining(mockTemplateMetadata));
  });
});
