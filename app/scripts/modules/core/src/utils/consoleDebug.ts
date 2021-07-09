import { module } from 'angular';

import { REST } from '../api';
import { Application } from '../application';

import IInjectorService = angular.auto.IInjectorService;

declare global {
  // tslint:disable-next-line
  interface Window {
    spinnaker: ConsoleDebugWindow;
  }
}

const injectables: string[] = [];

export class ConsoleDebugWindow {
  public application: Application;
  public $injector: IInjectorService;
  public api = REST;
  public plugins = {
    sharedLibraries: {} as { [libraryName: string]: any },
  };
  [key: string]: any;

  public addInjectable(key: string): void {
    injectables.push(key);
  }
}

export const DebugWindow = new ConsoleDebugWindow();
if (window) {
  window.spinnaker = DebugWindow;
}

(window as any).spinnaker = DebugWindow;

export const DEBUG_WINDOW = 'spinnaker.core.utils.consoleDebug';
module(DEBUG_WINDOW, []).run([
  '$injector',
  ($injector: IInjectorService) => {
    DebugWindow.$injector = $injector;
    injectables.forEach((k) => (DebugWindow[k] = $injector.get(k)));
  },
]);
