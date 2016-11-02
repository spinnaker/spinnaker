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

const moduleName = 'spinnaker.kubernetes.annotation.details.component';

module(moduleName, [])
  .component('kubernetesAnnotationDetails', new KubernetesAnnotationDetailsComponent());

export default moduleName;
