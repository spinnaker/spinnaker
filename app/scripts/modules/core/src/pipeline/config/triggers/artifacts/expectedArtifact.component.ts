import { equals, IComponentController, IComponentOptions, module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER } from 'core/pipeline/config/pipelineConfigProvider';
import { IExpectedArtifact } from 'core/domain/IExpectedArtifact';
import { IPipeline } from 'core/domain/IPipeline';

class ExpectedArtifactController implements IComponentController {
  public expectedArtifact: IExpectedArtifact;
  public pipeline: IPipeline;

  private static expectedArtifactEquals(first: IExpectedArtifact, other: IExpectedArtifact): boolean {
    // Interesting to point out that if two artifact's match artifacts equal, they match the same artifacts & are effectively equal
    return equals(first.matchArtifact, other.matchArtifact);
  }

  public removeExpectedArtifact(): void {
    this.pipeline.expectedArtifacts = this.pipeline.expectedArtifacts
      .filter(a => !ExpectedArtifactController.expectedArtifactEquals(a, this.expectedArtifact));

    this.pipeline.triggers
      .forEach(t => t.expectedArtifacts = t.expectedArtifacts
        .filter(a => !ExpectedArtifactController.expectedArtifactEquals(a, this.expectedArtifact)));
  }
}

class ExpectedArtifactComponent implements IComponentOptions {
  public bindings = { expectedArtifact: '=', pipeline: '=' };
  public templateUrl = require('./expectedArtifact.html');
  public controller = ExpectedArtifactController;
  public controllerAs = 'ctrl';
}

export const EXPECTED_ARTIFACT = 'spinnaker.core.pipeline.trigger.expected.artifact';
module(EXPECTED_ARTIFACT, [
  PIPELINE_CONFIG_PROVIDER,
]).component('expectedArtifact', new ExpectedArtifactComponent());
