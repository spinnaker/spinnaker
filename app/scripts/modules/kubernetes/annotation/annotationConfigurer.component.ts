import {module} from 'angular';
import * as _ from 'lodash';

require('./annotationConfigurer.component.less');

class AnnotationView {
  constructor (public key = '', public value = '') { }
}

class KubernetesAnnotationConfigurer implements ng.IComponentController {
  component: any;
  field: string;
  label: string;
  annotationViews: AnnotationView[];

  $onInit (): void {
    if (!_.isObject(this.component[this.field])) {
      this.component[this.field] = {};
      this.annotationViews = [];
    } else {
      this.annotationViews = _.map(this.component[this.field], (value: string, key: string) => {
        return new AnnotationView(key, value);
      });
    }
  }

  add (): void {
    this.annotationViews.push(new AnnotationView());
  }

  remove (index: number): void {
    this.annotationViews.splice(index, 1);
  }

  onKeyOrValueChange (): void {
    this.component[this.field] = this.annotationViews.reduce((annotationMap: any, view: AnnotationView) => {
      annotationMap[view.key] = view.value;
      return annotationMap;
    }, {});
  }
}

class KubernetesAnnotationConfigurerComponent implements ng.IComponentOptions {
  bindings: any = {
    component: '<',
    field: '@',
    label: '@'
  };
  templateUrl: string = require('./annotationConfigurer.component.html');
  controller: any = KubernetesAnnotationConfigurer;
}

export const KUBERNETES_ANNOTATION_CONFIGURER = 'spinnaker.kubernetes.annotation.configurer.component';

module(KUBERNETES_ANNOTATION_CONFIGURER, [])
  .component('kubernetesAnnotationConfigurer', new KubernetesAnnotationConfigurerComponent());
