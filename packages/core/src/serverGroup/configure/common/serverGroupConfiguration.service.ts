import { module } from 'angular';
import { PROVIDER_SERVICE_DELEGATE, ProviderServiceDelegate } from '../../../cloudProvider/providerService.delegate';

import { IServerGroupCommand } from './serverGroupCommandBuilder.service';

export class ServerGroupConfigurationService {
  public static $inject = ['providerServiceDelegate'];
  constructor(private providerServiceDelegate: ProviderServiceDelegate) {
    'ngImport';
  }

  private getDelegate(provider: string): any {
    return this.providerServiceDelegate.getDelegate(provider, 'serverGroup.configurationService');
  }

  public refreshInstanceTypes(provider: string, command: IServerGroupCommand): PromiseLike<void> {
    return this.getDelegate(provider).refreshInstanceTypes(command);
  }
}

export const SERVER_GROUP_CONFIGURATION_SERVICE = 'spinnaker.core.serverGroup.configure.common.configure.service';
module(SERVER_GROUP_CONFIGURATION_SERVICE, [PROVIDER_SERVICE_DELEGATE]).service(
  'serverGroupConfigurationService',
  ServerGroupConfigurationService,
);
