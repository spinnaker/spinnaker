import { IController, IScope } from 'angular';

import { IMultiManifestSelector } from 'kubernetes/v2/manifest/selector/IManifestSelector';
import { IDeleteOptions } from 'kubernetes/v2/manifest/delete/delete.controller';

export class KubernetesV2DeleteManifestConfigCtrl implements IController {
  constructor(private $scope: IScope) {
    'ngInject';
    if (this.$scope.stage.isNew) {
      const defaultSelection: IMultiManifestSelector = {
        location: '',
        account: '',
        kinds: [],
        labelSelectors: {
          selectors: [],
        },
      };
      Object.assign(this.$scope.stage, defaultSelection);
      const defaultOptions: IDeleteOptions = {
        gracePeriodSeconds: null,
        cascading: true,
      };
      this.$scope.stage.options = defaultOptions;
      this.$scope.stage.cloudProvider = 'kubernetes';
    }
  }
}
