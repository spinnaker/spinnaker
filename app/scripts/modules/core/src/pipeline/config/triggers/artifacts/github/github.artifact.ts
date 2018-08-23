import { module } from 'angular';

import { IArtifact } from 'core/domain/IArtifact';
import { Registry } from 'core/registry';
import { GithubArtifactEditor } from './GithubArtifactEditor';

export const GITHUB_ARTIFACT = 'spinnaker.core.pipeline.trigger.github.artifact';
module(GITHUB_ARTIFACT, []).config(() => {
  Registry.pipeline.registerArtifactKind({
    label: 'GitHub',
    description: 'A file stored in git, hosted by GitHub.',
    key: 'github',
    type: 'github/file',
    isDefault: false,
    isMatch: true,
    controller: function(artifact: IArtifact) {
      'ngInject';
      this.artifact = artifact;
      this.artifact.type = 'github/file';
    },
    controllerAs: 'ctrl',
    editCmp: GithubArtifactEditor,
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
