import { IController, module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER } from 'core/pipeline/config/pipelineConfigProvider';
import { IArtifact } from 'core/domain/IArtifact';
import { PipelineConfigProvider } from 'core/pipeline';

class DockerArtifactController implements IController {
  constructor(public artifact: IArtifact) {
    'ngInject';
    this.artifact.type = 'docker/image';
  }
}

export const DOCKER_ARTIFACT = 'spinnaker.core.pipeline.trigger.artifact.docker';
module(DOCKER_ARTIFACT, [
  PIPELINE_CONFIG_PROVIDER,
]).config((pipelineConfigProvider: PipelineConfigProvider) => {
  pipelineConfigProvider.registerArtifactKind({
    label: 'Docker',
    description: 'A Docker image to be deployed.',
    key: 'docker',
    controller: 'dockerArtifactCtrl',
    controllerAs: 'ctrl',
    template: `
<div class="col-md-12">
  <div class="form-group row">
    <label class="col-md-2 sm-label-right">
      Docker image
      <help-field key="pipeline.config.expectedArtifact.docker.name"></help-field>
    </label>
    <div class="col-md-8">
      <input type="text"
             placeholder="gcr.io/project/image:release"
             class="form-control input-sm"
             ng-model="ctrl.artifact.name"/>
    </div>
  </div>
</div>
`,
  });
}).controller('dockerArtifactCtrl', DockerArtifactController);

