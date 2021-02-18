import { IComponentOptions, IController, module } from 'angular';

import { AppengineLoadBalancerUpsertDescription } from '../../transformer';

class AppengineLoadBalancerAdvancedSettingsCtrl implements IController {
  public state = { error: false };
  public loadBalancer: AppengineLoadBalancerUpsertDescription;

  public disableMigrateTraffic(): boolean {
    if (this.loadBalancer.splitDescription.allocationDescriptions.length !== 1) {
      return true;
    } else {
      const targetServerGroupName = this.loadBalancer.splitDescription.allocationDescriptions[0].serverGroupName;
      const targetServerGroup = this.loadBalancer.serverGroups.find(
        (candidate) => candidate.name === targetServerGroupName,
      );

      if (targetServerGroup) {
        return !targetServerGroup.allowsGradualTrafficMigration;
      } else {
        // If the target server group name is not in the load balancer's server groups, then hopefully we are in
        // a pipeline stage - we'll leave it up to the user to make smart decisions.
        return false;
      }
    }
  }
}

const appengineLoadBalancerAdvancedSettingsComponent: IComponentOptions = {
  bindings: { loadBalancer: '=', application: '<' },
  template: `
    <ng-form name="advancedSettingsForm">
      <div class="row">
        <div class="form-group">
          <div class="col-md-3 sm-label-right">
            Migrate Traffic <help-field key="appengine.loadBalancer.migrateTraffic"></help-field>
          </div>
          <div class="col-md-9">
            <div class="checkbox">
              <input type="checkbox" ng-disabled="$ctrl.disableMigrateTraffic() && !($ctrl.loadBalancer.migrateTraffic = false)" ng-model="$ctrl.loadBalancer.migrateTraffic">
            </div>
          </div>
        </div>
      </div>
    </ng-form>
  `,
  controller: AppengineLoadBalancerAdvancedSettingsCtrl,
};

export const APPENGINE_LOAD_BALANCER_ADVANCED_SETTINGS = 'spinnaker.appengine.loadBalancer.advancedSettings.component';

module(APPENGINE_LOAD_BALANCER_ADVANCED_SETTINGS, []).component(
  'appengineLoadBalancerAdvancedSettings',
  appengineLoadBalancerAdvancedSettingsComponent,
);
