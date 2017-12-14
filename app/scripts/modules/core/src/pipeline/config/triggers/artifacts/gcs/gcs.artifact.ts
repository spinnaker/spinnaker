import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER } from 'core/pipeline/config/pipelineConfigProvider';
import { IArtifact } from 'core/domain/IArtifact';
import { PipelineConfigProvider } from 'core/pipeline';

export const GCS_ARTIFACT = 'spinnaker.core.pipeline.trigger.gcs.artifact';
module(GCS_ARTIFACT, [
  PIPELINE_CONFIG_PROVIDER,
]).config((pipelineConfigProvider: PipelineConfigProvider) => {
  pipelineConfigProvider.registerArtifactKind({
    label: 'GCS',
    description: 'A GCS object.',
    key: 'gcs',
    controller(artifact: IArtifact) {
      'ngInject';
      this.artifact = artifact;
      this.artifact.type = 'gcs/object';
    },
    controllerAs: 'ctrl',
    template: `
<div class="col-md-12">
  <div class="form-group row">
    <label class="col-md-2 sm-label-right">
      Object path
      <help-field key="pipeline.config.expectedArtifact.gcs.name"></help-field>
    </label>
    <div class="col-md-8">
      <input type="text"
             placeholder="gs://bucket/path/to/file"
             class="form-control input-sm"
             ng-model="ctrl.artifact.name"/>
    </div>
  </div>
</div>
`,
  });
});

