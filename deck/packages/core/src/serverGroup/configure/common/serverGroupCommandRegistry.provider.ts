import { cloneDeep } from 'lodash';

export interface IWatchConfig {
  property: string;
  method: Function;
}

export interface IServerGroupCommandConfigurer {
  beforeConfiguration: (command: any) => void;
  attachEventHandlers: (command: any) => void;
}

export class ServerGroupCommandRegistry {
  private providers: Map<string, any> = new Map<string, any>();

  public getCommandOverrides(provider: string): IServerGroupCommandConfigurer[] {
    let result: IServerGroupCommandConfigurer[] = [];
    if (this.providers.has(provider)) {
      result = cloneDeep(this.providers.get(provider));
    }

    return result;
  }

  public register(provider: string, handler: IServerGroupCommandConfigurer): void {
    if (!this.providers.has(provider)) {
      this.providers.set(provider, []);
    }
    this.providers.get(provider).push(handler);
  }
}
