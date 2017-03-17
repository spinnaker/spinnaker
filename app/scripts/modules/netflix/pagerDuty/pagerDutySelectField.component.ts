import {Component, Input, OnInit} from '@angular/core';

import {IDowngradeItem} from 'core/domain/IDowngradeItem';
import {API_SERVICE_PROVIDER} from 'core/api/api.service';
import {IPagerDutyService, PagerDutyReader} from './pagerDuty.read.service';

@Component({
  selector: 'deck-pager-duty-select-field',
  providers: [API_SERVICE_PROVIDER, PagerDutyReader],
  template: `
    <div class="form-group row">
      <div class="col-sm-3 sm-label-right">
        PagerDuty *
        <help-field [content]="helpContents"></help-field>
      </div>
      <div class="col-sm-9">
        <ui-select-wrapper [items]="pagerDutyServices"
                           [model]="component"
                           modelProperty="pdApiKey"
                           placeholder="Select a PagerDuty Service"
                           renderProperty="name"
                           selectProperty="integration_key"></ui-select-wrapper>
      </div>
    </div>
  `
})
export class PagerDutySelectFieldComponent implements OnInit {

  @Input()
  public component: any;

  public servicesLoaded = false;
  public pagerDutyServices: IPagerDutyService[];
  public helpContents = `<p>Make sure your service exists in Pager Duty and includes the "Generic API"
    integration (from your service in Pager Duty, click "New Integration", then select "Use our API directly").</p>`;

  constructor(private pagerDutyReader: PagerDutyReader) {}

  public ngOnInit(): void {
    this.pagerDutyReader.listServices().subscribe((services: IPagerDutyService[]) => {
      this.pagerDutyServices = services;
      this.servicesLoaded = true;
    });
  }
}

export const PAGER_DUTY_SELECT_FIELD_COMPONENT = 'spinnaker.netflix.pagerDuty.pagerDutySelectField.component';
export const PAGER_DUTY_SELECT_FIELD_COMPONENT_DOWNGRADE: IDowngradeItem = {
  moduleName: PAGER_DUTY_SELECT_FIELD_COMPONENT,
  injectionName: 'pagerDutySelectField',
  moduleClass: PagerDutySelectFieldComponent,
  inputs: ['component']
};
