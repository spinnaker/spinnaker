import { IController, IScope } from 'angular';

import { IManifestSelector } from 'kubernetes/v2/manifest/selector/IManifestSelector';

export class KubernetesV2ScaleManifestConfigCtrl implements IController {
  constructor(private $scope: IScope) {
    'ngInject';
    if (this.$scope.stage.isNew) {
      const defaultSelection: IManifestSelector = {
        location: '',
        account: '',
      };
      Object.assign(this.$scope.stage, defaultSelection);
      const defaultOptions: any = {
        replicas: null,
      };
      Object.assign(this.$scope.stage, defaultOptions);
      this.$scope.stage.cloudProvider = 'kubernetes';
    }
  }

  public handleManifestSelectorChange = (): void => {
    this.$scope.$applyAsync();
  };
}
