import { module } from 'angular';
import { isNil } from 'lodash';

import { IArtifact } from 'core/domain/IArtifact';
import { Registry } from 'core/registry';

export const DEFAULT_GCS_ARTIFACT = 'spinnaker.core.pipeline.trigger.artifact.defaultGcs';
module(DEFAULT_GCS_ARTIFACT, []).config(() => {
  Registry.pipeline.registerArtifactKind({
    label: 'GCS',
    type: 'gcs/object',
    description: 'A GCS object.',
    key: 'default.gcs',
    isDefault: true,
    isMatch: false,
    controller: function(artifact: IArtifact) {
      this.artifact = artifact;
      this.artifact.type = 'gcs/object';

      this.onReferenceChange = () => {
        const ref = this.artifact.reference;
        if (isNil(ref)) {
          return;
        }

        if (ref.indexOf('#') >= 0) {
          const split = ref.split('#');
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
      Object path
      <help-field key="pipeline.config.expectedArtifact.defaultGcs.reference"></help-field>
    </label>
    <div class="col-md-8">
      <input type="text"
             placeholder="gs://bucket/path/to/file"
             class="form-control input-sm"
             ng-change="ctrl.onReferenceChange()"
             ng-model="ctrl.artifact.reference"/>
    </div>
  </div>
</div>
`,
  });
});
