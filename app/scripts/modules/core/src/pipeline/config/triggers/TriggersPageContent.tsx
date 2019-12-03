import React from 'react';

import { findIndex } from 'lodash';

import { Application } from 'core/application';
import { ArtifactReferenceService } from 'core/artifact/ArtifactReferenceService';
import { IExpectedArtifact, IPipeline, ITrigger } from 'core/domain';
import { HelpField } from 'core/help';
import { PipelineConfigValidator } from 'core/pipeline';
import { CheckboxInput, FormField } from 'core/presentation';
import { Registry } from 'core/registry';
import { SETTINGS } from 'core/config/settings';
import { PipelineRoles } from './PipelineRoles';
import { Trigger } from './Trigger';

const { useState } = React;

export interface ITriggersPageContentProps {
  application: Application;
  pipeline: IPipeline;
  updatePipelineConfig: (changes: Partial<IPipeline>) => void;
}

export function TriggersPageContent(props: ITriggersPageContentProps) {
  const showProperties = SETTINGS.feature.quietPeriod || SETTINGS.feature.managedServiceAccounts;
  const {
    pipeline,
    pipeline: { triggers = [] },
    application,
    updatePipelineConfig,
  } = props;
  // must keep track of state to avoid race condition -- Remove once PipelineConfigurer is converted over to React
  const [expectedArtifacts, setExpectedArtifacts] = useState<IExpectedArtifact[]>(
    props.pipeline.expectedArtifacts ? props.pipeline.expectedArtifacts : [],
  );
  // KLUDGE: because we don't have a stable identifier to use for each trigger object, we need to reset the keys
  // used for each trigger in the list when a delete happens (because the array of triggers shifts by one).
  // For now, we do this by incrementing a counter every time a delete happens, and sticking that number
  // into the key of each trigger before its index value. Ideally, we should generate stable UUIDs for
  // each trigger object that we can use instead of relying on index keys.
  const [deleteCount, setDeleteCount] = useState(0);

  function updateRoles(roles: any[]): void {
    updatePipelineConfig({ roles });
  }

  function addTrigger(): void {
    const triggerTypes = Registry.pipeline.getTriggerTypes();
    let newTrigger: ITrigger = { enabled: true, type: null };
    if (triggerTypes.length === 1) {
      newTrigger = { enabled: true, type: triggerTypes[0].key };
    }
    updatePipelineConfig({ triggers: [...triggers, newTrigger] });
  }

  function removeTrigger(triggerIndex: number): void {
    const newTriggers = triggers.slice(0);
    newTriggers.splice(triggerIndex, 1);
    setDeleteCount(deleteCount + 1);
    updatePipelineConfig({ triggers: newTriggers });
  }

  function updateTrigger(index: number, updatedTrigger: ITrigger) {
    const updatedTriggers = triggers.slice(0);
    updatedTriggers[index] = updatedTrigger;
    PipelineConfigValidator.validatePipeline(pipeline);
    updatePipelineConfig({ triggers: updatedTriggers });
    if (SETTINGS.feature['artifactsRewrite']) {
      removeUnusedExpectedArtifacts(pipeline);
    }
  }

  // Expected Artifacts
  function updateExpectedArtifacts(e: IExpectedArtifact[]) {
    setExpectedArtifacts(e);
    updatePipelineConfig({ expectedArtifacts });
  }

  function removeUnusedExpectedArtifacts(pipelineParam: IPipeline) {
    // remove unused expected artifacts from the pipeline
    const newExpectedArtifacts: IExpectedArtifact[] = expectedArtifacts;
    newExpectedArtifacts.forEach(expectedArtifact => {
      if (
        !pipelineParam.triggers.find(t => t.expectedArtifactIds && t.expectedArtifactIds.includes(expectedArtifact.id))
      ) {
        newExpectedArtifacts.splice(findIndex(newExpectedArtifacts, e => e.id === expectedArtifact.id), 1);
      }
      ArtifactReferenceService.removeReferenceFromStages(expectedArtifact.id, pipelineParam.stages);
    });
    setExpectedArtifacts(newExpectedArtifacts);
    updatePipelineConfig({ expectedArtifacts });
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
                          input={inputProps => (
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
            trigger={trigger}
            updateExpectedArtifacts={updateExpectedArtifacts}
            updateTrigger={updateTrigger}
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
