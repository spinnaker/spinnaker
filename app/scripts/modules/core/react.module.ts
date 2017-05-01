import {module} from 'angular';
import 'ngimport';
import * as ReactGA from 'react-ga';
import * as angular from 'angular';
import IInjectorService = angular.auto.IInjectorService;

import {STATE_EVENTS} from 'core/state.events';
import {SETTINGS} from 'core/config/settings';

// Initialize React Google Analytics
if (SETTINGS.analytics.ga) {
  ReactGA.initialize(SETTINGS.analytics.ga, {});
}

type IWantInjector = ($injector: IInjectorService) => void;

class ReactInject {
  private queue: IWantInjector[] = [];
  public $injector: IInjectorService;

  public give(fn: IWantInjector): void {
    if (this.$injector) {
      fn(this.$injector);
    } else {
      this.queue.push(fn);
    }
  }

  public flush($injector: IInjectorService) {
    this.$injector = $injector;
    while (this.queue.length) {
      this.give(this.queue.pop());
    }
  }
}

export const ReactInjector = new ReactInject();

export const REACT_MODULE = 'spinnaker.react';
module(REACT_MODULE, [
  'bcherny/ngimport',
  STATE_EVENTS,
]).run(function ($injector: any) {
  // Make angular services importable and Convert angular components to react
  ReactInjector.flush($injector);
});

