import { module } from 'angular';

import { ArtifactTypePatterns } from 'core/artifact';
import { IArtifact } from 'core/domain/IArtifact';
import { Registry } from 'core/registry';

export const GCS_ARTIFACT = 'spinnaker.core.pipeline.trigger.gcs.artifact';
module(GCS_ARTIFACT, []).config(() => {
  Registry.pipeline.mergeArtifactKind({
    label: 'GCS',
    typePattern: ArtifactTypePatterns.GCS_OBJECT,
    type: 'gcs/object',
    description: 'A GCS object.',
    key: 'gcs',
    isDefault: false,
    isMatch: true,
    controller: function(artifact: IArtifact) {
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
