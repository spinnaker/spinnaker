import { module } from 'angular';
import IInjectorService = angular.auto.IInjectorService;

import { Application } from 'core/application';

const injectables: string[] = [];

export class ConsoleDebugWindow {
  public application: Application;
  public $injector: IInjectorService;
  [key: string]: any;

  public addInjectable(key: string): void {
    injectables.push(key);
  }
}

export const DebugWindow = new ConsoleDebugWindow();

(window as any).spinnaker = DebugWindow;

export const DEBUG_WINDOW = 'spinnaker.core.utils.consoleDebug';
module(DEBUG_WINDOW, []).run([
  '$injector',
  ($injector: IInjectorService) => {
    DebugWindow.$injector = $injector;
    injectables.forEach(k => (DebugWindow[k] = $injector.get(k)));
  },
]);
