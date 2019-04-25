import { IPromise, IQService } from 'angular';

import { IStage, IPipeline, Application } from '@spinnaker/core';

import { ICloudFoundryApplication, ICloudFoundryEnvVar, ICloudFoundryServerGroup } from 'cloudfoundry/domain';
import {
  ICloudFoundryCreateServerGroupCommand,
  ICloudFoundryDeployConfiguration,
} from './serverGroupConfigurationModel.cf';

export class CloudFoundryServerGroupCommandBuilder {
  // TODO:  Remove?
  public static buildUpdateServerGroupCommand(_originalServerGroup: any) {
    throw new Error('Implement me!');
  }

  private static envVarsFromObject(someObject: any): ICloudFoundryEnvVar[] {
    const envVars = [];
    for (const property in someObject) {
      if (someObject.hasOwnProperty(property)) {
        const envVar = { key: property, value: someObject[property] };
        envVars.push(envVar);
      }
    }
    return envVars;
  }

  private static getSubmitButtonLabel(mode: string) {
    switch (mode) {
      case 'createPipeline':
        return 'Add';
      case 'editPipeline':
      case 'editClonePipeline':
        return 'Done';
      case 'clone':
        return 'Clone';
      default:
        return 'Create';
    }
  }

  public static $inject = ['$q'];
  constructor(private $q: IQService) {}

  public buildNewServerGroupCommand(app: Application, defaults: any): IPromise<ICloudFoundryCreateServerGroupCommand> {
    defaults = defaults || {};
    return this.$q.when({
      application: app.name,
      stack: '',
      freeFormDetails: '',
      region: '',
      strategy: '',
      credentials: '',
      viewState: {
        mode: defaults.mode || 'create',
        submitButtonLabel: CloudFoundryServerGroupCommandBuilder.getSubmitButtonLabel(defaults.mode || 'create'),
      },
      selectedProvider: 'cloudfoundry',
      startApplication: true,
    } as ICloudFoundryCreateServerGroupCommand);
  }

  public buildServerGroupCommandFromExisting(
    app: Application,
    serverGroup: ICloudFoundryServerGroup,
    mode = 'clone',
  ): IPromise<ICloudFoundryCreateServerGroupCommand> {
    return this.buildNewServerGroupCommand(app, { mode }).then(command => {
      command.credentials = serverGroup.account;
      command.manifest = {
        direct: {
          memory: serverGroup.memory ? serverGroup.memory + 'M' : '1024M',
          diskQuota: serverGroup.diskQuota ? serverGroup.diskQuota + 'M' : '1024M',
          buildpacks:
            serverGroup.droplet && serverGroup.droplet.buildpacks
              ? serverGroup.droplet.buildpacks.map(item => item.name)
              : [],
          instances: serverGroup.instances ? serverGroup.instances.length : 1,
          routes: serverGroup.loadBalancers,
          environment: CloudFoundryServerGroupCommandBuilder.envVarsFromObject(serverGroup.env),
          services: (serverGroup.serviceInstances || []).map(serviceInstance => serviceInstance.name),
          healthCheckType: 'port',
        },
      };
      command.region = serverGroup.region;
      command.stack = serverGroup.stack;
      command.freeFormDetails = serverGroup.detail;
      return command;
    });
  }

  public buildNewServerGroupCommandForPipeline(
    stage: IStage,
    pipeline: IPipeline,
  ): IPromise<ICloudFoundryCreateServerGroupCommand> {
    return this.buildNewServerGroupCommand({ name: pipeline.application } as ICloudFoundryApplication, {
      mode: 'editPipeline',
    }).then(command => {
      command.viewState = {
        ...command.viewState,
        pipeline,
        requiresTemplateSelection: true,
        stage,
      };
      return command;
    });
  }

  public buildServerGroupCommandFromPipeline(
    application: ICloudFoundryApplication,
    originalCluster: ICloudFoundryDeployConfiguration,
    stage: IStage,
    pipeline: IPipeline,
  ) {
    return this.buildNewServerGroupCommand(application, { mode: 'editPipeline' }).then(command => {
      command.credentials = originalCluster.account;
      command.applicationArtifact = originalCluster.applicationArtifact;
      command.delayBeforeDisableSec = originalCluster.delayBeforeDisableSec;
      command.manifest = originalCluster.manifest;
      command.maxRemainingAsgs = originalCluster.maxRemainingAsgs;
      command.region = originalCluster.region;
      command.rollback = originalCluster.rollback;
      command.strategy = originalCluster.strategy;
      command.startApplication = originalCluster.startApplication;
      if (originalCluster.stack) {
        command.stack = originalCluster.stack;
      }

      if (originalCluster.freeFormDetails) {
        command.freeFormDetails = originalCluster.freeFormDetails;
      }

      command.viewState = {
        ...command.viewState,
        pipeline,
        stage,
      };

      return command;
    });
  }

  public buildCloneServerGroupCommandFromPipeline(
    stage: IStage,
    pipeline: IPipeline,
  ): IPromise<ICloudFoundryCreateServerGroupCommand> {
    return this.buildNewServerGroupCommand({ name: pipeline.application } as ICloudFoundryApplication, {
      mode: 'editClonePipeline',
    }).then(command => {
      command.credentials = stage.credentials;
      command.capacity = stage.capacity;
      command.account = stage.account;
      command.destination = stage.destination;
      command.delayBeforeDisableSec = stage.delayBeforeDisableSec;
      command.freeFormDetails = stage.freeFormDetails || command.freeFormDetails;
      command.maxRemainingAsgs = stage.maxRemainingAsgs;
      command.region = stage.region;
      command.startApplication = stage.startApplication === undefined || stage.startApplication;
      command.stack = stage.stack || command.stack;
      command.strategy = stage.strategy;
      command.target = stage.target;
      command.targetCluster = stage.targetCluster;
      command.manifest = stage.manifest || command.manifest;

      command.viewState = {
        ...command.viewState,
        pipeline,
        stage,
      };

      return command;
    });
  }
}
