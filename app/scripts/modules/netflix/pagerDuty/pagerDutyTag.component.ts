import {Component, Input, OnChanges, OnInit, SimpleChanges} from '@angular/core';

import {IDowngradeItem} from 'core/domain/IDowngradeItem';
import {API_SERVICE_PROVIDER} from 'core/api/api.service';
import {IPagerDutyService, PagerDutyReader} from './pagerDuty.read.service';

@Component({
  selector: 'deck-pager-duty-tag',
  providers: [API_SERVICE_PROVIDER, PagerDutyReader],
  template: `
    <span>
      <span *ngIf="!servicesLoaded">
        <i class="fa fa-asterisk fa-spin fa-fw"></i> Loading...
      </span>
      <span *ngIf="servicesLoaded  && currentService">
        {{ currentService.name }} ({{ currentService.integration_key }})
      </span>
      <span *ngIf="servicesLoaded && !currentService">
        Unable to locate PagerDuty key ({{ apiKey }})
      </span>
    </span>
  `
})
export class PagerDutyTagComponent implements OnInit, OnChanges {

  @Input()
  public apiKey: any;

  public servicesLoaded = false;
  public currentService: IPagerDutyService;

  constructor(private pagerDutyReader: PagerDutyReader) {}

  private setCurrentService(): void {
    this.servicesLoaded = false;
    this.pagerDutyReader.listServices().subscribe((pagerDutyServices: IPagerDutyService[]) => {
      this.currentService = pagerDutyServices.find((service: IPagerDutyService) => {
        return service.integration_key === this.apiKey;
      });
      this.servicesLoaded = true;
    });
  }

  public ngOnInit(): void {
    this.setCurrentService();
  }

  public ngOnChanges(changes: SimpleChanges): void {
    this.apiKey = changes.apiKey.currentValue;
    this.setCurrentService();
  }
}

export const PAGER_DUTY_TAG_COMPONENT = 'spinnaker.netflix.pagerDuty.pagerDutyTag.component';
export const PAGER_DUTY_TAG_COMPONENT_DOWNGRADE: IDowngradeItem = {
  moduleName: PAGER_DUTY_TAG_COMPONENT,
  injectionName: 'pagerDutyTag',
  moduleClass: PagerDutyTagComponent,
  inputs: ['apiKey']
};
