import { IController, IScope } from 'angular';

import { IManifestSelector } from 'kubernetes/v2/manifest/selector/IManifestSelector';

export class KubernetesV2FindArtifactsFromResourceConfigCtrl implements IController {
  constructor(private $scope: IScope) {
    'ngInject';
    if (this.$scope.stage.isNew) {
      const defaultSelection: IManifestSelector = {
        location: '',
        account: '',
        manifestName: '',
      };
      Object.assign(this.$scope.stage, defaultSelection);
      this.$scope.stage.cloudProvider = 'kubernetes';
    }
  }

  public handleManifestSelectorChange = (): void => {
    this.$scope.$applyAsync();
  };
}
