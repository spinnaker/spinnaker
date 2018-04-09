import { IController, module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER } from 'core/pipeline/config/pipelineConfigProvider';
import { IArtifact } from 'core/domain/IArtifact';
import { PipelineConfigProvider } from 'core/pipeline';

class CustomArtifactController implements IController {
  constructor(public artifact: IArtifact) {
    'ngInject';
  }
}

export const CUSTOM_ARTIFACT = 'spinnaker.core.pipeline.trigger.custom.artifact';
module(CUSTOM_ARTIFACT, [PIPELINE_CONFIG_PROVIDER])
  .config((pipelineConfigProvider: PipelineConfigProvider) => {
    pipelineConfigProvider.registerArtifactKind({
      label: 'Custom',
      description: 'A custom-defined artifact.',
      key: 'custom',
      isDefault: true,
      isMatch: true,
      controller(artifact: IArtifact) {
        'ngInject';
        this.artifact = artifact;
      },
      controllerAs: 'ctrl',
      template: `
<div class="col-md-12">
  <div class="form-group row">
    <label class="col-md-2 sm-label-right">
      Type
    </label>
    <div class="col-md-3">
      <input type="text"
             class="form-control input-sm"
             ng-model="ctrl.artifact.type"/>
    </div>
    <label class="col-md-2 sm-label-right">
      Name
    </label>
    <div class="col-md-3">
      <input type="text"
             class="form-control input-sm"
             ng-model="ctrl.artifact.name"/>
    </div>
  </div>
  <div class="form-group row">
    <label class="col-md-2 sm-label-right">
      Version
    </label>
    <div class="col-md-3">
      <input type="text"
             class="form-control input-sm"
             ng-model="ctrl.artifact.version"/>
    </div>
    <label class="col-md-2 sm-label-right">
      Location
    </label>
    <div class="col-md-3">
      <input type="text"
             class="form-control input-sm"
             ng-model="ctrl.artifact.location"/>
    </div>
  </div>
  <div class="form-group row">
    <label class="col-md-2 sm-label-right">
      Reference
    </label>
    <div class="col-md-8">
      <input type="text"
             class="form-control input-sm"
             ng-model="ctrl.artifact.reference"/>
    </div>
  </div>
</div>
`,
    });
  })
  .controller('customArtifactCtrl', CustomArtifactController);
