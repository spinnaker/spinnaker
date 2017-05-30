import { IPromise, module } from 'angular';

import { IServerGroupCommand } from './serverGroupCommandBuilder.service';

export class ServerGroupConfigurationService {
  constructor(private serviceDelegate: any) {
    'ngImport';
  }

  private getDelegate(provider: string): any {
    return this.serviceDelegate.getDelegate(provider, 'serverGroup.configurationService');
  }

  public refreshInstanceTypes(provider: string, command: IServerGroupCommand): IPromise<void> {
    return this.getDelegate(provider).refreshInstanceTypes(command);
  }
}

export const SERVER_GROUP_CONFIGURATION_SERVICE = 'spinnaker.core.serverGroup.configure.common.configure.service';
module(SERVER_GROUP_CONFIGURATION_SERVICE, [
  require('core/cloudProvider/serviceDelegate.service.js'),
])
  .service('serverGroupConfigurationService', ServerGroupConfigurationService);
