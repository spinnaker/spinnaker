import {module} from 'angular';

import {Application} from 'core/application/application.model';

export interface IServerGroupCommandBuilderOptions {
  account: string;
  mode: string;
  region: string;
}

export class ServerGroupCommandBuilderService {

  private getDelegate(provider: string): any {
    return this.serviceDelegate.getDelegate(provider, 'serverGroup.commandBuilder');
  }

  constructor(private serviceDelegate: any) { 'ngInject'; }

  public buildNewServerGroupCommand(application: Application,
                                    provider: string,
                                    options: IServerGroupCommandBuilderOptions): any {
    return this.getDelegate(provider).buildNewServerGroupCommand(application, options);
  }

  public buildServerGroupCommandFromExisting(application: Application,
                                             serverGroup: any,
                                             mode: string): any {
    return this.getDelegate(serverGroup.type).buildServerGroupCommandFromExisting(application, serverGroup, mode);
  }

  public buildNewServerGroupCommandForPipeline(provider: string,
                                               currentStage: any,
                                               pipeline: any): any {
    return this.getDelegate(provider).buildNewServerGroupCommandForPipeline(currentStage, pipeline);
  }

  public buildServerGroupCommandFromPipeline(application: Application,
                                             cluster: any,
                                             currentStage: any,
                                             pipeline: any): any {
    return this.getDelegate(cluster.provider).buildServerGroupCommandFromPipeline(application, cluster, currentStage, pipeline);
  }
}

export const SERVER_GROUP_COMMAND_BUILDER_SERVICE = 'spinnaker.core.serverGroup.configure.common.service';
module(SERVER_GROUP_COMMAND_BUILDER_SERVICE, [
  require('core/cloudProvider/serviceDelegate.service.js')
])
  .service('serverGroupCommandBuilder', ServerGroupCommandBuilderService);
