import { IController, IScope } from 'angular';

import { IManifestSelector } from 'kubernetes/v2/manifest/selector/IManifestSelector';
import { Application } from '@spinnaker/core';

export class KubernetesV2ScaleManifestConfigCtrl implements IController {
  public application: Application;

  constructor(private $scope: IScope) {
    'ngInject';
    if (this.$scope.stage.isNew) {
      this.application = this.$scope.$parent.application;
      const defaultSelection: IManifestSelector = {
        location: '',
        account: '',
      };
      Object.assign(this.$scope.stage, defaultSelection);
      const defaultOptions: any = {
        replicas: null,
        app: this.application.name,
      };
      Object.assign(this.$scope.stage, defaultOptions);
      this.$scope.stage.cloudProvider = 'kubernetes';
    }
  }

  public handleManifestSelectorChange = (): void => {
    this.$scope.$applyAsync();
  };
}
