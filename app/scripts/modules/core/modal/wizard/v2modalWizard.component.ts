import {module} from 'angular';
import {V2_WIZARD_PAGE_COMPONENT} from './v2wizardPage.component';
import {V2_MODAL_WIZARD_SERVICE} from './v2modalWizard.service';

import './modalWizard.less';

export class V2ModalWizard implements ng.IComponentController {

  public wizard: any;
  public heading: string;
  public taskMonitor: any;
  public dismiss: () => any;

  public constructor(private $scope: ng.IScope, v2modalWizardService: any) {
    this.wizard = v2modalWizardService;
  }

  public $onInit() {
    this.$scope.$on('waypoints-changed', (_event: any, snapshot: any) => {
      const ids = snapshot.lastWindow
        .map((entry: any) => entry.elem)
        .filter((key: string) => this.wizard.getPage(key));
      ids.reverse().forEach((id: string) => {
        this.wizard.setCurrentPage(this.wizard.getPage(id), true);
      });
    });

    this.wizard.setHeading(this.heading);
  }

  public $onDestroy() {
    this.wizard.resetWizard();
  }
}

class V2ModalWizardComponent implements ng.IComponentOptions {
  public bindings: any = {
    heading: '@',
    taskMonitor: '<',
    dismiss: '&',
  };

  public transclude = true;
  public templateUrl: string = require('./v2modalWizard.component.html');
  public controller: any = V2ModalWizard;
}

export const V2_MODAL_WIZARD_COMPONENT = 'spinnaker.core.modal.wizard.wizard.component';
module(V2_MODAL_WIZARD_COMPONENT, [
  V2_WIZARD_PAGE_COMPONENT,
  V2_MODAL_WIZARD_SERVICE,
]).component('v2ModalWizard', new V2ModalWizardComponent());
