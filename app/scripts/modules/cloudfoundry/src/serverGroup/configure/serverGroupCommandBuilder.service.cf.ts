import { IPromise, IQService, module } from 'angular';

import { IStage, IPipeline, Application } from '@spinnaker/core';

import { ICloudFoundryApplication, ICloudFoundryEnvVar, ICloudFoundryServerGroup } from 'cloudfoundry/domain';
import {
  ICloudFoundryCreateServerGroupCommand,
  ICloudFoundryDeployConfiguration,
} from './serverGroupConfigurationModel.cf';

export class CloudFoundryServerGroupCommandBuilder {
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
        return 'Done';
      case 'clone':
        return 'Clone';
      default:
        return 'Create';
    }
  }

  constructor(private $q: IQService) {
    'ngInject';
  }

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
      artifact: {
        type: 'artifact',
        reference: '',
        account: '',
      },
      manifest: {
        type: 'artifact',
        reference: '',
        account: '',
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
      command.artifact = {
        type: 'artifact',
        reference: '',
        account: '',
      };
      command.manifest = {
        type: 'direct',
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
        healthCheckType: '',
        healthCheckHttpEndpoint: '',
      };
      command.region = serverGroup.region;
      command.stack = serverGroup.stack;
      command.freeFormDetails = serverGroup.detail;
      command.source = { asgName: serverGroup.name };
      return command;
    });
  }

  public buildNewServerGroupCommandForPipeline(
    _stage: IStage,
    pipeline: IPipeline,
  ): IPromise<ICloudFoundryCreateServerGroupCommand> {
    return this.buildNewServerGroupCommand({ name: pipeline.application } as ICloudFoundryApplication, {
      mode: 'editPipeline',
    }).then(command => {
      command.viewState.requiresTemplateSelection = true;
      return command;
    });
  }

  public buildServerGroupCommandFromPipeline(
    application: ICloudFoundryApplication,
    originalCluster: ICloudFoundryDeployConfiguration,
  ) {
    return this.buildNewServerGroupCommand(application, { mode: 'editPipeline' }).then(app => {
      app.credentials = originalCluster.account;
      app.artifact = originalCluster.artifact;
      app.delayBeforeDisableSec = originalCluster.delayBeforeDisableSec;
      app.manifest = originalCluster.manifest;
      app.maxRemainingAsgs = originalCluster.maxRemainingAsgs;
      app.region = originalCluster.region;
      app.rollback = originalCluster.rollback;
      app.strategy = originalCluster.strategy;
      app.startApplication = originalCluster.startApplication;
      if (originalCluster.stack) {
        app.stack = originalCluster.stack;
      }

      if (originalCluster.freeFormDetails) {
        app.freeFormDetails = originalCluster.freeFormDetails;
      }

      return app;
    });
  }
}

export const CLOUD_FOUNDRY_SERVER_GROUP_COMMAND_BUILDER = 'spinnaker.cloudfoundry.serverGroupCommandBuilder.service';

module(CLOUD_FOUNDRY_SERVER_GROUP_COMMAND_BUILDER, []).service(
  'cfServerGroupCommandBuilder',
  CloudFoundryServerGroupCommandBuilder,
);
