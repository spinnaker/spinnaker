import { REST } from '../api';
import type { Application } from '../application';

declare global {
  // tslint:disable-next-line
  interface Window {
    spinnaker: ConsoleDebugWindow;
  }
}

export class ConsoleDebugWindow {
  public application: Application;
  public plugins = {
    sharedLibraries: {} as { [libraryName: string]: any },
  };
  [key: string]: any;

  public get api() {
    return REST;
  }
}

export const DebugWindow = new ConsoleDebugWindow();
if (window) {
  window.spinnaker = DebugWindow;
}

(window as any).spinnaker = DebugWindow;
