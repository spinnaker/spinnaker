import { IComponentOptions, module } from 'angular';

const modalCloseComponent: IComponentOptions = {
  bindings: {
    dismiss: '&',
  },
  template: `
    <div class="close-button pull-right">
      <a href class="btn btn-link" ng-click="$ctrl.dismiss()" >
        <span class="glyphicon glyphicon-remove"></span>
      </a>
    </div>
`,
};

export const MODAL_CLOSE_COMPONENT = 'spinnaker.core.modal.modalClose.component';
module(MODAL_CLOSE_COMPONENT, []).component('modalClose', modalCloseComponent);
