import { module } from 'angular';

import { SETTINGS } from '../config/settings';

export class ChaosMonkeyNewApplicationConfigController {
  public enabled = false;
  public applicationConfig: any;

  public $onInit() {
    this.enabled = SETTINGS.feature.chaosMonkey;
    if (this.enabled) {
      this.applicationConfig.chaosMonkey = {
        enabled: SETTINGS.newApplicationDefaults?.chaosMonkey,
        meanTimeBetweenKillsInWorkDays: 2,
        minTimeBetweenKillsInWorkDays: 1,
        grouping: 'cluster',
        regionsAreIndependent: true,
        exceptions: [],
      };
    }
  }
}

const chaosMonkeyNewApplicationConfigComponent: ng.IComponentOptions = {
  bindings: {
    applicationConfig: '<',
  },
  controller: ChaosMonkeyNewApplicationConfigController,
  template: `
    <div class="form-group row" ng-if="$ctrl.enabled">
      <div class="col-sm-3 sm-label-right">
        Chaos Monkey
        <help-field key="application.chaos.enabled"></help-field>
      </div>
      <div class="col-sm-9" style="margin-bottom: 0">
        <div class="checkbox" style="margin-top: 5px">
          <label>
            <input type="checkbox"
                   ng-model="$ctrl.applicationConfig.chaosMonkey.enabled"/>
            Enabled
          </label>
        </div>
      </div>
    </div>
  `,
};

export const CHAOS_MONKEY_NEW_APPLICATION_CONFIG_COMPONENT =
  'spinnaker.core.chaosMonkey.newApplication.config.component';
module(CHAOS_MONKEY_NEW_APPLICATION_CONFIG_COMPONENT, []).component(
  'chaosMonkeyNewApplicationConfig',
  chaosMonkeyNewApplicationConfigComponent,
);
