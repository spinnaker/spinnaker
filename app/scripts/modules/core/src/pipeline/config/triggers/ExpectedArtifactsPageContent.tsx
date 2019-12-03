import React from 'react';

import { ArtifactReferenceService } from 'core/artifact/ArtifactReferenceService';
import { ExpectedArtifactService } from 'core/artifact/expectedArtifact.service';
import { IExpectedArtifact, IPipeline } from 'core/domain';
import { HelpField } from 'core/help';
import { ExpectedArtifact } from './artifacts';

export interface IExpectedArtifactsPageContentProps {
  pipeline: IPipeline;
  updatePipelineConfig: (changes: Partial<IPipeline>) => void;
}

export function ExpectedArtifactsPageContent(props: IExpectedArtifactsPageContentProps) {
  const { pipeline, updatePipelineConfig } = props;

  function addArtifact(): void {
    const expectedArtifacts = pipeline.expectedArtifacts ? pipeline.expectedArtifacts.slice(0) : [];
    expectedArtifacts.push(ExpectedArtifactService.createEmptyArtifact());
    updatePipelineConfig({ expectedArtifacts });
  }

  function removeExpectedArtifact(pipelineContext: IPipeline, expectedArtifact: IExpectedArtifact): void {
    if (!pipelineContext.expectedArtifacts) {
      return;
    }
    updatePipelineConfig({
      expectedArtifacts: pipelineContext.expectedArtifacts.filter(a => a.id !== expectedArtifact.id),
    });

    if (!pipelineContext.triggers) {
      return;
    }
    const triggers = pipelineContext.triggers.map(t => {
      if (t.expectedArtifactIds) {
        t.expectedArtifactIds = t.expectedArtifactIds.filter(eid => expectedArtifact.id !== eid);
      }
      return t;
    });
    updatePipelineConfig({ triggers });

    ArtifactReferenceService.removeReferenceFromStages(expectedArtifact.id, pipelineContext.stages);
  }

  function updateExpectedArtifact(expectedArtifact: IExpectedArtifact) {
    const newExpectedArtifacts = pipeline.expectedArtifacts.map(artifact => {
      return artifact.id === expectedArtifact.id ? expectedArtifact : artifact;
    });
    updatePipelineConfig({
      expectedArtifacts: newExpectedArtifacts,
    });
  }

  return (
    <>
      <div className="row">
        <div className="col-md-12">
          Declare artifacts your pipeline expects during execution in this section.
          <HelpField id="pipeline.config.artifact.help" />
        </div>
      </div>
      {pipeline.expectedArtifacts &&
        pipeline.expectedArtifacts.map((e, i) => (
          <div className={'expected-artifact'} key={i}>
            <ExpectedArtifact
              expectedArtifact={e}
              pipeline={pipeline}
              removeExpectedArtifact={removeExpectedArtifact}
              updateExpectedArtifact={updateExpectedArtifact}
              usePriorExecution={true}
            />
          </div>
        ))}
      <hr />
      <div className="row">
        <div className="col-md-12">
          <button className="btn btn-block btn-add-trigger add-new" onClick={() => addArtifact()}>
            <span className="glyphicon glyphicon-plus-sign" /> Add Artifact
          </button>
        </div>
      </div>
    </>
  );
}
