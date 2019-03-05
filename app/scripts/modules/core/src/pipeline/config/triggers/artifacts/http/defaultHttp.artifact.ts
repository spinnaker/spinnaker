import { module } from 'angular';

import { ArtifactTypePatterns } from 'core/artifact';
import { IArtifact } from 'core/domain/IArtifact';
import { Registry } from 'core/registry';

export const DEFAULT_HTTP_ARTIFACT = 'spinnaker.core.pipeline.trigger.defaultHttp.artifact';
module(DEFAULT_HTTP_ARTIFACT, []).config(() => {
  Registry.pipeline.mergeArtifactKind({
    label: 'HTTP',
    typePattern: ArtifactTypePatterns.HTTP_FILE,
    type: 'http/file',
    description: 'An HTTP artifact.',
    key: 'default.http',
    isDefault: true,
    isMatch: false,
    controller: function(artifact: IArtifact) {
      this.artifact = artifact;
      this.artifact.type = 'http/file';
    },
    controllerAs: 'ctrl',
    template: `
<div class="col-md-12">
  <div class="form-group row">
    <label class="col-md-2 sm-label-right">
      URL
    </label>
    <div class="col-md-8">
      <input type="text"
             placeholder="http://host/path/file.ext"
             class="form-control input-sm"
             ng-model="ctrl.artifact.name" />
    </div>
  </div>
</div>
`,
  });
});
