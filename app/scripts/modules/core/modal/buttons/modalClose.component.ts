import {module, noop} from 'angular';

class ModalCloseComponent implements ng.IComponentOptions {
  public bindings: any = {
    dismiss: '&'
  };
  public controller: ng.IComponentController = noop;
  public template: string = `
    <div class="close-button pull-right">
      <a href class="btn btn-link" ng-click="$ctrl.dismiss()" >
        <span class="glyphicon glyphicon-remove"></span>
      </a>
    </div>
`;
}

const moduleName = 'spinnaker.core.modal.modalClose.directive';

module(moduleName, []).component('modalClose', new ModalCloseComponent());

export default moduleName;
