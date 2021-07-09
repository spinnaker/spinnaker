import { module } from 'angular';

import { IArtifact, IArtifactAccountPair, IServerGroup } from '@spinnaker/core';

import { IAppengineServerGroupCommand } from './configure/serverGroupCommandBuilder.service';
import { GitCredentialType, IAppengineGitTrigger, IAppengineJenkinsTrigger } from '../domain/index';

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
  public configArtifacts: IArtifactAccountPair[];
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
  public expectedArtifactId: string;
  public expectedArtifact: IArtifact;
  public fromArtifact: boolean;
  public sourceType: string;
  public storageAccountName?: string;
  public containerImageUrl?: string;
  public suppressVersionString?: boolean;

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
    this.configArtifacts = command.configArtifacts.filter((a) => !!a.id || !!a.artifact);
    this.applicationDirectoryRoot = command.applicationDirectoryRoot;
    this.interestingHealthProviderNames = command.interestingHealthProviderNames || [];
    this.expectedArtifactId = command.expectedArtifactId;
    this.expectedArtifact = command.expectedArtifact;
    this.fromArtifact = command.fromArtifact;
    this.sourceType = command.sourceType;
    this.storageAccountName = command.storageAccountName;
    this.containerImageUrl = command.containerImageUrl;
    this.suppressVersionString = command.suppressVersionString;
  }
}

export class AppengineServerGroupTransformer {
  public static $inject = ['$q'];
  constructor(private $q: ng.IQService) {}

  public normalizeServerGroup(serverGroup: IServerGroup): PromiseLike<IServerGroup> {
    return this.$q.resolve(serverGroup);
  }

  public convertServerGroupCommandToDeployConfiguration(command: IAppengineServerGroupCommand) {
    return new AppengineDeployDescription(command);
  }
}

export const APPENGINE_SERVER_GROUP_TRANSFORMER = 'spinnaker.appengine.serverGroup.transformer.service';

module(APPENGINE_SERVER_GROUP_TRANSFORMER, []).service(
  'appengineServerGroupTransformer',
  AppengineServerGroupTransformer,
);
