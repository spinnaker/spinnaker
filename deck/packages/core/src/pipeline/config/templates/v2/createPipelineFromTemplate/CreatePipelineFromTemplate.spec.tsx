import { shallow } from 'enzyme';
import React from 'react';

import { CreatePipelineFromTemplateComponent } from './CreatePipelineFromTemplate';
import { CreatePipelineModal } from '../../../../create';

describe('CreatePipelineFromTemplate', () => {
  it('opens a saved pipeline through the injected state service', () => {
    const injectedGo = jasmine.createSpy('injectedGo');
    const component = shallow(
      <CreatePipelineFromTemplateComponent
        {...({ router: {}, stateParams: {}, stateService: { go: injectedGo } } as any)}
        closeModalCallback={() => undefined}
        template={{} as any}
      />,
      { disableLifecycleMethods: true },
    );
    component.setState({ applicationSelectionComplete: true, loadedApplication: { name: 'test-app' } });

    (component.find(CreatePipelineModal).prop('pipelineSavedCallback') as (id: string) => void)('pipeline-id');

    expect(injectedGo).toHaveBeenCalledWith('home.applications.application.pipelines.pipelineConfig', {
      application: 'test-app',
      pipelineId: 'pipeline-id',
      new: 1,
    });
  });
});
