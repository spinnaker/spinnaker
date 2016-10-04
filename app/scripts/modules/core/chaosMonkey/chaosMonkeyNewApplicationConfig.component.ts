import {module} from 'angular';

export class ChaosMonkeyNewApplicationConfigController {

  static get $inject() { return ['settings']; }

  public enabled: boolean = false;
  public applicationConfig: any;

  public constructor(private settings: any) {
    this.enabled = settings.feature && settings.feature.chaosMonkey;
    if (this.enabled) {
      this.applicationConfig.chaosMonkey = {
        enabled: this.enabled
      };
    }
  }
}

class ChaosMonkeyNewApplicationConfigComponent implements ng.IComponentOptions {
  public bindings: any = {
    applicationConfig: '='
  };
  public controller: ng.IComponentController = ChaosMonkeyNewApplicationConfigController;
  public template: string = `
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

const moduleName = 'spinnaker.core.chaosMonkey.newApplication.config.component';

module(moduleName, [
  require('../config/settings')
])
.component('chaosMonkeyNewApplicationConfig', new ChaosMonkeyNewApplicationConfigComponent());

export default moduleName;
