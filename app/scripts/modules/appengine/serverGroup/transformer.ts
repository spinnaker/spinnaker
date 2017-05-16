import {module} from 'angular';

import { IServerGroup } from 'core/domain/index';
import {IAppengineServerGroupCommand} from './configure/serverGroupCommandBuilder.service';
import {IAppengineGitTrigger, IAppengineJenkinsTrigger, GitCredentialType} from 'appengine/domain/index';

export class AppengineDeployDescription {
  public cloudProvider = 'appengine';
  public provider = 'appengine';
  public credentials: string;
  public account: string;
  public application: string;
  public stack?: string;
  public freeFormDetails?: string;
  public repositoryUrl: string;
  public branch: string;
  public configFilepaths: string[];
  public configFiles: string[];
  public applicationDirectoryRoot: string;
  public promote?: boolean;
  public stopPreviousVersion?: boolean;
  public type: string;
  public region: string;
  public strategy?: string;
  public strategyApplication?: string;
  public strategyPipeline?: string;
  public fromTrigger?: boolean;
  public trigger?: IAppengineGitTrigger | IAppengineJenkinsTrigger;
  public gitCredentialType: GitCredentialType;
  public interestingHealthProviderNames: string[];

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
    this.configFiles = command.configFiles;
    this.applicationDirectoryRoot = command.applicationDirectoryRoot;
    this.interestingHealthProviderNames = command.interestingHealthProviderNames || [];
  }
}

class AppengineServerGroupTransformer {
  constructor (private $q: ng.IQService) { 'ngInject'; }

  public normalizeServerGroup (serverGroup: IServerGroup): ng.IPromise<IServerGroup> {
    return this.$q.resolve(serverGroup);
  }

  public convertServerGroupCommandToDeployConfiguration (command: IAppengineServerGroupCommand) {
    return new AppengineDeployDescription(command);
  }
}

export const APPENGINE_SERVER_GROUP_TRANSFORMER = 'spinnaker.appengine.serverGroup.transformer.service';

module(APPENGINE_SERVER_GROUP_TRANSFORMER, [])
  .service('appengineServerGroupTransformer', AppengineServerGroupTransformer);
