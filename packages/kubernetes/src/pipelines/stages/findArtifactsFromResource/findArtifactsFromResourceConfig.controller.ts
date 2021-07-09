import { IController, IScope } from 'angular';

import { Application } from '@spinnaker/core';

import { IManifestSelector } from '../../../manifest/selector/IManifestSelector';

export class KubernetesV2FindArtifactsFromResourceConfigCtrl implements IController {
  public application: Application;

  public static $inject = ['$scope'];
  constructor(private $scope: IScope) {
    this.application = this.$scope.$parent.application;
    if (this.$scope.stage.isNew) {
      const defaultSelection: IManifestSelector = {
        location: '',
        account: '',
        manifestName: '',
        app: this.application.name,
      };
      Object.assign(this.$scope.stage, defaultSelection);
      this.$scope.stage.cloudProvider = 'kubernetes';
    }
  }

  public handleManifestSelectorChange = (): void => {
    this.$scope.$applyAsync();
  };
}
