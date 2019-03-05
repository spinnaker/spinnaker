import { IController, module } from 'angular';

import { ArtifactTypePatterns } from 'core/artifact';
import { IArtifact } from 'core/domain/IArtifact';
import { Registry } from 'core/registry';

class IvyArtifactController implements IController {
  public static $inject = ['artifact'];
  constructor(public artifact: IArtifact) {}
}

export const IVY_ARTIFACT = 'spinnaker.core.pipeline.trigger.ivy.artifact';
module(IVY_ARTIFACT, [])
  .config(() => {
    Registry.pipeline.mergeArtifactKind({
      label: 'Ivy',
      typePattern: ArtifactTypePatterns.IVY_FILE,
      type: 'ivy/file',
      description: 'An Ivy repository artifact.',
      key: 'ivy',
      isDefault: false,
      isMatch: true,
      controller: function(artifact: IArtifact) {
        this.artifact = artifact;
        this.artifact.type = 'ivy/file';
      },
      controllerAs: 'ctrl',
      template: `
<div class="col-md-12">
  <div class="form-group row">
    <label class="col-md-2 sm-label-right">
      Coordinate
    </label>
    <div class="col-md-8">
      <input type="text"
             placeholder="group:artifact:version"
             class="form-control input-sm"
             ng-model="ctrl.artifact.name" />
    </div>
  </div>
</div>
`,
    });
  })
  .controller('ivyArtifactCtrl', IvyArtifactController);
