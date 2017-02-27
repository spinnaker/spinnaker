import {module} from 'angular';
import {uniq} from 'lodash';
import {IAppengineAllocationDescription} from 'appengine/loadBalancer/transformer';

class AppengineAllocationConfigurationRowCtrl implements ng.IComponentController {
  public allocationDescription: IAppengineAllocationDescription;
  public serverGroupOptions: string[];

  public getServerGroupOptions(): string[] {
    if (this.allocationDescription.serverGroupName) {
      return uniq(this.serverGroupOptions.concat(this.allocationDescription.serverGroupName));
    } else {
      return this.serverGroupOptions;
    }
  }
}

class AppengineAllocationConfigurationRowComponent implements ng.IComponentOptions {
  public bindings: any = {allocationDescription: '<', removeAllocation: '&', serverGroupOptions: '<', onAllocationChange: '&'};
  public template = `
    <div class="form-group">
      <div class="row">
        <div class="col-md-7">
          <ui-select ng-model="$ctrl.allocationDescription.serverGroupName"
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
                   ng-model="$ctrl.allocationDescription.allocation"
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
  `;
  public controller: any = AppengineAllocationConfigurationRowCtrl;
}

export const APPENGINE_ALLOCATION_CONFIGURATION_ROW = 'spinnaker.appengine.allocationConfigurationRow.component';

module(APPENGINE_ALLOCATION_CONFIGURATION_ROW, [])
  .component('appengineAllocationConfigurationRow', new AppengineAllocationConfigurationRowComponent());
