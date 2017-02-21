import {module} from 'angular';
import {PAGER_DUTY_READ_SERVICE, IPagerDutyService} from './pagerDuty.read.service';

export class PagerDutySelectFieldController implements ng.IComponentController {
  public pagerDutyServices: [IPagerDutyService];
  public servicesLoaded: boolean;
  public helpContents = `<p>Make sure your service exists in Pager Duty and includes the "Generic API" 
      integration (from your service in Pager Duty, click "New Integration", then select "Use our API directly").`;

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
  public controller: any = PagerDutySelectFieldController;
  public template = `
  <div class="form-group row">
    <div class="col-sm-3 sm-label-right">PagerDuty * <help-field content="{{$ctrl.helpContents}}"></help-field></div>
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

export const PAGER_DUTY_SELECT_FIELD_COMPONENT = 'spinnaker.netflix.pagerDuty.pagerDutySelectField.component';
module(PAGER_DUTY_SELECT_FIELD_COMPONENT, [PAGER_DUTY_READ_SERVICE])
  .component('pagerDutySelectField', new PagerDutySelectFieldComponent());
