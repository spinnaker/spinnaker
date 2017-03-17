import {module} from 'angular';
import {map, isObject} from 'lodash';

import './annotationConfigurer.component.less';

interface IAnnotationView {
  key: string;
  value: string;
}

class KubernetesAnnotationConfigurer implements ng.IComponentController {
  public component: any;
  public field: string;
  public label: string;
  public annotationViews: IAnnotationView[];

  public $onInit(): void {
    if (!isObject(this.component[this.field])) {
      this.component[this.field] = {};
      this.annotationViews = [];
    } else {
      this.annotationViews = map(this.component[this.field], (value: string, key: string) => {
        return {key, value};
      });
    }
  }

  public add($event: Event): void {
    if ($event) {
      // Disables Chrome's default form validation.
      $event.preventDefault();
    }
    this.annotationViews.push({key: '', value: ''});
  }

  public remove(index: number): void {
    this.annotationViews.splice(index, 1);
    this.onKeyOrValueChange();
  }

  public onKeyOrValueChange(): void {
    this.component[this.field] = this.annotationViews.reduce((annotationMap: {[key: string]: string}, view: IAnnotationView) => {
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
