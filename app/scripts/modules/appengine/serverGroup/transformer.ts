import {module} from 'angular';

import {ServerGroup} from 'core/domain/index';
import {IAppengineServerGroupCommand} from './configure/serverGroupCommandBuilder.service';
import {IAppengineGitTrigger, IAppengineJenkinsTrigger, GitCredentialType} from 'appengine/domain/index';

export class AppengineDeployDescription {
  cloudProvider = 'appengine';
  provider = 'appengine';
  credentials: string;
  account: string;
  application: string;
  stack?: string;
  freeFormDetails?: string;
  repositoryUrl: string;
  branch: string;
  configFilepaths: string[];
  promote?: boolean;
  stopPreviousVersion?: boolean;
  type: string;
  region: string;
  strategy?: string;
  strategyApplication?: string;
  strategyPipeline?: string;
  fromTrigger?: boolean;
  trigger?: IAppengineGitTrigger | IAppengineJenkinsTrigger;
  gitCredentialType: GitCredentialType;

  constructor(command: IAppengineServerGroupCommand) {
    this.credentials = command.credentials;
    this.account = command.credentials;
    this.application = command.application;
    this.stack = command.stack;
    this.freeFormDetails = command.freeFormDetails;
    this.repositoryUrl = command.repositoryUrl;
    this.branch = command.branch;
    this.configFilepaths = command.configFilepaths;
    this.promote = command.promote;
    this.stopPreviousVersion = command.stopPreviousVersion;
    this.type = command.type;
    this.region = command.region;
    this.strategy = command.strategy;
    this.strategyApplication = command.strategyApplication;
    this.strategyPipeline = command.strategyPipeline;
    this.fromTrigger = command.fromTrigger;
    this.trigger = command.trigger;
    this.gitCredentialType = command.gitCredentialType;
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
