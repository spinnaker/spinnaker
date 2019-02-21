import { module } from 'angular';

import { IArtifact } from 'core/domain/IArtifact';
import { Registry } from 'core/registry';
import { BitbucketArtifactEditor } from './BitbucketArtifactEditor';

export const BITBUCKET_ARTIFACT = 'spinnaker.core.pipeline.trigger.bitbucket.artifact';
module(BITBUCKET_ARTIFACT, []).config(() => {
  Registry.pipeline.registerArtifactKind({
    label: 'Bitbucket',
    type: 'bitbucket/file',
    description: 'A file stored in git, hosted by Bitbucket.',
    key: 'bitbucket',
    isDefault: false,
    isMatch: true,
    controller: function(artifact: IArtifact) {
      this.artifact = artifact;
      this.artifact.type = 'bitbucket/file';
    },
    controllerAs: 'ctrl',
    editCmp: BitbucketArtifactEditor,
    template: `
<div class="col-md-12">
  <div class="form-group row">
    <label class="col-md-2 sm-label-right">
      File path
      <help-field key="pipeline.config.expectedArtifact.git.name"></help-field>
    </label>
    <div class="col-md-8">
      <input type="text"
             placeholder="manifests/frontend.yaml"
             class="form-control input-sm"
             ng-model="ctrl.artifact.name"/>
    </div>
  </div>
</div>
`,
  });
});
