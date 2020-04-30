import { IController, IScope } from 'angular';
import { defaults } from 'lodash';

import { Application } from '@spinnaker/core';

import { IManifestSelector } from '../../../manifest/selector/IManifestSelector';

export class KubernetesV2ScaleManifestConfigCtrl implements IController {
  public application: Application;

  public static $inject = ['$scope'];
  constructor(private $scope: IScope) {
    if (this.$scope.stage.isNew) {
      this.application = this.$scope.$parent.application;
      const defaultSelection: IManifestSelector = {
        location: '',
        account: '',
      };
      defaults(this.$scope.stage, defaultSelection);
      const defaultOptions: any = {
        replicas: null,
        app: this.application.name,
      };
      defaults(this.$scope.stage, defaultOptions);
      this.$scope.stage.cloudProvider = 'kubernetes';
    }
  }

  public handleManifestSelectorChange = (): void => {
    this.$scope.$applyAsync();
  };
}
