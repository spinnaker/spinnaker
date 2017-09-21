import { IComponentController, IComponentOptions, module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER } from 'core/pipeline/config/pipelineConfigProvider';
import { IExpectedArtifact, MissingArtifactPolicy } from 'core/domain/IExpectedArtifact';
import { IPipeline } from 'core/domain/IPipeline';

class ArtifactController implements IComponentController {
  public artifact: IExpectedArtifact;
  public pipeline: IPipeline;
  public missingPolicies: string[] = Object.keys(MissingArtifactPolicy).map(key => MissingArtifactPolicy[key as any]);

  public removeArtifact(): void {
    const artifactIndex = this.pipeline.expectedArtifacts.indexOf(this.artifact);
    this.pipeline.expectedArtifacts.splice(artifactIndex, 1);

    this.pipeline.triggers
      .forEach(t => t.expectedArtifacts = t.expectedArtifacts.filter(a => !this.isEqual(a, this.artifact)));
  }

  private isEqual(first: IExpectedArtifact, other: IExpectedArtifact): boolean {
    return first.name === other.name
      && first.type === other.type;
  }
}

class ArtifactComponent implements IComponentOptions {
  public bindings = { artifact: '<', pipeline: '<' };
  public templateUrl = require('./artifact.html');
  public controller = ArtifactController;
}

export const ARTIFACT = 'spinnaker.core.pipeline.trigger.artifact';
module(ARTIFACT, [
  PIPELINE_CONFIG_PROVIDER,
]).component('artifact', new ArtifactComponent());
