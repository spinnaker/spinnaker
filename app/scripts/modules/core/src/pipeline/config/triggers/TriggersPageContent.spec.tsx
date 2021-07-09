import React from 'react';
import { mount } from 'enzyme';

import { ApplicationModelBuilder } from '../../../application';
import { ArtifactReferenceService } from '../../../artifact';
import { IExpectedArtifact, ITrigger } from '../../../domain';
import { Registry } from '../../../registry';

import { ITriggersPageContentProps, TriggersPageContent } from './TriggersPageContent';

describe('<TriggersPageContent />', () => {
  let removeReferencesFromStagesSpy: jasmine.Spy;
  let updatePipelineSpy: jasmine.Spy;

  let props: ITriggersPageContentProps;

  let newTrigger: ITrigger;
  let triggerA: ITrigger;
  let triggerB: ITrigger;

  let expectedArtifactA: IExpectedArtifact;
  let expectedArtifactB: IExpectedArtifact;

  beforeEach(() => {
    spyOn(Registry.pipeline, 'getTriggerTypes').and.returnValue([{ key: 'cron' }, { key: 'git' }]);

    removeReferencesFromStagesSpy = spyOn(ArtifactReferenceService, 'removeReferencesFromStages');
    updatePipelineSpy = jasmine.createSpy('updatePipeline');

    props = {
      application: ApplicationModelBuilder.createApplicationForTests('my-application'),
      pipeline: {
        application: 'my-application',
        id: 'pipeline-id',
        limitConcurrent: true,
        keepWaitingPipelines: true,
        name: 'My Pipeline',
        parameterConfig: [],
        stages: [],
        triggers: [],
      },
      updatePipelineConfig: updatePipelineSpy,
    };

    newTrigger = { enabled: true, type: null };
    triggerA = { enabled: true, type: 'cron' };
    triggerB = { enabled: true, type: 'git' };

    expectedArtifactA = {
      id: 'expected-artifact-a',
      displayName: 'tasty-otter-27',
      useDefaultArtifact: false,
      usePriorArtifact: false,
      matchArtifact: null,
      defaultArtifact: null,
    };
    expectedArtifactB = {
      id: 'expected-artifact-b',
      displayName: 'sad-tarantula-28',
      useDefaultArtifact: false,
      usePriorArtifact: false,
      matchArtifact: null,
      defaultArtifact: null,
    };
  });

  describe('Adding a trigger', () => {
    it('Adds a first trigger to the pipeline', () => {
      const component = mount(<TriggersPageContent {...props} />);
      expect(updatePipelineSpy).toHaveBeenCalledTimes(0);
      component.find('.btn-add-trigger').simulate('click');
      expect(updatePipelineSpy).toHaveBeenCalledTimes(1);
      expect(updatePipelineSpy).toHaveBeenCalledWith({ triggers: [newTrigger] });
    });
    it('Adds a second trigger to the pipeline', () => {
      const component = mount(
        <TriggersPageContent {...props} pipeline={{ ...props.pipeline, triggers: [triggerA] }} />,
      );
      expect(updatePipelineSpy).toHaveBeenCalledTimes(0);
      component.find('.btn-add-trigger').simulate('click');
      expect(updatePipelineSpy).toHaveBeenCalledTimes(1);
      expect(updatePipelineSpy).toHaveBeenCalledWith({ triggers: [triggerA, newTrigger] });
    });
  });

  describe('Editing a trigger', () => {
    it('Edits a property of an existing trigger', () => {
      const component = mount(
        <TriggersPageContent {...props} pipeline={{ ...props.pipeline, triggers: [triggerA] }} />,
      );
      expect(updatePipelineSpy).toHaveBeenCalledTimes(0);
      component.find('.enable-trigger-checkbox').simulate('change', { target: { checked: false } });
      component.update();
      expect(updatePipelineSpy).toHaveBeenCalledTimes(1);
      expect(updatePipelineSpy).toHaveBeenCalledWith({ triggers: [{ ...triggerA, enabled: false }] });
    });
  });

  describe('Removing a trigger', () => {
    it('Removes the trigger from the pipeline', () => {
      const component = mount(
        <TriggersPageContent {...props} pipeline={{ ...props.pipeline, triggers: [triggerA, triggerB] }} />,
      );
      expect(updatePipelineSpy).toHaveBeenCalledTimes(0);
      component.find('.glyphicon-trash').at(0).simulate('click');
      expect(updatePipelineSpy).toHaveBeenCalledTimes(1);
      expect(updatePipelineSpy).toHaveBeenCalledWith({ triggers: [triggerB] });
    });
    it('Removes expected artifacts if associated only with the removed trigger', () => {
      const component = mount(
        <TriggersPageContent
          {...props}
          pipeline={{
            ...props.pipeline,
            expectedArtifacts: [expectedArtifactA, expectedArtifactB],
            triggers: [{ ...triggerA, expectedArtifactIds: [expectedArtifactA.id] }, triggerB],
          }}
        />,
      );
      expect(updatePipelineSpy).toHaveBeenCalledTimes(0);
      expect(removeReferencesFromStagesSpy).toHaveBeenCalledTimes(0);
      component.find('.glyphicon-trash').at(0).simulate('click');
      expect(updatePipelineSpy).toHaveBeenCalledTimes(1);
      expect(removeReferencesFromStagesSpy).toHaveBeenCalledTimes(1);
      expect(updatePipelineSpy).toHaveBeenCalledWith({ triggers: [triggerB], expectedArtifacts: [expectedArtifactB] });
      expect(removeReferencesFromStagesSpy).toHaveBeenCalledWith([expectedArtifactA.id], props.pipeline.stages);
    });
    it('Does not remove expected artifacts if associated with multiple triggers', () => {
      const component = mount(
        <TriggersPageContent
          {...props}
          pipeline={{
            ...props.pipeline,
            expectedArtifacts: [expectedArtifactA, expectedArtifactB],
            triggers: [
              { ...triggerA, expectedArtifactIds: [expectedArtifactA.id] },
              { ...triggerB, expectedArtifactIds: [expectedArtifactA.id] },
            ],
          }}
        />,
      );
      expect(updatePipelineSpy).toHaveBeenCalledTimes(0);
      component.find('.glyphicon-trash').at(0).simulate('click');
      expect(updatePipelineSpy).toHaveBeenCalledTimes(1);
      expect(removeReferencesFromStagesSpy).toHaveBeenCalledTimes(0);
      expect(updatePipelineSpy).toHaveBeenCalledWith({
        triggers: [{ ...triggerB, expectedArtifactIds: [expectedArtifactA.id] }],
      });
    });
  });
});
