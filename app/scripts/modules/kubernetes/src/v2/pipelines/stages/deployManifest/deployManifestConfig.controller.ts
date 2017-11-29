import { IScope, IController } from 'angular';
import { load } from 'js-yaml';

import {
  IKubernetesManifestCommandMetadata,
  KubernetesManifestCommandBuilder
} from '../../../manifest/manifestCommandBuilder.service';

export class KubernetesV2DeployManifestConfigCtrl implements IController {
  public state = {
    loaded: false,
  };

  public metadata: IKubernetesManifestCommandMetadata;

  constructor(private $scope: IScope,
              private kubernetesManifestCommandBuilder: KubernetesManifestCommandBuilder) {
    'ngInject';
    this.kubernetesManifestCommandBuilder.buildNewManifestCommand(this.$scope.application, this.$scope.stage.manifest, this.$scope.stage.moniker)
      .then((builtCommand) => {
        if (this.$scope.stage.isNew) {
          Object.assign(this.$scope.stage, builtCommand.command);
        }

        this.metadata = builtCommand.metadata;
        this.state.loaded = true;
      });
  }

  public change() {
    try {
      this.$scope.stage.manifest = load(this.metadata.manifestText);
    } catch (e) {}
  }
}
