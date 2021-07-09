import { IQService } from 'angular';

import { Application, IPipeline, IStage } from '@spinnaker/core';
import { ICloudFoundryApplication, ICloudFoundryServerGroup } from '../../domain';

import { CloudFoundryServerGroupCommandBuilder } from './serverGroupCommandBuilder.service.cf';
import {
  ICloudFoundryCreateServerGroupCommand,
  ICloudFoundryDeployConfiguration,
} from './serverGroupConfigurationModel.cf';

export class CloudFoundryServerGroupCommandBuilderShim {
  public static $inject = ['$q'];
  constructor(private $q: IQService) {}

  public buildNewServerGroupCommand(
    app: Application,
    defaults: any,
  ): PromiseLike<ICloudFoundryCreateServerGroupCommand> {
    return this.$q.when(CloudFoundryServerGroupCommandBuilder.buildNewServerGroupCommand(app, defaults));
  }

  public buildServerGroupCommandFromExisting(
    app: Application,
    serverGroup: ICloudFoundryServerGroup,
    mode = 'clone',
  ): PromiseLike<ICloudFoundryCreateServerGroupCommand> {
    return this.$q.when(
      CloudFoundryServerGroupCommandBuilder.buildServerGroupCommandFromExisting(app, serverGroup, mode),
    );
  }

  public buildNewServerGroupCommandForPipeline(
    stage: IStage,
    pipeline: IPipeline,
  ): PromiseLike<ICloudFoundryCreateServerGroupCommand> {
    return this.$q.when(CloudFoundryServerGroupCommandBuilder.buildNewServerGroupCommandForPipeline(stage, pipeline));
  }

  public buildServerGroupCommandFromPipeline(
    application: ICloudFoundryApplication,
    originalCluster: ICloudFoundryDeployConfiguration,
    stage: IStage,
    pipeline: IPipeline,
  ): PromiseLike<ICloudFoundryCreateServerGroupCommand> {
    return this.$q.when(
      CloudFoundryServerGroupCommandBuilder.buildServerGroupCommandFromPipeline(
        application,
        originalCluster,
        stage,
        pipeline,
      ),
    );
  }

  public buildCloneServerGroupCommandFromPipeline(
    stage: IStage,
    pipeline: IPipeline,
  ): PromiseLike<ICloudFoundryCreateServerGroupCommand> {
    return this.$q.when(
      CloudFoundryServerGroupCommandBuilder.buildCloneServerGroupCommandFromPipeline(stage, pipeline),
    );
  }
}
