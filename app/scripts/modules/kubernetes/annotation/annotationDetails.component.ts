import {module} from 'angular';

class KubernetesAnnotationDetailsComponent implements ng.IComponentOptions {
  bindings: any = {
    annotations: '<'
  };
  template: string = `
    <dt ng-repeat-start="(key, value) in $ctrl.annotations">{{key}}</dt>
    <dd ng-repeat-end>{{value}}</dd>
  `;
}

export const KUBERNETES_ANNOTATION_DETAILS = 'spinnaker.kubernetes.annotation.details.component';

module(KUBERNETES_ANNOTATION_DETAILS, [])
  .component('kubernetesAnnotationDetails', new KubernetesAnnotationDetailsComponent());
