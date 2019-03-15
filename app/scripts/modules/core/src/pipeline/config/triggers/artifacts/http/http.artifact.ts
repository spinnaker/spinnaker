import { IController, module } from 'angular';

import { ArtifactTypePatterns } from 'core/artifact';
import { IArtifact } from 'core/domain/IArtifact';
import { Registry } from 'core/registry';

class HttpArtifactController implements IController {
  public static $inject = ['artifact'];
  constructor(public artifact: IArtifact) {}
}

export const HTTP_ARTIFACT = 'spinnaker.core.pipeline.trigger.http.artifact';
module(HTTP_ARTIFACT, [])
  .config(() => {
    Registry.pipeline.mergeArtifactKind({
      label: 'HTTP',
      typePattern: ArtifactTypePatterns.HTTP_FILE,
      type: 'http/file',
      description: 'An HTTP artifact.',
      key: 'http',
      isDefault: false,
      isMatch: true,
      controller: function(artifact: IArtifact) {
        this.artifact = artifact;
        this.artifact.type = 'http/file';
        if (this.artifact.name && !this.artifact.reference) {
          this.artifact.reference = this.artifact.name;
        }
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
             placeholder="http://host/file.ext"
             class="form-control input-sm"
             ng-model="ctrl.artifact.reference" />
    </div>
  </div>
</div>
`,
    });
  })
  .controller('httpArtifactCtrl', HttpArtifactController);
