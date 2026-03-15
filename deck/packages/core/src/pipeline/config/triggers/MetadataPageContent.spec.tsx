import { mount } from 'enzyme';
import React from 'react';

import { MetadataPage } from './MetadataPageContent';
import type { IPipeline, IPipelineTag } from '../../../domain';

describe('<MetadataPageContent />', () => {
  let updatePipelineConfigSpy: jasmine.Spy;

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
    updatePipelineConfigSpy = jasmine.createSpy('updatePipelineConfig');
  });

  describe('Rendering tags', () => {
    it('renders tag rows when pipeline has tags', () => {
      const tags: IPipelineTag[] = [
        { name: 'service', value: 'products' },
        { name: 'type', value: 'scale' },
      ];
      const pipeline = makePipeline({ tags });
      const wrapper = mount(<MetadataPage pipeline={pipeline} updatePipelineConfig={updatePipelineConfigSpy} />);

      const tagInputs = wrapper.find('table.tags tbody tr');
      expect(tagInputs.length).toBe(2);

      const nameInputs = wrapper.find('table.tags tbody input[type="text"]');
      expect(nameInputs.at(0).prop('value')).toBe('service');
      expect(nameInputs.at(1).prop('value')).toBe('products');
      expect(nameInputs.at(2).prop('value')).toBe('type');
      expect(nameInputs.at(3).prop('value')).toBe('scale');
    });

    it('renders no tag rows when pipeline.tags is undefined', () => {
      const pipeline = makePipeline();
      const wrapper = mount(<MetadataPage pipeline={pipeline} updatePipelineConfig={updatePipelineConfigSpy} />);

      const tagRows = wrapper.find('table.tags tbody tr');
      expect(tagRows.length).toBe(0);
    });

    it('renders no tag rows when pipeline.tags is an empty array', () => {
      const pipeline = makePipeline({ tags: [] });
      const wrapper = mount(<MetadataPage pipeline={pipeline} updatePipelineConfig={updatePipelineConfigSpy} />);

      const tagRows = wrapper.find('table.tags tbody tr');
      expect(tagRows.length).toBe(0);
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

      const orcaPlan = makePipeline({
        name: instanceConfig.name,
        // Orca plan does NOT include instance-level tags
      });

      const wrapper = mount(<MetadataPage pipeline={orcaPlan} updatePipelineConfig={updatePipelineConfigSpy} />);
      const tagRows = wrapper.find('table.tags tbody tr');
      expect(tagRows.length).toBe(0);
    });

    it('renders tags when given the raw instance config directly', () => {
      const instanceConfig = makePipeline({
        tags: [
          { name: 'service', value: 'products' },
          { name: 'type', value: 'eval' },
        ],
      });

      const wrapper = mount(
        <MetadataPage pipeline={instanceConfig} updatePipelineConfig={updatePipelineConfigSpy} />,
      );
      const tagRows = wrapper.find('table.tags tbody tr');
      expect(tagRows.length).toBe(2);
    });
  });

  describe('Adding a tag', () => {
    it('calls updatePipelineConfig with new empty tag appended', () => {
      const pipeline = makePipeline({
        tags: [{ name: 'service', value: 'products' }],
      });
      const wrapper = mount(<MetadataPage pipeline={pipeline} updatePipelineConfig={updatePipelineConfigSpy} />);

      wrapper.find('button.add-new').simulate('click');
      expect(updatePipelineConfigSpy).toHaveBeenCalledTimes(1);
      expect(updatePipelineConfigSpy).toHaveBeenCalledWith({
        tags: [
          { name: 'service', value: 'products' },
          { name: '', value: '' },
        ],
      });
    });

    it('creates the first tag when pipeline has no tags', () => {
      const pipeline = makePipeline();
      const wrapper = mount(<MetadataPage pipeline={pipeline} updatePipelineConfig={updatePipelineConfigSpy} />);

      wrapper.find('button.add-new').simulate('click');
      expect(updatePipelineConfigSpy).toHaveBeenCalledTimes(1);
      expect(updatePipelineConfigSpy).toHaveBeenCalledWith({
        tags: [{ name: '', value: '' }],
      });
    });
  });

  describe('Deleting a tag', () => {
    it('removes the tag at the clicked index', () => {
      const tags: IPipelineTag[] = [
        { name: 'service', value: 'products' },
        { name: 'type', value: 'scale' },
      ];
      const pipeline = makePipeline({ tags });
      const wrapper = mount(<MetadataPage pipeline={pipeline} updatePipelineConfig={updatePipelineConfigSpy} />);

      wrapper.find('.glyphicon-trash').at(0).simulate('click');
      expect(updatePipelineConfigSpy).toHaveBeenCalledTimes(1);
      expect(updatePipelineConfigSpy).toHaveBeenCalledWith({
        tags: [{ name: 'type', value: 'scale' }],
      });
    });
  });
});
