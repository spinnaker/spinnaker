import React from 'react';
import { mount } from 'enzyme';

import { IPipeline, IPipelineTemplateV2 } from '../../../../domain';
import { PipelineTemplateV2Service } from '../../templates/v2/pipelineTemplateV2.service';
import { ShowPipelineTemplateJsonModal } from './ShowPipelineTemplateJsonModal';

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

  it('dismisses modal with close button', () => {
    const dismissModal = jasmine.createSpy('dismissModal');
    const wrapper = mount(<ShowPipelineTemplateJsonModal template={mockTemplate} dismissModal={dismissModal} />);
    const button = wrapper.find('button').filterWhere((n) => n.text() === 'Close');
    button.simulate('click');
    expect(dismissModal).toHaveBeenCalled();
  });

  it('updates template json with user input', () => {
    const wrapper = mount(<ShowPipelineTemplateJsonModal template={mockTemplate} />);
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
