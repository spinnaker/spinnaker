import type { Application, IPipeline, IStage } from '@spinnaker/core';
import type { ICloudFoundryApplication, ICloudFoundryServerGroup } from '../../domain';

import { CloudFoundryServerGroupCommandBuilder } from './serverGroupCommandBuilder.service.cf';
import type {
  ICloudFoundryCreateServerGroupCommand,
  ICloudFoundryDeployConfiguration,
} from './serverGroupConfigurationModel.cf';

export class CloudFoundryServerGroupCommandBuilderShim {
  public buildNewServerGroupCommand(
    app: Application,
    defaults: any,
  ): PromiseLike<ICloudFoundryCreateServerGroupCommand> {
    return Promise.resolve(CloudFoundryServerGroupCommandBuilder.buildNewServerGroupCommand(app, defaults));
  }

  public buildServerGroupCommandFromExisting(
    app: Application,
    serverGroup: ICloudFoundryServerGroup,
    mode = 'clone',
  ): PromiseLike<ICloudFoundryCreateServerGroupCommand> {
    return Promise.resolve(
      CloudFoundryServerGroupCommandBuilder.buildServerGroupCommandFromExisting(app, serverGroup, mode),
    );
  }

  public buildNewServerGroupCommandForPipeline(
    stage: IStage,
    pipeline: IPipeline,
  ): PromiseLike<ICloudFoundryCreateServerGroupCommand> {
    return Promise.resolve(
      CloudFoundryServerGroupCommandBuilder.buildNewServerGroupCommandForPipeline(stage, pipeline),
    );
  }

  public buildServerGroupCommandFromPipeline(
    application: ICloudFoundryApplication,
    originalCluster: ICloudFoundryDeployConfiguration,
    stage: IStage,
    pipeline: IPipeline,
  ): PromiseLike<ICloudFoundryCreateServerGroupCommand> {
    return Promise.resolve(
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
    return Promise.resolve(
      CloudFoundryServerGroupCommandBuilder.buildCloneServerGroupCommandFromPipeline(stage, pipeline),
    );
  }
}
