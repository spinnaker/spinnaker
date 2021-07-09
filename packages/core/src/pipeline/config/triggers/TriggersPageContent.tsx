import React from 'react';

import { PipelineRoles } from './PipelineRoles';
import { Trigger } from './Trigger';
import { Application } from '../../../application';
import { ArtifactReferenceService } from '../../../artifact';
import { SETTINGS } from '../../../config/settings';
import { IExpectedArtifact, IPipeline, ITrigger } from '../../../domain';
import { HelpField } from '../../../help';
import { CheckboxInput, FormField } from '../../../presentation';
import { Registry } from '../../../registry';
import { PipelineConfigValidator } from '../validation/PipelineConfigValidator';

const { useState } = React;

export interface ITriggersPageContentProps {
  application: Application;
  pipeline: IPipeline;
  updatePipelineConfig: (changes: Partial<IPipeline>) => void;
}

export function TriggersPageContent(props: ITriggersPageContentProps) {
  const showProperties = SETTINGS.feature.quietPeriod || SETTINGS.feature.managedServiceAccounts;
  const { pipeline, application, updatePipelineConfig } = props;
  const expectedArtifacts = pipeline?.expectedArtifacts ?? [];
  const triggers = pipeline?.triggers ?? [];
  // KLUDGE: because we don't have a stable identifier to use for each trigger object, we need to reset the keys
  // used for each trigger in the list when a delete happens (because the array of triggers shifts by one).
  // For now, we do this by incrementing a counter every time a delete happens, and sticking that number
  // into the key of each trigger before its index value. Ideally, we should generate stable UUIDs for
  // each trigger object that we can use instead of relying on index keys.
  const [deleteCount, setDeleteCount] = useState(0);

  function updateRoles(roles: any[]): void {
    updatePipelineConfig({ roles });
  }

  /**
   * Adds a new trigger to the pipeline. If only one trigger type is registered,
   * defaults the new trigger to that type.
   */
  function addTrigger(): void {
    const triggerTypes = Registry.pipeline.getTriggerTypes();
    let newTrigger: ITrigger = { enabled: true, type: null };
    if (triggerTypes.length === 1) {
      newTrigger = { enabled: true, type: triggerTypes[0].key };
    }
    updatePipelineConfig({ triggers: [...triggers, newTrigger] });
  }

  /**
   * Removes a trigger from the pipeline. Also handles removing each of the
   * trigger's associated expected artifacts (that are associated with no other
   * trigger) from the pipeline if the standard artifacts UI is enabled.
   *
   * @param indexToRemove The index of the trigger to remove
   */
  function removeTrigger(indexToRemove: number): void {
    const triggerToRemove = triggers[indexToRemove];
    const updatedTriggers = triggers.filter((_t, i) => i !== indexToRemove);
    const artifactIdsToRemove = (triggerToRemove?.expectedArtifactIds ?? []).filter((id) => {
      return !updatedTriggers.some((trigger) => (trigger.expectedArtifactIds || []).includes(id));
    });
    const pipelineUpdate: Partial<IPipeline> = {
      triggers: updatedTriggers,
    };

    if (artifactIdsToRemove.length > 0) {
      const updatedExpectedArtifacts = expectedArtifacts.filter(({ id }) => !artifactIdsToRemove.includes(id));
      pipelineUpdate.expectedArtifacts = updatedExpectedArtifacts;
      ArtifactReferenceService.removeReferencesFromStages(artifactIdsToRemove, pipeline.stages);
    }

    updatePipelineConfig(pipelineUpdate);
    setDeleteCount(deleteCount + 1);
  }

  /**
   * Updates a pipeline trigger.
   *
   * @param indexToUpdate The index of the trigger to update
   * @param updatedTrigger The updated trigger
   */
  function updateTrigger(indexToUpdate: number, updatedTrigger: ITrigger): void {
    PipelineConfigValidator.validatePipeline(pipeline);
    updatePipelineConfig({
      triggers: triggers.map((trigger, index) => {
        if (index === indexToUpdate) {
          return updatedTrigger;
        }
        return trigger;
      }),
    });
  }

  /**
   * Adds an expected artifact to the pipeline.
   *
   * @param artifact The expected artifact to add to the pipeline
   */
  function addExpectedArtifact(artifact: IExpectedArtifact): void {
    updatePipelineConfig({
      expectedArtifacts: expectedArtifacts.concat([artifact]),
    });
  }

  /**
   * Updates an expected artifact.
   *
   * @param updatedArtifact The updated artifact
   */
  function updateExpectedArtifact(updatedArtifact: IExpectedArtifact): void {
    updatePipelineConfig({
      expectedArtifacts: expectedArtifacts.map((artifact) => {
        if (artifact.id === updatedArtifact.id) {
          return updatedArtifact;
        }
        return artifact;
      }),
    });
  }

  /**
   * Removes an expected artifact from the pipeline. Also handles removing
   * any references to the artifact from pipeline stages.
   *
   * Because each trigger's state is managed by Formik within the Trigger
   * component, we cannot make a top-down update of triggers that reference the
   * removed artifact, so each trigger is responsible for updating its own list
   * of associated artifacts in response to changes to
   * `pipeline.expectedArtifacts`.
   *
   * @param artifact The artifact to remove
   */
  function removeExpectedArtifact(artifactToRemove: IExpectedArtifact): void {
    updatePipelineConfig({
      expectedArtifacts: expectedArtifacts.filter((a) => a.id !== artifactToRemove.id),
    });
    ArtifactReferenceService.removeReferenceFromStages(artifactToRemove.id, pipeline.stages);
  }

  return (
    <div className="triggers">
      {triggers.length > 0 && showProperties && (
        <div className="form-horizontal panel-pipeline-phase">
          <div className="form-group row">
            <div className="col-md-12">
              <div className="trigger-config">
                {SETTINGS.feature.managedServiceAccounts && (
                  <PipelineRoles roles={pipeline.roles} updateRoles={updateRoles} />
                )}
                {SETTINGS.feature.quietPeriod && (
                  <div className="row">
                    <div className="col-md-10">
                      <div className="col-md-9 col-md-offset-3">
                        <FormField
                          name="respectQuietPeriod"
                          onChange={() => {
                            updatePipelineConfig({ respectQuietPeriod: !pipeline.respectQuietPeriod });
                          }}
                          value={pipeline.respectQuietPeriod}
                          input={(inputProps) => (
                            <CheckboxInput
                              {...inputProps}
                              text={
                                <>
                                  Disable automated triggers during quiet period (
                                  <strong>does not affect Pipeline triggers</strong>).
                                  <HelpField id="pipeline.config.triggers.respectQuietPeriod" />
                                </>
                              }
                            />
                          )}
                        />
                      </div>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
      {triggers.map((trigger, index) => (
        <div className="trigger-config" key={`${deleteCount}:${index}`}>
          <Trigger
            application={application}
            index={index}
            pipeline={pipeline}
            removeTrigger={removeTrigger}
            triggerInitialValues={trigger}
            updateTrigger={updateTrigger}
            addExpectedArtifact={addExpectedArtifact}
            updateExpectedArtifact={updateExpectedArtifact}
            removeExpectedArtifact={removeExpectedArtifact}
          />
        </div>
      ))}
      {triggers.length === 0 && (
        <div className="row">
          <p className="col-md-12">You don't have any triggers configured for {pipeline.name}.</p>
        </div>
      )}
      <div className="row">
        <div className="col-md-12">
          <button className="btn btn-block btn-add-trigger add-new" onClick={() => addTrigger()}>
            <span className="glyphicon glyphicon-plus-sign" /> Add Trigger
          </button>
        </div>
      </div>
    </div>
  );
}
