import { shallow } from 'enzyme';
import React from 'react';

import { MetadataPageContent } from './MetadataPageContent';
import { Triggers } from './Triggers';
import { ApplicationModelBuilder } from '../../../application';
import type { IPipeline, IPipelineTag } from '../../../domain';

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
      fieldUpdated: jasmine.createSpy('fieldUpdated'),
      updatePipelineConfig: jasmine.createSpy('updatePipelineConfig'),
      revertCount: 0,
    };
  });

  describe('MetadataPageContent pipeline prop routing', () => {
    it('receives the plan pipeline when pipelineConfig is not provided', () => {
      const plan = makePipeline({ name: 'test-pipeline' });

      const wrapper = shallow(<Triggers {...defaultProps} pipeline={plan} />);
      const metadataContent = wrapper.find(MetadataPageContent);

      expect(metadataContent.prop('pipeline')).toBe(plan);
      expect(metadataContent.prop('pipeline').tags).toBeUndefined();
    });

    it('receives the raw config instead of the plan when pipelineConfig is provided', () => {
      const plan = makePipeline({ name: 'test-pipeline' });
      const rawConfig = makePipeline({ name: 'test-pipeline', tags: instanceTags });

      const wrapper = shallow(<Triggers {...defaultProps} pipeline={plan} pipelineConfig={rawConfig} />);
      const metadataContent = wrapper.find(MetadataPageContent);

      expect(metadataContent.prop('pipeline')).toBe(rawConfig);
      expect(metadataContent.prop('pipeline').tags).toEqual(instanceTags);
    });

    it('uses pipeline directly for standard non-templated pipelines without pipelineConfig', () => {
      const standardPipeline = makePipeline({
        tags: [{ name: 'env', value: 'prod' }],
      });

      const wrapper = shallow(<Triggers {...defaultProps} pipeline={standardPipeline} />);
      const metadataContent = wrapper.find(MetadataPageContent);

      expect(metadataContent.prop('pipeline')).toBe(standardPipeline);
      expect(metadataContent.prop('pipeline').tags).toEqual([{ name: 'env', value: 'prod' }]);
    });

    it('passes updatePipelineConfig through to MetadataPageContent', () => {
      const pipeline = makePipeline();
      const wrapper = shallow(<Triggers {...defaultProps} pipeline={pipeline} />);
      const metadataContent = wrapper.find(MetadataPageContent);

      expect(metadataContent.prop('updatePipelineConfig')).toBe(defaultProps.updatePipelineConfig);
    });
  });
});
