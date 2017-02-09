import {module} from 'angular';
import {uniq} from 'lodash';

class AppengineAllocationConfigurationRowCtrl implements ng.IComponentController {
  public allocation: {serverGroupName: string, percent: number};
  public serverGroupOptions: string[];
  public useTextInput: boolean;
  public onAllocationChange: Function;
  private initializeAsTextInput: boolean;

  public $onInit(): void {
    if (this.initializeAsTextInput) {
      this.useTextInput = true;
    }

    // An allocation percent has at most one decimal place.
    this.allocation.percent = Math.round(this.allocation.percent * 10) / 10;
  }

  public getServerGroupOptions(): string[] {
    if (this.allocation.serverGroupName) {
      return uniq(this.serverGroupOptions.concat(this.allocation.serverGroupName));
    } else {
      return this.serverGroupOptions;
    }
  }

  public setUseTextInput(useTextInput: boolean): void {
    this.useTextInput = useTextInput;
    // Prevents pipeline expressions (or non-existent server groups) from entering the dropdown.
    if (!this.serverGroupOptions.includes(this.allocation.serverGroupName)) {
      delete this.allocation.serverGroupName;
    }
    this.onAllocationChange();
  }
}

class AppengineAllocationConfigurationRowComponent implements ng.IComponentOptions {
  public bindings: any = {allocation: '<', removeAllocation: '&', serverGroupOptions: '<', onAllocationChange: '&', allowTextInput: '<', initializeAsTextInput: '<'};
  public template = `
    <div class="form-group">
      <div class="row">
        <div class="col-md-7">
          <ui-select ng-model="$ctrl.allocation.serverGroupName"
                     ng-if="!$ctrl.useTextInput"
                     on-select="$ctrl.onAllocationChange()"
                     class="form-control input-sm">
            <ui-select-match placeholder="Select...">
              {{$select.selected}}
            </ui-select-match>
            <ui-select-choices repeat="serverGroup as serverGroup in $ctrl.getServerGroupOptions() | filter: $select.search">
              <div ng-bind-html="serverGroup | highlight: $select.search"></div>
            </ui-select-choices>
          </ui-select>
          
          <!-- TODO(dpeach): remove "no-spel" class after figuring out a way to allow the spel expression helper without the big "Expression Docs" link-->
          <input class="form-control input-sm no-spel" ng-change="$ctrl.onAllocationChange()" required formnovalidate
                 type="text" ng-if="$ctrl.useTextInput" ng-model="$ctrl.allocation.serverGroupName"/>
          <span ng-if="$ctrl.allowTextInput && !$ctrl.useTextInput" ng-click="$ctrl.setUseTextInput(true)" class="pull-right small">
            <a href>Click for text input</a>
          </span>
          <span ng-if="$ctrl.allowTextInput && $ctrl.useTextInput" ng-click="$ctrl.setUseTextInput(false)" class="pull-right small">
            <a href>Click for list of existing server groups</a>
          </span>
        </div>
        <div class="col-md-3">
          <div class="input-group input-group-sm">
            <input type="number"
                   ng-model="$ctrl.allocation.percent"
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
