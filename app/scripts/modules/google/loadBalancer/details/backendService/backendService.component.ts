import {module} from 'angular';

class GceBackendServiceDetailsComponent implements ng.IComponentOptions {
  bindings: any = {
    backendService: '<'
  };
  template: string = `
    <dt>Name</dt>
    <dd>{{$ctrl.backendService.name}}</dd>
    <dt>Health Check</dt>
    <dd>{{$ctrl.backendService.healthCheck.name}}</dd>
    <dt ng-if="$ctrl.backendService.sessionAffinity">Session Affinity</dt>
    <dd ng-if="$ctrl.backendService.sessionAffinity">{{$ctrl.backendService.sessionAffinity}}</dd>
    <dt ng-if="$ctrl.backendService.sessionAffinity === 'GENERATED_COOKIE'">Affinity Cookie TTL</dt>
    <dd ng-if="$ctrl.backendService.sessionAffinity === 'GENERATED_COOKIE'">{{$ctrl.backendService.affinityCookieTtlSec}}</dd>`;
}

const gceBackendServiceDetailsComponent = 'spinnaker.gce.loadBalancer.details.backendServiceDetails.component';

module(gceBackendServiceDetailsComponent, [])
  .component('gceBackendServiceDetails', new GceBackendServiceDetailsComponent());

export default gceBackendServiceDetailsComponent;
