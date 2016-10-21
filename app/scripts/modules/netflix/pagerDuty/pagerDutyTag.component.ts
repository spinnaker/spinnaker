import {module} from 'angular';
import PagerDutyReaderModule, {IPagerDutyService} from './pagerDuty.read.service';

export class PagerDutyTagController implements ng.IComponentController {
  public currentService: IPagerDutyService;
  public apiKey: any;
  static get $inject() { return ['pagerDutyReader']; }

  public constructor(private pagerDutyReader: any) {}

  public $onInit() {
    this.pagerDutyReader.listServices().then((pagerDutyServices: [IPagerDutyService]) => {
      this.currentService = pagerDutyServices.find( (x) => {
        return x['integration_key'] === this.apiKey;
      });
    });
  }
}

class PagerDutyTagComponent implements ng.IComponentOptions {
  public bindings: any = {
    apiKey: '='
  };
  public controller: ng.IComponentController = PagerDutyTagController;
  public template: string = `
    <span>{{$ctrl.currentService.name}}</span>
    <span>( {{$ctrl.currentService.integration_key}} )</span>
  `;
}

const moduleName = 'spinnaker.netflix.pagerDuty.pagerDutyTag.component';

module(moduleName, [
  PagerDutyReaderModule,
]).component('pagerDutyTag', new PagerDutyTagComponent());

export default moduleName;
