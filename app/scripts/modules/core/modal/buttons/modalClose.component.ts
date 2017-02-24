import {module} from 'angular';

class ModalCloseComponent implements ng.IComponentOptions {
  public bindings: any = {
    dismiss: '&'
  };
  public template = `
    <div class="close-button pull-right">
      <a href class="btn btn-link" ng-click="$ctrl.dismiss()" >
        <span class="glyphicon glyphicon-remove"></span>
      </a>
    </div>
`;
}

export const MODAL_CLOSE_COMPONENT = 'spinnaker.core.modal.modalClose.directive';
module(MODAL_CLOSE_COMPONENT, []).component('modalClose', new ModalCloseComponent());
