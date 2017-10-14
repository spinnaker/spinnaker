import { IComponentController, IComponentOptions, module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER } from 'core/pipeline/config/pipelineConfigProvider';
import { IArtifact } from 'core/domain/IArtifact';

class ArtifactController implements IComponentController {
  public artifact: IArtifact;
}

class ArtifactComponent implements IComponentOptions {
  public bindings = { artifact: '=' };
  public templateUrl = require('./artifact.html');
  public controller = ArtifactController;
  public controllerAs = 'ctrl';
}

export const ARTIFACT = 'spinnaker.core.pipeline.trigger.artifact';
module(ARTIFACT, [
  PIPELINE_CONFIG_PROVIDER,
]).component('artifact', new ArtifactComponent());
