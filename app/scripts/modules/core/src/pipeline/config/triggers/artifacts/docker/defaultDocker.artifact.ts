import { module } from 'angular';
import { isNil } from 'lodash';

import { IArtifact } from 'core/domain/IArtifact';
import { Registry } from 'core/registry';

export const setNameAndVersionFromReference = (artifact: IArtifact) => {
  const ref = artifact.reference;
  if (isNil(ref)) {
    return;
  }

  const atIndex: number = ref.indexOf('@');
  const lastColonIndex: number = ref.lastIndexOf(':');

  if (atIndex >= 0) {
    const split = ref.split('@');
    artifact.name = split[0];
    artifact.version = split[1];
  } else if (lastColonIndex > 0) {
    artifact.name = ref.substring(0, lastColonIndex);
    artifact.version = ref.substring(lastColonIndex + 1);
  } else {
    artifact.name = ref;
  }
};

export const DEFAULT_DOCKER_ARTIFACT = 'spinnaker.core.pipeline.trigger.artifact.defaultDocker';
module(DEFAULT_DOCKER_ARTIFACT, []).config(() => {
  Registry.pipeline.registerArtifactKind({
    label: 'Docker',
    type: 'docker/image',
    isDefault: true,
    isMatch: false,
    isPubliclyAccessible: true,
    description: 'A Docker image to be deployed.',
    key: 'default.docker',
    controller: function(artifact: IArtifact) {
      'ngInject';
      this.artifact = artifact;
      this.artifact.type = 'docker/image';

      this.onReferenceChange = () => {
        setNameAndVersionFromReference(this.artifact);
      };
    },
    controllerAs: 'ctrl',
    template: `
<div class="col-md-12">
  <div class="form-group row">
    <label class="col-md-2 sm-label-right">
      Docker image
      <help-field key="pipeline.config.expectedArtifact.defaultDocker.reference"></help-field>
    </label>
    <div class="col-md-8">
      <input type="text"
             placeholder="gcr.io/project/image@sha256:9efcc2818c9..."
             class="form-control input-sm"
             ng-change="ctrl.onReferenceChange()"
             ng-model="ctrl.artifact.reference"/>
    </div>
  </div>
</div>
`,
  });
});
