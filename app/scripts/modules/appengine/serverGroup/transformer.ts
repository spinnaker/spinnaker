import {module} from 'angular';

import {ServerGroup} from 'core/domain/index';
import {IAppengineServerGroupCommand} from './configure/serverGroupCommandBuilder.service';

class AppengineDeployDescription {
  cloudProvider: string = 'appengine';
  credentials: string;
  application: string;
  stack?: string;
  freeFormDetails?: string;
  repositoryUrl: string;
  branch: string;
  appYamlPath: string;
  promote?: boolean;
  stopPreviousVersion?: boolean;
  type: string;
  region: string;

  constructor(command: IAppengineServerGroupCommand) {
    this.credentials = command.credentials;
    this.application = command.application;
    this.stack = command.stack;
    this.freeFormDetails = command.freeFormDetails;
    this.repositoryUrl = command.repositoryUrl;
    this.branch = command.branch;
    this.appYamlPath = command.appYamlPath;
    this.promote = command.promote;
    this.stopPreviousVersion = command.stopPreviousVersion;
    this.type = command.type;
    this.region = command.region;
  }
}

class AppengineServerGroupTransformer {
  static get $inject () { return ['$q']; }

  constructor (private $q: ng.IQService) { }

  public normalizeServerGroup (serverGroup: ServerGroup): ng.IPromise<ServerGroup> {
    return this.$q.resolve(serverGroup);
  }

  public convertServerGroupCommandToDeployConfiguration (command: IAppengineServerGroupCommand) {
    return new AppengineDeployDescription(command);
  }
}

export const APPENGINE_SERVER_GROUP_TRANSFORMER = 'spinnaker.appengine.serverGroup.transformer.service';

module(APPENGINE_SERVER_GROUP_TRANSFORMER, [])
  .service('appengineServerGroupTransformer', AppengineServerGroupTransformer);
