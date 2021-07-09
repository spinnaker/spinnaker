import { IComponentController, IComponentOptions, module } from 'angular';

import { SETTINGS } from '../config/settings';
import { IPagerDutyService, PagerDutyReader } from './pagerDuty.read.service';
import { SchedulerFactory } from '../scheduler/SchedulerFactory';

export class PagerDutySelectFieldController implements IComponentController {
  public component: any;
  public pagerDutyServices: IPagerDutyService[];
  public servicesLoaded: boolean;
  private scheduler: any;
  public helpContents = `<p>Make sure your service exists in Pager Duty and includes the "Generic API"
      integration.</p>
      <h5><b>Setting up a new integration</b></h5>
      <ol>
        <li>Find your service in Pager Duty</li>
        <li>Click "New Integration"</li>
        <li>Select "Use our API directly"</li>
        <li>Make sure to select "Events API v1" (Spinnaker is not compatible with v2)</li>
      </ol>
      <p><b>Note:</b> it can take up to five minutes for the service to appear in Spinnaker</p>`;

  public required = (SETTINGS.pagerDuty && SETTINGS.pagerDuty.required) || false;
  public label = `PagerDuty${this.required ? ' *' : ''}`;

  public $onInit() {
    this.scheduler = SchedulerFactory.createScheduler(10000);
    this.scheduler.subscribe(() => this.loadPagerDutyServices());
    this.loadPagerDutyServices();
  }

  public $onDestroy(): void {
    this.scheduler.unsubscribe();
  }

  private loadPagerDutyServices(): void {
    PagerDutyReader.listServices().subscribe((pagerDutyServices: IPagerDutyService[]) => {
      this.pagerDutyServices = pagerDutyServices.filter((service) => service.integration_key);
      this.servicesLoaded = true;
    });
  }
}

const pagerDutySelectField: IComponentOptions = {
  bindings: {
    component: '=',
  },
  controller: PagerDutySelectFieldController,
  template: `
    <div class="form-group row">
      <div class="col-sm-3 sm-label-right">{{$ctrl.label}} <help-field content="{{$ctrl.helpContents}}"></help-field></div>
      <div class="col-sm-9">
        <ui-select ng-if="$ctrl.servicesLoaded" ng-model="$ctrl.component.pdApiKey" class="form-control input-sm" ng-required="$ctrl.required">
          <ui-select-match placeholder="Select a PagerDuty Service">{{$select.selected.name}}</ui-select-match>
          <ui-select-choices repeat="pagerDuty.integration_key as pagerDuty in $ctrl.pagerDutyServices | filter: $select.search">
            {{pagerDuty.name}}
          </ui-select-choices>
        </ui-select>
    </div>
  </div>
`,
};

export const PAGER_DUTY_SELECT_FIELD_COMPONENT = 'spinnaker.core.pagerDuty.pagerDutySelectField.component';
module(PAGER_DUTY_SELECT_FIELD_COMPONENT, []).component('pagerDutySelectField', pagerDutySelectField);
