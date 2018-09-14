import { IPromise, IQService, module } from 'angular';

import { IStage, IPipeline } from '@spinnaker/core';

import { ICloudFoundryApplication, ICloudFoundryServerGroup } from 'cloudfoundry/domain';
import {
  ICloudFoundryCreateServerGroupCommand,
  ICloudFoundryDeployConfiguration,
} from './serverGroupConfigurationModel.cf';

export class CloudFoundryServerGroupCommandBuilder {
  constructor(private $q: IQService) {
    'ngInject';
  }

  private getSubmitButtonLabel(mode: string) {
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

  public buildNewServerGroupCommand(
    app: ICloudFoundryApplication,
    defaults: any = {},
  ): IPromise<ICloudFoundryCreateServerGroupCommand> {
    return this.$q.when({
      application: app.name,
      stack: '',
      freeFormDetails: '',
      region: '',
      strategy: '',
      credentials: '',
      viewState: {
        mode: defaults.mode || 'create',
        submitButtonLabel: this.getSubmitButtonLabel(defaults.mode || 'create'),
      },
      artifact: {
        type: 'artifact',
        reference: '',
        account: '',
      },
      manifest: {
        type: 'direct',
        memory: '1024M',
        diskQuota: '1024M',
        buildpack: '',
        instances: 1,
        routes: [],
        env: [],
        services: [],
      },
      startApplication: true,
    } as ICloudFoundryCreateServerGroupCommand);
  }

  public buildServerGroupCommandFromExisting(
    app: ICloudFoundryApplication,
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
        memory: serverGroup.memory + 'M',
        diskQuota: serverGroup.diskQuota + 'M',
        buildpack: serverGroup.droplet.buildpacks.length > 0 ? serverGroup.droplet.buildpacks[0].name : '',
        instances: serverGroup.instances.length,
        routes: serverGroup.loadBalancers,
        env: serverGroup.env,
        services: serverGroup.serviceInstances.map(serviceInstance => serviceInstance.name),
        reference: '',
        account: '',
        pattern: '',
      };
      command.region = serverGroup.region;
      command.stack = serverGroup.stack;
      command.freeFormDetails = serverGroup.detail;
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
      app.manifest = originalCluster.manifest;
      app.region = originalCluster.region;
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

  public buildUpdateServerGroupCommand(_originalServerGroup: any) {
    throw new Error('Implement me!');
  }
}

export const CLOUD_FOUNDRY_SERVER_GROUP_COMMAND_BUILDER = 'spinnaker.cloudfoundry.serverGroupCommandBuilder.service';

module(CLOUD_FOUNDRY_SERVER_GROUP_COMMAND_BUILDER, []).service(
  'cfServerGroupCommandBuilder',
  CloudFoundryServerGroupCommandBuilder,
);
