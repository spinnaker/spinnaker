import { fireEvent } from '@testing-library/react';
import React from 'react';

import { Triggers } from './Triggers';
import { ApplicationModelBuilder } from '../../../application';
import type { IPipeline, IPipelineTag } from '../../../domain';
import { renderWithRouter } from '../../../utils/testUtils/rtl';

// Triggers routes `pipelineConfig || pipeline` into the "Metadata" page. Rather than inspecting
// child props (Enzyme shallow), we render the real Metadata section and assert on the tags/description
// it renders for the routed pipeline, plus that edits flow back through updatePipelineConfig.
describe('<Triggers />', () => {
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

  const instanceTags: IPipelineTag[] = [
    { name: 'service', value: 'products' },
    { name: 'type', value: 'eval' },
  ];

  let defaultProps: any;

  beforeEach(() => {
    defaultProps = {
      application: ApplicationModelBuilder.createApplicationForTests('products'),
      fieldUpdated: vi.fn(),
      updatePipelineConfig: vi.fn(),
      revertCount: 0,
    };
  });

  const metadataSection = (container: HTMLElement) =>
    container.querySelector('[data-page-content="description"]') as HTMLElement;

  const tagValues = (container: HTMLElement): string[] =>
    Array.from(metadataSection(container).querySelectorAll('table.tags tbody input')).map(
      (input) => (input as HTMLInputElement).value,
    );

  describe('MetadataPageContent pipeline routing', () => {
    it('renders the plan pipeline (no tags) when pipelineConfig is not provided', () => {
      const plan = makePipeline({ name: 'test-pipeline' });

      const { container } = renderWithRouter(<Triggers {...defaultProps} pipeline={plan} />);

      expect(tagValues(container)).toEqual([]);
    });

    it('renders the raw config tags instead of the plan when pipelineConfig is provided', () => {
      const plan = makePipeline({ name: 'test-pipeline' });
      const rawConfig = makePipeline({ name: 'test-pipeline', tags: instanceTags });

      const { container } = renderWithRouter(<Triggers {...defaultProps} pipeline={plan} pipelineConfig={rawConfig} />);

      expect(tagValues(container)).toEqual(['service', 'products', 'type', 'eval']);
    });

    it('renders the pipeline directly for standard pipelines without pipelineConfig', () => {
      const standardPipeline = makePipeline({ tags: [{ name: 'env', value: 'prod' }] });

      const { container } = renderWithRouter(<Triggers {...defaultProps} pipeline={standardPipeline} />);

      expect(tagValues(container)).toEqual(['env', 'prod']);
    });

    it('routes Metadata edits back through updatePipelineConfig', () => {
      const pipeline = makePipeline();

      const { container } = renderWithRouter(<Triggers {...defaultProps} pipeline={pipeline} />);
      const description = metadataSection(container).querySelector('textarea') as HTMLTextAreaElement;
      fireEvent.change(description, { target: { value: 'updated description' } });

      expect(defaultProps.updatePipelineConfig).toHaveBeenCalledWith(
        expect.objectContaining({ description: 'updated description' }),
      );
    });
  });
});
