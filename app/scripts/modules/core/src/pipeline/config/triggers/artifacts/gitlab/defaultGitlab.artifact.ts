import { module } from 'angular';

import { ArtifactTypePatterns } from 'core/artifact';
import { IArtifact } from 'core/domain/IArtifact';
import { Registry } from 'core/registry';

export const DEFAULT_GITLAB_ARTIFACT = 'spinnaker.core.pipeline.trigger.artifact.defaultGitlab';
module(DEFAULT_GITLAB_ARTIFACT, []).config(() => {
  Registry.pipeline.mergeArtifactKind({
    label: 'Gitlab',
    typePattern: ArtifactTypePatterns.GITLAB_FILE,
    type: 'gitlab/file',
    description: 'A file stored in git, hosted by Gitlab.',
    key: 'default.gitlab',
    isDefault: true,
    isMatch: false,
    controller: function(artifact: IArtifact) {
      this.artifact = artifact;
      this.artifact.type = 'gitlab/file';
      const pathRegex = new RegExp('/api/v4/projects/[^/]*/[^/]*/repository/files/(.*)$');

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
      <help-field key="pipeline.config.expectedArtifact.defaultGitlab.reference"></help-field>
    </label>
    <div class="col-md-8">
      <input type="text"
             placeholder="https://gitlab.com/api/v4/projects/$ORG%2F$REPO/repository/files/path%2Fto%2Ffile.yml/raw"
             class="form-control input-sm"
             ng-change="ctrl.onReferenceChange()"
             ng-model="ctrl.artifact.reference"/>
    </div>
  </div>
  <div class="form-group row">
    <label class="col-md-3 sm-label-right">
      Commit/Branch
      <help-field key="pipeline.config.expectedArtifact.defaultGitlab.version"></help-field>
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
