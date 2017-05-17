import IInjectorService = angular.auto.IInjectorService;
import { angular2react } from 'angular2react';

import { ReactInject } from '@spinnaker/core';

import { FastPropertyReaderService } from './fastProperties/fastProperty.read.service';
import { IFastPropertyProps } from './fastProperties/view/filter/FastPropertyFilterSearch';
import { fastPropertyFilterSearchComponent } from './fastProperties/view/filter/fastPropertyFilterSearch.component';

export class NetflixReactInject extends ReactInject {

  // Services
  public get fastPropertyReader() { return this.$injector.get('fastPropertyReader') as FastPropertyReaderService; }

  // Reactified components
  public FastPropertyFilterSearch: React.ComponentClass<IFastPropertyProps>;

  public initialize($injector: IInjectorService) {
    this.$injector = $injector;
    this.FastPropertyFilterSearch = angular2react('fastPropertyFilterSearch', fastPropertyFilterSearchComponent, $injector) as any;
  }
}

export const NetflixReactInjector: NetflixReactInject = new NetflixReactInject();
