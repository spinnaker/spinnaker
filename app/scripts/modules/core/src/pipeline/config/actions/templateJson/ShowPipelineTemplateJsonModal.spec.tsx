import * as React from 'react';
import { mount } from 'enzyme';

import { IPipeline } from 'core/domain';
import { ShowPipelineTemplateJsonModal } from './ShowPipelineTemplateJsonModal';

describe('<ShowPipelineTemplateJsonModal />', () => {
  const mockPipeline: Partial<IPipeline> = {
    keepWaitingPipelines: false,
    lastModifiedBy: 'anonymous',
    limitConcurrent: true,
    stages: [{ name: 'Find Image from Cluster', refId: '1', requisiteStageRefIds: [], type: 'findImage' }],
  };

  it('renders a pipeline object in a template json string', () => {
    const wrapper = mount(<ShowPipelineTemplateJsonModal pipeline={mockPipeline as IPipeline} />);
    const templateStr = wrapper.find('JsonEditor').prop('value');
    const template = JSON.parse(templateStr as string);
    expect(template.pipeline).toEqual(mockPipeline);
  });

  it('dismisses modal with close button', () => {
    const dismissModal = jasmine.createSpy('dismissModal');
    const wrapper = mount(
      <ShowPipelineTemplateJsonModal pipeline={mockPipeline as IPipeline} dismissModal={dismissModal} />,
    );
    const button = wrapper.find('button').filterWhere(n => n.text() === 'Close');
    button.simulate('click');
    expect(dismissModal).toHaveBeenCalled();
  });

  it('updates template json with user input', () => {
    const wrapper = mount(
      <ShowPipelineTemplateJsonModal pipeline={mockPipeline as IPipeline} ownerEmail="example@example.com" />,
    );
    const simulateInputChange = (id: string, value: string) =>
      wrapper.find(id).simulate('change', { target: { value } });

    const mockTemplateMetadata = {
      description: 'mock-template-description',
      name: 'mock-template-name',
      owner: 'mock-template-owner',
    };

    simulateInputChange('#template-name', mockTemplateMetadata.name);
    simulateInputChange('#template-description', mockTemplateMetadata.description);
    simulateInputChange('#template-owner', mockTemplateMetadata.owner);

    const templateStr = wrapper.find('JsonEditor').prop('value');
    const template = JSON.parse(templateStr as string);

    expect(template.metadata.name).toEqual(mockTemplateMetadata.name);
    expect(template.metadata.description).toEqual(mockTemplateMetadata.description);
    expect(template.metadata.owner).toEqual(mockTemplateMetadata.owner);
  });
});
