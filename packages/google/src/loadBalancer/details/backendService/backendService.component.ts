import { module } from 'angular';

const gceBackendServiceDetailsComponent: ng.IComponentOptions = {
  bindings: {
    backendService: '<',
  },
  template: `
    <dt>Name</dt>
    <dd>{{$ctrl.backendService.name}}</dd>
    <dt>Health Check</dt>
    <dd>{{$ctrl.backendService.healthCheck.name}}</dd>
    <dt ng-if="$ctrl.backendService.sessionAffinity">Session Affinity</dt>
    <dd ng-if="$ctrl.backendService.sessionAffinity">{{$ctrl.backendService.sessionAffinity | gceSessionAffinityFilter}}</dd>
    <dt ng-if="$ctrl.backendService.sessionAffinity === 'GENERATED_COOKIE'">Affinity Cookie TTL</dt>
    <dd ng-if="$ctrl.backendService.sessionAffinity === 'GENERATED_COOKIE'">{{$ctrl.backendService.affinityCookieTtlSec}}</dd>`,
};

export const GCE_BACKEND_SERVICE_DETAILS_COMPONENT =
  'spinnaker.gce.loadBalancer.details.backendServiceDetails.component';
module(GCE_BACKEND_SERVICE_DETAILS_COMPONENT, []).component(
  'gceBackendServiceDetails',
  gceBackendServiceDetailsComponent,
);
