import { render, screen } from '@testing-library/react';
import React from 'react';
import type { Mock } from 'vitest';

import { MetadataPage } from './MetadataPageContent';
import type { IPipeline, IPipelineTag } from '../../../domain';
import { setupUser } from '../../../utils/testUtils';

describe('<MetadataPageContent />', () => {
  let updatePipelineConfigSpy: Mock;

  const makePipeline = (overrides: Partial<IPipeline> = {}): IPipeline => ({
    application: 'products',
    id: 'pipeline-1',
    keepWaitingPipelines: false,
    limitConcurrent: true,
    name: 'test-pipeline',
    parameterConfig: [],
    stages: [],
    triggers: [],
    ...overrides,
  });

  beforeEach(() => {
    updatePipelineConfigSpy = vi.fn();
  });

  const tagRows = (container: HTMLElement) => container.querySelectorAll('table.tags tbody tr');
  const tagInputs = (container: HTMLElement) =>
    Array.from(container.querySelectorAll<HTMLInputElement>('table.tags tbody input[type="text"]'));

  describe('Rendering tags', () => {
    it('renders tag rows when pipeline has tags', () => {
      const tags: IPipelineTag[] = [
        { name: 'service', value: 'products' },
        { name: 'type', value: 'scale' },
      ];
      const { container } = render(
        <MetadataPage pipeline={makePipeline({ tags })} updatePipelineConfig={updatePipelineConfigSpy} />,
      );

      expect(tagRows(container)).toHaveLength(2);
      expect(tagInputs(container).map((input) => input.value)).toEqual(['service', 'products', 'type', 'scale']);
    });

    it('renders no tag rows when pipeline.tags is undefined', () => {
      const { container } = render(
        <MetadataPage pipeline={makePipeline()} updatePipelineConfig={updatePipelineConfigSpy} />,
      );

      expect(tagRows(container)).toHaveLength(0);
    });

    it('renders no tag rows when pipeline.tags is an empty array', () => {
      const { container } = render(
        <MetadataPage pipeline={makePipeline({ tags: [] })} updatePipelineConfig={updatePipelineConfigSpy} />,
      );

      expect(tagRows(container)).toHaveLength(0);
    });
  });

  describe('V2 templated pipeline plan vs instance config', () => {
    it('renders no tags when given the Orca plan which does not include instance-level tags', () => {
      const instanceConfig = makePipeline({
        tags: [
          { name: 'service', value: 'products' },
          { name: 'type', value: 'eval' },
        ],
      });
      // Orca plan does NOT include instance-level tags
      const orcaPlan = makePipeline({ name: instanceConfig.name });

      const { container } = render(<MetadataPage pipeline={orcaPlan} updatePipelineConfig={updatePipelineConfigSpy} />);

      expect(tagRows(container)).toHaveLength(0);
    });

    it('renders tags when given the raw instance config directly', () => {
      const instanceConfig = makePipeline({
        tags: [
          { name: 'service', value: 'products' },
          { name: 'type', value: 'eval' },
        ],
      });

      const { container } = render(
        <MetadataPage pipeline={instanceConfig} updatePipelineConfig={updatePipelineConfigSpy} />,
      );

      expect(tagRows(container)).toHaveLength(2);
    });
  });

  describe('Adding a tag', () => {
    it('calls updatePipelineConfig with new empty tag appended', async () => {
      const user = setupUser();
      const pipeline = makePipeline({ tags: [{ name: 'service', value: 'products' }] });
      render(<MetadataPage pipeline={pipeline} updatePipelineConfig={updatePipelineConfigSpy} />);

      await user.click(screen.getByRole('button', { name: 'Add tag' }));

      expect(updatePipelineConfigSpy).toHaveBeenCalledTimes(1);
      expect(updatePipelineConfigSpy).toHaveBeenCalledWith({
        tags: [
          { name: 'service', value: 'products' },
          { name: '', value: '' },
        ],
      });
    });

    it('creates the first tag when pipeline has no tags', async () => {
      const user = setupUser();
      render(<MetadataPage pipeline={makePipeline()} updatePipelineConfig={updatePipelineConfigSpy} />);

      await user.click(screen.getByRole('button', { name: 'Add tag' }));

      expect(updatePipelineConfigSpy).toHaveBeenCalledTimes(1);
      expect(updatePipelineConfigSpy).toHaveBeenCalledWith({ tags: [{ name: '', value: '' }] });
    });
  });

  describe('Deleting a tag', () => {
    it('removes the tag at the clicked index', async () => {
      const user = setupUser();
      const tags: IPipelineTag[] = [
        { name: 'service', value: 'products' },
        { name: 'type', value: 'scale' },
      ];
      render(<MetadataPage pipeline={makePipeline({ tags })} updatePipelineConfig={updatePipelineConfigSpy} />);

      await user.click(screen.getAllByText('Remove field')[0]);

      expect(updatePipelineConfigSpy).toHaveBeenCalledTimes(1);
      expect(updatePipelineConfigSpy).toHaveBeenCalledWith({ tags: [{ name: 'type', value: 'scale' }] });
    });
  });
});
