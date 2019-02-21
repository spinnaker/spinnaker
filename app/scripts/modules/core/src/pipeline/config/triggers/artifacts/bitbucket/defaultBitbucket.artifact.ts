import { module } from 'angular';

import { IArtifact } from 'core/domain/IArtifact';
import { Registry } from 'core/registry';

export const DEFAULT_BITBUCKET_ARTIFACT = 'spinnaker.core.pipeline.trigger.artifact.defaultBitbucket';
module(DEFAULT_BITBUCKET_ARTIFACT, []).config(() => {
  Registry.pipeline.registerArtifactKind({
    label: 'Bitbucket',
    type: 'bitbucket/file',
    description: 'A file stored in git, hosted by Bitbucket.',
    key: 'default.bitbucket',
    isDefault: true,
    isMatch: false,
    controller: function(artifact: IArtifact) {
      this.artifact = artifact;
      this.artifact.type = 'bitbucket/file';
      const pathRegex = new RegExp('/1.0/repositories/[^/]*/[^/]*/raw/[^/]*/(.*)$');

      this.onReferenceChange = () => {
        const results = pathRegex.exec(this.artifact.reference);
        if (results !== null) {
          this.artifact.name = decodeURIComponent(results[1]);
        }
      };
    },
    controllerAs: 'ctrl',
    template: `
<div class="col-md-12">
  <div class="form-group row">
    <label class="col-md-3 sm-label-right">
      Content URL
      <help-field key="pipeline.config.expectedArtifact.defaultBitbucket.reference"></help-field>
    </label>
    <div class="col-md-8">
      <input type="text"
             placeholder="https://api.bitbucket.org/1.0/repositories/$ORG/$REPO/raw/$VERSION/$FILEPATH"
             class="form-control input-sm"
             ng-change="ctrl.onReferenceChange()"
             ng-model="ctrl.artifact.reference"/>
    </div>
  </div>
</div>
`,
  });
});
