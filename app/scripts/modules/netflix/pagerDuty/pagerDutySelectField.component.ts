import {module} from 'angular';
import PagerDutyReaderModule, {IPagerDutyService} from './pagerDuty.read.service';

export class PagerDutySelectFieldController implements ng.IComponentController {
  public pagerDutyServices: [IPagerDutyService];
  public servicesLoaded: boolean;

  static get $inject() { return ['pagerDutyReader']; }

  public constructor(private pagerDutyReader: any) {}

  public $onInit() {
    this.pagerDutyReader.listServices().then((pagerDutyServices: [IPagerDutyService]) => {
      this.pagerDutyServices = pagerDutyServices;
      this.servicesLoaded = true;
    });
  }
}

class PagerDutySelectFieldComponent implements ng.IComponentOptions {
  public bindings: any = {
    component: '='
  };
  public controller: ng.IComponentController = PagerDutySelectFieldController;
  public template: string = `
  <div class="form-group row">
    <div class="col-sm-3 sm-label-right">PagerDuty</div>
    <div class="col-sm-9">
      <ui-select ng-if="$ctrl.servicesLoaded" ng-model="$ctrl.component.pdApiKey" class="form-control input-sm" required>
        <ui-select-match placeholder="Select a PagerDuty Service">{{$select.selected.name}}</ui-select-match>
        <ui-select-choices repeat="pagerDuty.integration_key as pagerDuty in $ctrl.pagerDutyServices | filter: $select.search">
          {{pagerDuty.name}}
        </ui-select-choices>
      </ui-select>
    </div>
  </div>
  `;
}

const moduleName = 'spinnaker.netflix.pagerDuty.pagerDutySelectField.component';

module(moduleName, [
  PagerDutyReaderModule,
]).component('pagerDutySelectField', new PagerDutySelectFieldComponent());

export default moduleName;
