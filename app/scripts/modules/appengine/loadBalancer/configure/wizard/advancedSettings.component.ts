import {module} from 'angular';

class AppEngineLoadBalancerAdvancedSettingsComponent implements ng.IComponentOptions {
  public bindings: any = {loadBalancer: '='};
  public template: string = `
    <ng-form name="advancedSettingsForm">
      <div class="row">
        <div class="form-group">
          <div class="col-md-3 sm-label-right">
            Migrate Traffic <help-field key="appengine.loadBalancer.migrateTraffic"></help-field>
          </div>
          <div class="col-md-9">
            <div class="checkbox">
              <input type="checkbox" ng-model="$ctrl.loadBalancer.migrateTraffic">
            </div>
          </div>
        </div>
      </div>
    </ng-form>
  `;
}

export const APPENGINE_LOAD_BALANCER_ADVANCED_SETTINGS = 'spinnaker.appengine.loadBalancer.advancedSettings.component';

module(APPENGINE_LOAD_BALANCER_ADVANCED_SETTINGS, [])
  .component('appengineLoadBalancerAdvancedSettings', new AppEngineLoadBalancerAdvancedSettingsComponent());
