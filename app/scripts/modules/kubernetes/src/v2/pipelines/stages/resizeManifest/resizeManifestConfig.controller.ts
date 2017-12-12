import { IScope, IController } from 'angular';

import { IManifestSelector } from '../../../manifest/selector/IManifestSelector';

export class KubernetesV2ResizeManifestConfigCtrl implements IController {
  constructor(private $scope: IScope) {
    'ngInject';
    if (this.$scope.stage.isNew) {
      const defaultSelection: IManifestSelector = {
        location: '',
        account: '',
        kinds: [],
        labelSelectors: {
          selectors: []
        }
      };
      Object.assign(this.$scope.stage, defaultSelection);
      const defaultOptions: any = {
        replicas: 0
      };
      Object.assign(this.$scope.stage, defaultOptions);
      this.$scope.stage.cloudProvider = 'kubernetes';
    }
  }
}
