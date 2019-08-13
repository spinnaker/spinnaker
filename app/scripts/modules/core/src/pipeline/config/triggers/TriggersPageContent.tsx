import * as React from 'react';

import { extend, findIndex } from 'lodash';

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

export interface ITriggersPageContentProps {
  application: Application;
  pipeline: IPipeline;
  updatePipelineConfig: (changes: Partial<IPipeline>) => void;
}

export function TriggersPageContent(props: ITriggersPageContentProps) {
  const showProperties = SETTINGS.feature.quietPeriod || SETTINGS.feature.managedServiceAccounts;
  const { pipeline, application, updatePipelineConfig } = props;

  function updateRoles(roles: any[]): void {
    updatePipelineConfig({ roles });
  }

  function addTrigger(): void {
    const triggerTypes = Registry.pipeline.getTriggerTypes();
    let newTrigger: ITrigger = { enabled: true, type: null };
    if (triggerTypes.length === 1) {
      newTrigger = { enabled: true, type: triggerTypes[0].key };
    }
    updatePipelineConfig({ triggers: [...pipeline.triggers, newTrigger] });
  }

  function removeTrigger(triggerIndex: number): void {
    const triggers = pipeline.triggers.slice(0);
    triggers.splice(triggerIndex, 1);
    updatePipelineConfig({ triggers });
  }

  function updateTrigger(index: number, changes: Partial<ITrigger>) {
    const triggers = pipeline.triggers.slice(0);
    extend(triggers[index], changes);
    PipelineConfigValidator.validatePipeline(pipeline);
    updatePipelineConfig({ triggers });
    if (SETTINGS.feature['artifactsRewrite']) {
      removeUnusedExpectedArtifacts(pipeline);
    }
  }

  // Expected Artifacts
  function updateExpectedArtifacts(expectedArtifacts: IExpectedArtifact[]) {
    updatePipelineConfig({ expectedArtifacts });
  }

  function removeUnusedExpectedArtifacts(pipelineParam: IPipeline) {
    // remove unused expected artifacts from the pipeline
    const newExpectedArtifacts: IExpectedArtifact[] = pipelineParam.expectedArtifacts.slice(0);
    pipelineParam.expectedArtifacts.forEach(expectedArtifact => {
      if (
        !pipelineParam.triggers.find(t => t.expectedArtifactIds && t.expectedArtifactIds.includes(expectedArtifact.id))
      ) {
        newExpectedArtifacts.splice(findIndex(newExpectedArtifacts, e => e.id === expectedArtifact.id), 1);
      }
      ArtifactReferenceService.removeReferenceFromStages(expectedArtifact.id, pipelineParam.stages);
    });
    updatePipelineConfig({ expectedArtifacts: newExpectedArtifacts });
  }

  return (
    <div className="triggers">
      {pipeline.triggers.length > 0 && showProperties && (
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
      {pipeline.triggers.map((trigger, index) => (
        <div className="trigger-config" key={index}>
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
      {pipeline.triggers.length === 0 && (
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
