import {PAGER_DUTY_READ_SERVICE, PagerDutyReader, IPagerDutyService} from './pagerDuty.read.service';
import {module, IComponentController, IComponentOptions} from 'angular';

export class PagerDutyTagComponentController implements IComponentController {

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
  `
};

export const PAGER_DUTY_TAG_COMPONENT = 'spinnaker.netflix.pagerDuty.pagerDutyTag.component';
module(PAGER_DUTY_TAG_COMPONENT, [PAGER_DUTY_READ_SERVICE]).component('pagerDutyTag', pagerDutyTagComponent);
