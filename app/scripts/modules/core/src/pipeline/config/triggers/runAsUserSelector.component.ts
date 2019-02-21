import { IComponentOptions, IController, module } from 'angular';
import { SETTINGS } from 'core/config/settings';

class RunAsUserSelectorController implements IController {
  public enabled = true;
  public serviceAccounts: string[];
  public component: string;
  public field: string;

  public $onInit(): void {
    this.enabled = SETTINGS.feature.fiatEnabled && !SETTINGS.feature.managedServiceAccounts;
  }
}

const runAsUserSelectorComponent: IComponentOptions = {
  bindings: {
    serviceAccounts: '<',
    component: '=',
    field: '@',
  },
  template: `
    <div ng-if="$ctrl.enabled">
      <div class="col-md-3 sm-label-right">
        Run As User
        <help-field key="pipeline.config.trigger.runAsUser"></help-field>
      </div>
      <div class="col-md-9">
        <select
          class="form-control input-sm"
          ng-options="svcAcct for svcAcct in $ctrl.serviceAccounts"
          ng-model="$ctrl.component[$ctrl.field]">
          <option value="">Select Run As User</option>
        </select>
      </div>
    </div>
  `,
  controller: RunAsUserSelectorController,
  controllerAs: '$ctrl'
};

export const RUN_AS_USER_SELECTOR_COMPONENT = 'spinnaker.core.runAsUser.selector.component';
module(RUN_AS_USER_SELECTOR_COMPONENT, []).component('runAsUserSelector', runAsUserSelectorComponent);
