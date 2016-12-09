import {module} from 'angular';

import './cloudProviderLogo.less';

class CloudProviderLogoController implements ng.IComponentController {
  public provider: string;
  public state: string;
  public height: string;
  public width: string;
  public showTooltip: boolean;
  public tooltip: string;
  public elem: JQuery;

  static get $inject() { return ['cloudProviderRegistry']; }

  public constructor(private cloudProviderRegistry: any) {}

  public $onInit(): void {
    if (this.showTooltip) {
      this.tooltip = this.cloudProviderRegistry.getValue(this.provider, 'name') || this.provider;
    }
  }
}

class CloudProviderLogoComponent implements ng.IComponentOptions {
  public bindings: any = {
    provider: '<',
    state: '@',
    height: '@',
    width: '@',
    showTooltip: '<',
  };
  public controller: any = CloudProviderLogoController;
  public template: string = `<span class="icon icon-{{$ctrl.provider}} icon-{{$ctrl.state}}" 
                                   style="height: {{$ctrl.height}}; width: {{$ctrl.width}}" 
                                   uib-tooltip="{{$ctrl.tooltip}}"></span>`;
}

export const CLOUD_PROVIDER_LOGO = 'spinnaker.core.cloudProviderLogo.directive';

module(CLOUD_PROVIDER_LOGO, [
  require('./cloudProvider.registry'),
]).component('cloudProviderLogo', new CloudProviderLogoComponent());
