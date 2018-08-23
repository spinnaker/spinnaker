import { module } from 'angular';

import { IArtifact } from 'core/domain/IArtifact';
import { Registry } from 'core/registry';
import { Base64ArtifactEditor } from './Base64ArtifactEditor';

import './base64.artifact.less';

export const BASE64_ARTIFACT = 'spinnaker.core.pipeline.trigger.artifact.base64';
module(BASE64_ARTIFACT, []).config(() => {
  Registry.pipeline.registerArtifactKind({
    label: 'Base64',
    type: 'embedded/base64',
    description: 'An artifact that includes its referenced resource as part of its payload.',
    key: 'base64',
    isDefault: false,
    isMatch: true,
    controller: function(artifact: IArtifact) {
      'ngInject';
      this.artifact = artifact;
      this.artifact.type = 'embedded/base64';
    },
    controllerAs: 'ctrl',
    editCmp: Base64ArtifactEditor,
    template: `
<div class="col-md-12">
  <div class="form-group row">
    <label class="col-md-2 sm-label-right">
      Name
    </label>
    <div class="col-md-8">
      <input type="text"
             placeholder="base64-artifact"
             class="form-control input-sm"
             ng-model="ctrl.artifact.name" />
    </div>
  </div>
</div>
`,
  });
});
