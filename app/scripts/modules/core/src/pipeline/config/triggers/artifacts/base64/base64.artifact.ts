import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER } from 'core/pipeline/config/pipelineConfigProvider';
import { IArtifact } from 'core/domain/IArtifact';
import { PipelineConfigProvider } from 'core/pipeline';

import './base64.artifact.less';

export const BASE64_ARTIFACT = 'spinnaker.core.pipeline.trigger.artifact.base64';
module(BASE64_ARTIFACT, [PIPELINE_CONFIG_PROVIDER]).config((pipelineConfigProvider: PipelineConfigProvider) => {
  pipelineConfigProvider.registerArtifactKind({
    label: 'Base64',
    description: 'An artifact that includes its referenced resource as part of its payload.',
    key: 'base64',
    isDefault: false,
    isMatch: true,
    controller(artifact: IArtifact) {
      'ngInject';
      this.artifact = artifact;
      this.artifact.type = 'embedded/base64';
    },
    controllerAs: 'ctrl',
    template: `
<div class="col-md-12">
  <div class="form-group row">
    <label class="col-md-2 sm-label-right">
      Name
    </label>
    <div class="col-md-8">
      <input type="text"
             placeholder="base64-artifact"
             class="form-control input-sm"
             ng-model="ctrl.artifact.name" />
    </div>
  </div>
</div>
`,
  });
});
