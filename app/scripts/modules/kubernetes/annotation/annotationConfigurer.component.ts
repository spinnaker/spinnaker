import {module} from 'angular';
import * as _ from 'lodash';

require('./annotationConfigurer.component.less');

class AnnotationView {
  constructor (public key = '', public value = '') { }
}

class KubernetesAnnotationConfigurer implements ng.IComponentController {
  public component: any;
  public field: string;
  public label: string;
  public annotationViews: AnnotationView[];

  public $onInit (): void {
    if (!_.isObject(this.component[this.field])) {
      this.component[this.field] = {};
      this.annotationViews = [];
    } else {
      this.annotationViews = _.map(this.component[this.field], (value: string, key: string) => {
        return new AnnotationView(key, value);
      });
    }
  }

  public add (): void {
    this.annotationViews.push(new AnnotationView());
  }

  public remove (index: number): void {
    this.annotationViews.splice(index, 1);
  }

  public onKeyOrValueChange (): void {
    this.component[this.field] = this.annotationViews.reduce((annotationMap: any, view: AnnotationView) => {
      annotationMap[view.key] = view.value;
      return annotationMap;
    }, {});
  }
}

class KubernetesAnnotationConfigurerComponent implements ng.IComponentOptions {
  public bindings: any = {
    component: '<',
    field: '@',
    label: '@'
  };
  public templateUrl: string = require('./annotationConfigurer.component.html');
  public controller: any = KubernetesAnnotationConfigurer;
}

export const KUBERNETES_ANNOTATION_CONFIGURER = 'spinnaker.kubernetes.annotation.configurer.component';

module(KUBERNETES_ANNOTATION_CONFIGURER, [])
  .component('kubernetesAnnotationConfigurer', new KubernetesAnnotationConfigurerComponent());
