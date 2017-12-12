import { IScope, IController } from 'angular';

import { IManifestSelector } from '../../../manifest/selector/IManifestSelector';
import { IDeleteOptions } from '../../../manifest/delete/delete.controller';

export class KubernetesV2DeleteManifestConfigCtrl implements IController {
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
      const defaultOptions: IDeleteOptions = {
        gracePeriodSeconds: 0,
        cascading: true,
      }
      this.$scope.stage.options = defaultOptions;
      this.$scope.stage.cloudProvider = 'kubernetes';
    }
  }
}
