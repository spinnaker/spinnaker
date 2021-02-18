import { IController, module } from 'angular';

import { ModalWizard } from './ModalWizard';
import { V2_WIZARD_PAGE_COMPONENT } from './v2wizardPage.component';

import './modalWizard.less';

export class V2ModalWizard implements IController {
  public wizard: any;
  public heading: string;
  public taskMonitor: any;
  public dismiss: () => any;

  public static $inject = ['$scope'];
  public constructor(private $scope: ng.IScope) {
    this.wizard = ModalWizard;
  }

  public $onInit() {
    this.$scope.$on('waypoints-changed', (_event: any, snapshot: any) => {
      const ids = snapshot.lastWindow.map((entry: any) => entry.elem).filter((key: string) => ModalWizard.getPage(key));
      ids.reverse().forEach((id: string) => {
        ModalWizard.setCurrentPage(ModalWizard.getPage(id), true);
      });
    });

    ModalWizard.setHeading(this.heading);
  }

  public $onDestroy() {
    ModalWizard.resetWizard();
  }
}

const v2ModalWizardComponent: ng.IComponentOptions = {
  bindings: {
    heading: '@',
    taskMonitor: '<',
    dismiss: '&',
  },
  transclude: true,
  templateUrl: require('./v2modalWizard.component.html'),
  controller: V2ModalWizard,
};

export const V2_MODAL_WIZARD_COMPONENT = 'spinnaker.core.modal.wizard.wizard.component';
module(V2_MODAL_WIZARD_COMPONENT, [V2_WIZARD_PAGE_COMPONENT]).component('v2ModalWizard', v2ModalWizardComponent);
