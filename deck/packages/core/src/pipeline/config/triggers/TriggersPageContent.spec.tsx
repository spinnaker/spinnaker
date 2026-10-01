import type { Mock } from 'vitest';
import { render, screen } from '@testing-library/react';
import { setupUser } from '../../../utils/testUtils/userEvent';
import React from 'react';

import type { ITriggersPageContentProps } from './TriggersPageContent';
import { TriggersPageContent } from './TriggersPageContent';
import { ApplicationModelBuilder } from '../../../application';
import { ArtifactReferenceService } from '../../../artifact/ArtifactReferenceService';
import type { IExpectedArtifact, ITrigger } from '../../../domain';
import { Registry } from '../../../registry';

describe('<TriggersPageContent />', () => {
  let removeReferencesFromStagesSpy: Mock;
  let updatePipelineSpy: Mock;

  let props: ITriggersPageContentProps;

  let newTrigger: ITrigger;
  let triggerA: ITrigger;
  let triggerB: ITrigger;

  let expectedArtifactA: IExpectedArtifact;
  let expectedArtifactB: IExpectedArtifact;

  beforeEach(() => {
    vi.spyOn(Registry.pipeline, 'getTriggerTypes').mockReturnValue([{ key: 'cron' }, { key: 'git' }]);

    removeReferencesFromStagesSpy = vi
      .spyOn(ArtifactReferenceService, 'removeReferencesFromStages')
      .mockReturnValue(undefined);
    updatePipelineSpy = vi.fn();

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
    it('Adds a first trigger to the pipeline', async () => {
      const user = setupUser();
      render(<TriggersPageContent {...props} />);
      expect(updatePipelineSpy).toHaveBeenCalledTimes(0);
      await user.click(screen.getByRole('button', { name: /Add Trigger/ }));
      expect(updatePipelineSpy).toHaveBeenCalledTimes(1);
      expect(updatePipelineSpy).toHaveBeenCalledWith({ triggers: [newTrigger] });
    });
    it('Adds a second trigger to the pipeline', async () => {
      const user = setupUser();
      render(<TriggersPageContent {...props} pipeline={{ ...props.pipeline, triggers: [triggerA] }} />);
      expect(updatePipelineSpy).toHaveBeenCalledTimes(0);
      await user.click(screen.getByRole('button', { name: /Add Trigger/ }));
      expect(updatePipelineSpy).toHaveBeenCalledTimes(1);
      expect(updatePipelineSpy).toHaveBeenCalledWith({ triggers: [triggerA, newTrigger] });
    });
  });

  describe('Editing a trigger', () => {
    it('Edits a property of an existing trigger', async () => {
      const user = setupUser();
      render(<TriggersPageContent {...props} pipeline={{ ...props.pipeline, triggers: [triggerA] }} />);
      expect(updatePipelineSpy).toHaveBeenCalledTimes(0);
      await user.click(screen.getByRole('checkbox', { name: 'Trigger Enabled' }));
      expect(updatePipelineSpy).toHaveBeenCalledTimes(1);
      expect(updatePipelineSpy).toHaveBeenCalledWith({ triggers: [{ ...triggerA, enabled: false }] });
    });
  });

  describe('Removing a trigger', () => {
    it('Removes the trigger from the pipeline', async () => {
      const user = setupUser();
      render(<TriggersPageContent {...props} pipeline={{ ...props.pipeline, triggers: [triggerA, triggerB] }} />);
      expect(updatePipelineSpy).toHaveBeenCalledTimes(0);
      await user.click(screen.getAllByRole('button', { name: /Remove trigger/ })[0]);
      expect(updatePipelineSpy).toHaveBeenCalledTimes(1);
      expect(updatePipelineSpy).toHaveBeenCalledWith({ triggers: [triggerB] });
    });
    it('Removes expected artifacts if associated only with the removed trigger', async () => {
      const user = setupUser();
      render(
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
      await user.click(screen.getAllByRole('button', { name: /Remove trigger/ })[0]);
      expect(updatePipelineSpy).toHaveBeenCalledTimes(1);
      expect(removeReferencesFromStagesSpy).toHaveBeenCalledTimes(1);
      expect(updatePipelineSpy).toHaveBeenCalledWith({ triggers: [triggerB], expectedArtifacts: [expectedArtifactB] });
      expect(removeReferencesFromStagesSpy).toHaveBeenCalledWith([expectedArtifactA.id], props.pipeline.stages);
    });
    it('Does not remove expected artifacts if associated with multiple triggers', async () => {
      const user = setupUser();
      render(
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
      await user.click(screen.getAllByRole('button', { name: /Remove trigger/ })[0]);
      expect(updatePipelineSpy).toHaveBeenCalledTimes(1);
      expect(removeReferencesFromStagesSpy).toHaveBeenCalledTimes(0);
      expect(updatePipelineSpy).toHaveBeenCalledWith({
        triggers: [{ ...triggerB, expectedArtifactIds: [expectedArtifactA.id] }],
      });
    });
  });
});
