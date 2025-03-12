import { module } from 'angular';
import { cloneDeep } from 'lodash';

export interface IWatchConfig {
  property: string;
  method: Function;
}

export interface IServerGroupCommandConfigurer {
  beforeConfiguration: (command: any) => void;
  attachEventHandlers: (command: any) => void;
}

export class ServerGroupCommandRegistry implements ng.IServiceProvider {
  private providers: Map<string, any> = new Map<string, any>();

  public $get(): ServerGroupCommandRegistry {
    return this;
  }

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

export const SERVER_GROUP_COMMAND_REGISTRY_PROVIDER = 'spinnaker.core.serverGroup.configure.command.registry';
module(SERVER_GROUP_COMMAND_REGISTRY_PROVIDER, []).provider('serverGroupCommandRegistry', ServerGroupCommandRegistry);
