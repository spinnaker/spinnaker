import { IComponentOptions, module } from 'angular';

const footerComponent: IComponentOptions = {
  bindings: {
    action: '&',
    isValid: '&',
    cancel: '&',
    account: '=?',
    verification: '=?',
  },
  template: `
    <div class="modal-footer">
      <user-verification account="$ctrl.account" verification="$ctrl.verification"></user-verification>
      <button type="submit" ng-click="$ctrl.action()" style="display:none"></button> <!-- Allows form submission via enter keypress-->
      <button class="btn btn-default" ng-click="$ctrl.cancel()">Cancel</button>
      <button type="submit"
              class="btn btn-primary"
              ng-click="$ctrl.action()"
              ng-disabled="!$ctrl.isValid()">
        Submit
      </button>
    </div>
  `,
  controller: () => {},
};

export const AWS_FOOTER_COMPONENT = 'spinnaker.amazon.footer';
module(AWS_FOOTER_COMPONENT, []).component('awsFooter', footerComponent);
