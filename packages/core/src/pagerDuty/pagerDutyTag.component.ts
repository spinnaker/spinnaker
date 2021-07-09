import { IComponentController, IComponentOptions, module } from 'angular';

import { IPagerDutyService, PagerDutyReader } from './pagerDuty.read.service';

export class PagerDutyTagComponentController implements IComponentController {
  public apiKey: any;

  public servicesLoaded = false;
  public currentService: IPagerDutyService;

  private setCurrentService(): void {
    this.servicesLoaded = false;
    PagerDutyReader.listServices().subscribe((pagerDutyServices: IPagerDutyService[]) => {
      this.currentService = pagerDutyServices.find((service: IPagerDutyService) => {
        return service.integration_key === this.apiKey;
      });
      this.servicesLoaded = true;
    });
  }

  public $onInit(): void {
    this.setCurrentService();
  }

  public $onChanges(): void {
    this.setCurrentService();
  }
}

const pagerDutyTagComponent: IComponentOptions = {
  bindings: {
    apiKey: '<',
  },
  controller: PagerDutyTagComponentController,
  template: `
    <span>
      <span ng-if="!$ctrl.servicesLoaded">
        <i class="fa fa-asterisk fa-spin fa-fw"></i> Loading...
      </span>
      <span ng-if="$ctrl.servicesLoaded && $ctrl.currentService">
        {{ $ctrl.currentService.name }} ({{ $ctrl.currentService.integration_key }})
      </span>
      <span ng-if="$ctrl.servicesLoaded && !$ctrl.currentService">
        Unable to locate PagerDuty key ({{ $ctrl.apiKey }})
      </span>
    </span>
  `,
};

export const PAGER_DUTY_TAG_COMPONENT = 'spinnaker.core.pagerDuty.pagerDutyTag.component';
module(PAGER_DUTY_TAG_COMPONENT, []).component('pagerDutyTag', pagerDutyTagComponent);
