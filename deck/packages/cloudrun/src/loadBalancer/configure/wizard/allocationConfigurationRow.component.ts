import type { IComponentOptions, IController } from 'angular';
import { module } from 'angular';
import { uniq } from 'lodash';

import type { ICloudrunAllocationDescription } from '../../loadBalancerTransformer';

class CloudrunAllocationConfigurationRowCtrl implements IController {
  public allocationDescription: ICloudrunAllocationDescription;
  public serverGroupOptions: string[];

  public getServerGroupOptions(): string[] {
    if (this.allocationDescription.revisionName) {
      return uniq(this.serverGroupOptions.concat(this.allocationDescription.revisionName));
    } else {
      return this.serverGroupOptions;
    }
  }
}

const cloudrunAllocationConfigurationRowComponent: IComponentOptions = {
  bindings: {
    allocationDescription: '<',
    removeAllocation: '&',
    serverGroupOptions: '<',
    onAllocationChange: '&',
  },
  template: `
    <div class="form-group">
      <div class="row">
        <div class="col-md-7">
          <ui-select ng-model="$ctrl.allocationDescription.revisionName"
                     on-select="$ctrl.onAllocationChange()"
                     class="form-control input-sm">
            <ui-select-match placeholder="Select...">
              {{$select.selected}}
            </ui-select-match>
            <ui-select-choices repeat="serverGroup as serverGroup in $ctrl.getServerGroupOptions() | filter: $select.search">
              <div ng-bind-html="serverGroup | highlight: $select.search"></div>
            </ui-select-choices>
          </ui-select>
        </div>
        <div class="col-md-3">
          <div class="input-group input-group-sm">
            <input type="number"
                   ng-model="$ctrl.allocationDescription.percent"
                   required
                   class="form-control input-sm"
                   min="0"
                   ng-change="$ctrl.onAllocationChange()"
                   max="100"/>
            <span class="input-group-addon">%</span>
          </div>
        </div>
        <div class="col-md-2">
          <a class="btn btn-link sm-label" ng-click="$ctrl.removeAllocation()">
            <span class="glyphicon glyphicon-trash"></span>
          </a>
        </div>
      </div>
    </div>
  `,
  controller: CloudrunAllocationConfigurationRowCtrl,
};

export const CLOUDRUN_ALLOCATION_CONFIGURATION_ROW = 'spinnaker.cloudrun.allocationConfigurationRow.component';

module(CLOUDRUN_ALLOCATION_CONFIGURATION_ROW, []).component(
  'cloudrunAllocationConfigurationRow',
  cloudrunAllocationConfigurationRowComponent,
);
