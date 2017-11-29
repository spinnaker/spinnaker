import { IScope, IController } from 'angular';
import { load } from 'js-yaml';

import {
  KubernetesManifestCommandBuilder
} from '../../../manifest/manifestCommandBuilder.service';

export class KubernetesV2DeployManifestConfigCtrl implements IController {

  public state = {
    loaded: false,
  };

  constructor(private $scope: IScope,
              private kubernetesManifestCommandBuilder: KubernetesManifestCommandBuilder) {
    'ngInject';
    this.kubernetesManifestCommandBuilder.buildNewManifestCommand(this.$scope.application)
      .then((builtCommand) => {
        this.$scope.stage.backingData = builtCommand.backingData;
        if (this.$scope.stage.isNew) {
          Object.assign(this.$scope.stage, builtCommand);
        }
        this.$scope.stage.manifest = load(this.$scope.stage.manifestText);
        this.state.loaded = true;
      });
  }

  public change() {
    try {
      this.$scope.stage.manifest = load(this.$scope.stage.manifestText);
    } catch (e) {}
  }
}
