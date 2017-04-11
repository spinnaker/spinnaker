import {module} from 'angular';
import {uniq} from 'lodash';
import {StageConstants} from 'core/pipeline/config/stages/stageConstants';
import {IAppengineAllocationDescription} from 'appengine/loadBalancer/transformer';
import {Application} from 'core/application/application.model';

class AppengineStageAllocationLabelCtrl implements ng.IComponentController {
  public inputViewValue: string;
  private allocationDescription: IAppengineAllocationDescription;

  private static mapTargetCoordinateToLabel(targetCoordinate: string): string {
    const target = StageConstants.TARGET_LIST.find(t => t.val === targetCoordinate);
    if (target) {
      return target.label;
    } else {
      return null;
    }
  }

  public $doCheck(): void {
    this.setInputViewValue();
  }

  private setInputViewValue(): void {
    switch (this.allocationDescription.locatorType) {
      case 'text':
        this.inputViewValue = this.allocationDescription.serverGroupName;
        break;
      case 'fromExisting':
        this.inputViewValue = this.allocationDescription.serverGroupName;
        break;
      case 'targetCoordinate':
        if (this.allocationDescription.cluster && this.allocationDescription.target) {
          const targetLabel = AppengineStageAllocationLabelCtrl.mapTargetCoordinateToLabel(this.allocationDescription.target);
          this.inputViewValue = `${targetLabel} (${this.allocationDescription.cluster})`;
        } else {
          this.inputViewValue = null;
        }
        break;
      default:
        this.inputViewValue = null;
        break;
    }
  }
}

class AppengineStageAllocationLabel implements ng.IComponentOptions {
  public bindings: any = {allocationDescription: '<'};
  public controller: any = AppengineStageAllocationLabelCtrl;
  public template = `<input ng-model="$ctrl.inputViewValue" type="text" class="form-control input-sm" readonly/>`;
}

class AppengineStageAllocationConfigurationRowCtrl implements ng.IComponentController {
  public allocationDescription: IAppengineAllocationDescription;
  public serverGroupOptions: string[];
  public targets = StageConstants.TARGET_LIST;
  public clusterList: string[];
  public onAllocationChange: Function;
  private application: Application;
  private region: string;
  private account: string;

  public static get $inject() { return ['appListExtractorService']; }

  constructor(private appListExtractorService: any) {
    const clusterFilter = this.appListExtractorService.clusterFilterForCredentialsAndRegion(this.account, this.region);
    this.clusterList = this.appListExtractorService.getClusters([this.application], clusterFilter);
  }

  public getServerGroupOptions(): string[] {
    if (this.allocationDescription.serverGroupName) {
      return uniq(this.serverGroupOptions.concat(this.allocationDescription.serverGroupName));
    } else {
      return this.serverGroupOptions;
    }
  }

  public onLocatorTypeChange(): void {
    // Prevents pipeline expressions (or non-existent server groups) from entering the dropdown.
    if (!this.serverGroupOptions.includes(this.allocationDescription.serverGroupName)) {
      delete this.allocationDescription.serverGroupName;
    }
    this.onAllocationChange();
  }
}

class AppengineStageAllocationConfigurationRow implements ng.IComponentOptions {
  public bindings: any = {
    application: '<',
    region: '@',
    account: '@',
    allocationDescription: '<',
    removeAllocation: '&',
    serverGroupOptions: '<',
    onAllocationChange: '&'
  };
  public controller: any = AppengineStageAllocationConfigurationRowCtrl;
  public templateUrl = require('./stageAllocationConfigurationRow.component.html');
}

export const APPENGINE_STAGE_ALLOCATION_CONFIGURATION_ROW = 'spinnaker.appengine.stageAllocationConfigurationRow.component';
module(APPENGINE_STAGE_ALLOCATION_CONFIGURATION_ROW, [])
  .component('appengineStageAllocationConfigurationRow', new AppengineStageAllocationConfigurationRow())
  .component('appengineStageAllocationLabel', new AppengineStageAllocationLabel());

