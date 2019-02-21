import { module } from 'angular';

import { IArtifact } from 'core/domain/IArtifact';
import { Registry } from 'core/registry';
import { S3ArtifactEditor } from './S3ArtifactEditor';

export const S3_ARTIFACT = 'spinnaker.core.pipeline.trigger.s3.artifact';
module(S3_ARTIFACT, []).config(() => {
  Registry.pipeline.registerArtifactKind({
    label: 'S3',
    type: 's3/object',
    description: 'An S3 object.',
    key: 's3',
    isDefault: false,
    isMatch: true,
    controller: function(artifact: IArtifact) {
      this.artifact = artifact;
      this.artifact.type = 's3/object';
    },
    controllerAs: 'ctrl',
    editCmp: S3ArtifactEditor,
    template: `
<div class="col-md-12">
  <div class="form-group row">
    <label class="col-md-2 sm-label-right">
      Object path
      <help-field key="pipeline.config.expectedArtifact.s3.name"></help-field>
    </label>
    <div class="col-md-8">
      <input type="text"
             placeholder="s3://bucket/path/to/file"
             class="form-control input-sm"
             ng-model="ctrl.artifact.name"/>
    </div>
  </div>
</div>
`,
  });
});
