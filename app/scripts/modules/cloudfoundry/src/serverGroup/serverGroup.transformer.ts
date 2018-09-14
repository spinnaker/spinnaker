import { module, IPromise } from 'angular';

import { defaults } from 'lodash';

import { ICloudFoundryServerGroup } from 'cloudfoundry/domain';

export class CloudFoundryServerGroupTransformer {
  public constructor(private $q: ng.IQService) {
    'ngInject';
  }

  public normalizeServerGroupDetails(serverGroup: ICloudFoundryServerGroup): ICloudFoundryServerGroup {
    return serverGroup;
  }

  public normalizeServerGroup(serverGroup: ICloudFoundryServerGroup): IPromise<ICloudFoundryServerGroup> {
    return this.$q.resolve(serverGroup);
  }

  public convertServerGroupCommandToDeployConfiguration(base: any): any {
    const command = defaults({ viewState: [] }, base);
    command.cloudProvider = 'cloudfoundry';
    command.provider = 'cloudfoundry';
    command.account = command.credentials;
    delete command.viewState;
    delete command.selectedProvider;
    return command;
  }
}

export const CLOUD_FOUNDRY_SERVER_GROUP_TRANSFORMER = 'spinnaker.cloudfoundry.serverGroup.transformer';
module(CLOUD_FOUNDRY_SERVER_GROUP_TRANSFORMER, []).service(
  'cfServerGroupTransformer',
  CloudFoundryServerGroupTransformer,
);
