import { angular2react } from 'angular2react';
import type React from 'react';

import { ReactInject } from '@spinnaker/core';

import type { IAnnotationCustomSectionsProps } from '../manifest/AnnotationCustomSections';
import { kubernetesAnnotationCustomSectionsComponent } from '../manifest/annotationCustomSections.component';

import IInjectorService = angular.auto.IInjectorService;

export class KubernetesReactInject extends ReactInject {
  private $injectorProxy = {} as IInjectorService;

  public KubernetesAnnotationCustomSections: React.ComponentClass<IAnnotationCustomSectionsProps> = angular2react(
    'kubernetesAnnotationCustomSections',
    kubernetesAnnotationCustomSectionsComponent,
    this.$injectorProxy,
  ) as any;

  public initialize($injector: IInjectorService) {
    const realInjector: { [key: string]: Function } = $injector as any;
    const proxyInjector: { [key: string]: Function } = this.$injectorProxy as any;

    Object.keys($injector)
      .filter((key) => typeof realInjector[key] === 'function')
      .forEach((key) => (proxyInjector[key] = realInjector[key].bind(realInjector)));
  }
}

export const KubernetesReactInjector: KubernetesReactInject = new KubernetesReactInject();
