import { Application, IPipeline, IStage } from '@spinnaker/core';
import { ICloudFoundryApplication, ICloudFoundryEnvVar, ICloudFoundryServerGroup } from '../../domain';

import {
  ICloudFoundryCreateServerGroupCommand,
  ICloudFoundryDeployConfiguration,
} from './serverGroupConfigurationModel.cf';

export class CloudFoundryServerGroupCommandBuilder {
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

  public static buildNewServerGroupCommand(app: Application, defaults: any): ICloudFoundryCreateServerGroupCommand {
    defaults = defaults || {};
    return {
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
    } as ICloudFoundryCreateServerGroupCommand;
  }

  public static buildServerGroupCommandFromExisting(
    app: Application,
    serverGroup: ICloudFoundryServerGroup,
    mode = 'clone',
  ): ICloudFoundryCreateServerGroupCommand {
    const command: ICloudFoundryCreateServerGroupCommand = this.buildNewServerGroupCommand(app, { mode });
    command.credentials = '';
    command.manifest = {
      direct: {
        memory: serverGroup.memory ? serverGroup.memory + 'M' : '1024M',
        diskQuota: serverGroup.diskQuota ? serverGroup.diskQuota + 'M' : '1024M',
        buildpacks:
          serverGroup.droplet && serverGroup.droplet.buildpacks
            ? serverGroup.droplet.buildpacks.map((item) => item.name)
            : [],
        instances: serverGroup.instances ? serverGroup.instances.length : 1,
        routes: serverGroup.loadBalancers || [],
        environment: CloudFoundryServerGroupCommandBuilder.envVarsFromObject(serverGroup.env),
        services: (serverGroup.serviceInstances || []).map((serviceInstance) => serviceInstance.name),
        healthCheckType: 'port',
      },
    };
    command.region = '';
    command.stack = serverGroup.stack;
    command.freeFormDetails = serverGroup.detail;
    command.source = {
      asgName: serverGroup.name,
      region: serverGroup.region,
      account: serverGroup.account,
    };
    return command;
  }

  public static buildNewServerGroupCommandForPipeline(
    stage: IStage,
    pipeline: IPipeline,
  ): ICloudFoundryCreateServerGroupCommand {
    const command: ICloudFoundryCreateServerGroupCommand = this.buildNewServerGroupCommand(
      { name: pipeline.application } as Application,
      { mode: 'editPipeline' },
    );
    command.viewState = {
      ...command.viewState,
      pipeline,
      requiresTemplateSelection: true,
      stage,
    };
    return command;
  }

  public static buildServerGroupCommandFromPipeline(
    application: ICloudFoundryApplication,
    originalCluster: ICloudFoundryDeployConfiguration,
    stage: IStage,
    pipeline: IPipeline,
  ): ICloudFoundryCreateServerGroupCommand {
    const command = this.buildNewServerGroupCommand(application, { mode: 'editPipeline' });
    command.credentials = originalCluster.account;
    command.applicationArtifact = originalCluster.applicationArtifact;
    command.delayBeforeDisableSec = originalCluster.delayBeforeDisableSec;
    command.manifest = originalCluster.manifest;
    command.maxRemainingAsgs = originalCluster.maxRemainingAsgs;
    command.region = originalCluster.region;
    command.rollback = originalCluster.rollback;
    command.strategy = originalCluster.strategy;
    command.startApplication = originalCluster.startApplication;
    command.targetPercentages = originalCluster.targetPercentages;
    command.delayBeforeScaleDownSec = originalCluster.delayBeforeScaleDownSec;
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
  }

  public static buildCloneServerGroupCommandFromPipeline(
    stage: IStage,
    pipeline: IPipeline,
  ): ICloudFoundryCreateServerGroupCommand {
    const command = this.buildNewServerGroupCommand({ name: pipeline.application } as Application, {
      mode: 'editClonePipeline',
    });
    command.credentials = stage.credentials;
    command.capacity = stage.capacity;
    command.account = stage.account;
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
    command.source = stage.source;

    command.viewState = {
      ...command.viewState,
      pipeline,
      stage,
    };

    return command;
  }
}
