import {module} from 'angular';

import {SETTINGS} from 'core/config/settings';

export class ChaosMonkeyNewApplicationConfigController {
  public enabled = false;
  public applicationConfig: any;

  public constructor() {
    this.enabled = SETTINGS.feature.chaosMonkey;
    if (this.enabled) {
      this.applicationConfig.chaosMonkey = {
        enabled: this.enabled,
        meanTimeBetweenKillsInWorkDays: 2,
        minTimeBetweenKillsInWorkDays: 1,
        grouping: 'cluster',
        regionsAreIndependent: true,
        exceptions: []
      };
    }
  }
}

class ChaosMonkeyNewApplicationConfigComponent implements ng.IComponentOptions {
  public bindings: any = {
    applicationConfig: '='
  };
  public controller: any = ChaosMonkeyNewApplicationConfigController;
  public template = `
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
  `;
}

export const CHAOS_MONKEY_NEW_APPLICATION_CONFIG_COMPONENT = 'spinnaker.core.chaosMonkey.newApplication.config.component';
module(CHAOS_MONKEY_NEW_APPLICATION_CONFIG_COMPONENT, [])
.component('chaosMonkeyNewApplicationConfig', new ChaosMonkeyNewApplicationConfigComponent());
