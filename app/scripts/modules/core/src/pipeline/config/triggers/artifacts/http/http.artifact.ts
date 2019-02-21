import { IController, module } from 'angular';

import { IArtifact } from 'core/domain/IArtifact';
import { Registry } from 'core/registry';
import { HttpArtifactEditor } from './HttpArtifactEditor';

class HttpArtifactController implements IController {
  public static $inject = ['artifact'];
  constructor(public artifact: IArtifact) {}
}

export const HTTP_ARTIFACT = 'spinnaker.core.pipeline.trigger.http.artifact';
module(HTTP_ARTIFACT, [])
  .config(() => {
    Registry.pipeline.registerArtifactKind({
      label: 'HTTP',
      type: 'http/file',
      description: 'An HTTP artifact.',
      key: 'http',
      isDefault: false,
      isMatch: true,
      controller: function(artifact: IArtifact) {
        this.artifact = artifact;
        this.artifact.type = 'http/file';
      },
      controllerAs: 'ctrl',
      editCmp: HttpArtifactEditor,
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
             ng-model="ctrl.artifact.name" />
    </div>
  </div>
</div>
`,
    });
  })
  .controller('httpArtifactCtrl', HttpArtifactController);
