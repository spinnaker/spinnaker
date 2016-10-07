import {module} from 'angular';
import wizardPageModule from './v2wizardPage.component';
import modalWizardServiceModule from './v2modalWizard.service';

import './modalWizard.less';

export class V2ModalWizard implements ng.IComponentController {

  public wizard: any;
  public notReallyModal: boolean;
  public heading: string;
  public taskMonitor: any;
  public dismiss: () => any;

  public constructor(private $scope: ng.IScope, private v2modalWizardService: any) {
    this.wizard = v2modalWizardService;
  }

  public $onInit() {
    this.$scope.$on('waypoints-changed', (event: any, snapshot: any) => {
      let ids = snapshot.lastWindow
        .map((entry: any) => entry.elem)
        .filter((key: string) => this.wizard.getPage(key));
      ids.reverse().forEach((id: string) => {
        this.wizard.setCurrentPage(this.wizard.getPage(id), true);
      });
    });

    if (this.notReallyModal) {
      this.wizard.setPageOffset(0);
    }

    this.wizard.setHeading(this.heading);
  }

  public $onDestroy() {
    this.wizard.resetWizard();
  }
}

class V2ModalWizardComponent implements ng.IComponentOptions {
  public bindings: any = {
    heading: '@',
    notReallyModal: '<',
    taskMonitor: '<',
    modalInstance: '<',
    dismiss: '&',
  };

  public transclude: boolean = true;
  public templateUrl: string = require('./v2modalWizard.component.html');
  public controller: ng.IComponentController = V2ModalWizard;
}

const moduleName = 'spinnaker.core.modal.wizard.wizard.component';

module(moduleName, [
  wizardPageModule,
  modalWizardServiceModule,
]).component('v2ModalWizard', new V2ModalWizardComponent());

export default moduleName;
