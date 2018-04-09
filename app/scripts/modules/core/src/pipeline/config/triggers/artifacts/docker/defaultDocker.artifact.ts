import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER } from 'core/pipeline/config/pipelineConfigProvider';
import { IArtifact } from 'core/domain/IArtifact';
import { PipelineConfigProvider } from 'core/pipeline';
import { isNil } from 'lodash';

export const DEFAULT_DOCKER_ARTIFACT = 'spinnaker.core.pipeline.trigger.artifact.defaultDocker';
module(DEFAULT_DOCKER_ARTIFACT, [PIPELINE_CONFIG_PROVIDER]).config((pipelineConfigProvider: PipelineConfigProvider) => {
  pipelineConfigProvider.registerArtifactKind({
    label: 'Docker',
    isDefault: true,
    isMatch: false,
    description: 'A Docker image to be deployed.',
    key: 'default.docker',
    controller(artifact: IArtifact) {
      'ngInject';
      this.artifact = artifact;
      this.artifact.type = 'docker/image';

      this.onReferenceChange = () => {
        const ref = this.artifact.reference;
        if (isNil(ref)) {
          return;
        }

        if (ref.indexOf('@') >= 0) {
          const split = ref.split('@');
          this.artifact.name = split[0];
          this.artifact.version = split[1];
        } else if (ref.indexOf(':') >= 0) {
          const split = ref.split(':');
          this.artifact.name = split[0];
          this.artifact.version = split[1];
        } else {
          this.artifact.name = ref;
        }
      };
    },
    controllerAs: 'ctrl',
    template: `
<div class="col-md-12">
  <div class="form-group row">
    <label class="col-md-2 sm-label-right">
      Docker image
      <help-field key="pipeline.config.expectedArtifact.defaultDocker.reference"></help-field>
    </label>
    <div class="col-md-8">
      <input type="text"
             placeholder="gcr.io/project/image@sha256:9efcc2818c9..."
             class="form-control input-sm"
             ng-change="ctrl.onReferenceChange()"
             ng-model="ctrl.artifact.reference"/>
    </div>
  </div>
</div>
`,
  });
});
