import { module } from 'angular';

import { IArtifact } from 'core/domain/IArtifact';
import { Registry } from 'core/registry';

export const DEFAULT_GITHUB_ARTIFACT = 'spinnaker.core.pipeline.trigger.artifact.defaultGithub';
module(DEFAULT_GITHUB_ARTIFACT, []).config(() => {
  Registry.pipeline.registerArtifactKind({
    label: 'GitHub',
    type: 'github/file',
    description: 'A file stored in git, hosted by GitHub.',
    key: 'default.github',
    isDefault: true,
    isMatch: false,
    controller: function(artifact: IArtifact) {
      this.artifact = artifact;
      this.artifact.type = 'github/file';
      const pathRegex = new RegExp('/repos/[^/]*/[^/]*/contents/(.*)$');

      this.onReferenceChange = () => {
        const results = pathRegex.exec(this.artifact.reference);
        if (results !== null) {
          this.artifact.name = results[1];
        }
      };
    },
    controllerAs: 'ctrl',
    template: `
<div class="col-md-12">
  <div class="form-group row">
    <label class="col-md-3 sm-label-right">
      Content URL
      <help-field key="pipeline.config.expectedArtifact.defaultGithub.reference"></help-field>
    </label>
    <div class="col-md-8">
      <input type="text"
             placeholder="https://api.github.com/repos/$ORG/$REPO/contents/$FILEPATH"
             class="form-control input-sm"
             ng-change="ctrl.onReferenceChange()"
             ng-model="ctrl.artifact.reference"/>
    </div>
  </div>
  <div class="form-group row">
    <label class="col-md-3 sm-label-right">
      Commit/Branch
      <help-field key="pipeline.config.expectedArtifact.defaultGithub.version"></help-field>
    </label>
    <div class="col-md-3">
      <input type="text"
             placeholder="master"
             class="form-control input-sm"
             ng-model="ctrl.artifact.version"/>
    </div>
  </div>
</div>
`,
  });
});
