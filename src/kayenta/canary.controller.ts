import { IComponentController, module } from 'angular';

import { Application } from '@spinnaker/core';

class CanaryController implements IComponentController {
  constructor(public app: Application) {
    'ngInject';
  }
}

export const CANARY_CONTROLLER = 'spinnaker.kayenta.canary.controller';
module(CANARY_CONTROLLER, [])
  .controller('CanaryCtrl', CanaryController);
