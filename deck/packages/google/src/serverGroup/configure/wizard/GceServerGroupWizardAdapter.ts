import { cloneDeep } from 'lodash';

import type { Application, DeckRuntimeServices, IPipeline, IServerGroup, IStage } from '@spinnaker/core';

import type {
  GceCommandHandlerName,
  GceConfigurationRefreshMethod,
  GceConfigurationUpdateMethod,
  GceServerGroupOperationMode,
  IGceServerGroupCommand,
  IGceServerGroupCommandBuilderAdapter,
  IGceServerGroupCommandDefaults,
  IGceServerGroupCommandUpdate,
  IGceServerGroupCommandUpdateResult,
  IGceServerGroupConfigurationAdapter,
  IGceServerGroupWizardAdapter,
} from './GceServerGroupWizard.types';
import { GceServerGroupCommandBuilder } from '../serverGroupCommandBuilder.service';
import { GceServerGroupConfigurationService } from '../serverGroupConfiguration.service';

const EMPTY_UPDATE_RESULT: IGceServerGroupCommandUpdateResult = { dirty: {} };

export class GceServerGroupWizardAdapter implements IGceServerGroupWizardAdapter {
  public static fromRuntimeServices(
    runtimeServices: Pick<DeckRuntimeServices, 'loadBalancerReader' | 'securityGroupReader'>,
  ): GceServerGroupWizardAdapter {
    return new GceServerGroupWizardAdapter(
      undefined,
      (new GceServerGroupConfigurationService(
        undefined,
        runtimeServices.securityGroupReader,
        runtimeServices.loadBalancerReader,
      ) as unknown) as IGceServerGroupConfigurationAdapter,
    );
  }

  public constructor(
    private commandBuilder = (new GceServerGroupCommandBuilder() as unknown) as IGceServerGroupCommandBuilderAdapter,
    private configurationService = (new GceServerGroupConfigurationService() as unknown) as IGceServerGroupConfigurationAdapter,
  ) {}

  public buildNewServerGroupCommand(
    application: Application,
    defaults?: IGceServerGroupCommandDefaults,
  ): Promise<IGceServerGroupCommand> {
    return this.commandBuilder.buildNewServerGroupCommand(application, defaults);
  }

  public buildNewServerGroupCommandForPipeline(
    currentStage: IStage,
    pipeline: IPipeline,
  ): Promise<IGceServerGroupCommand> {
    return this.commandBuilder.buildNewServerGroupCommandForPipeline(currentStage, pipeline);
  }

  public buildServerGroupCommandFromExisting(
    application: Application,
    serverGroup: IServerGroup,
    mode?: GceServerGroupOperationMode,
  ): Promise<IGceServerGroupCommand> {
    return this.commandBuilder.buildServerGroupCommandFromExisting(application, serverGroup, mode);
  }

  public buildServerGroupCommandFromPipeline(
    application: Application,
    originalCluster: Partial<IGceServerGroupCommand>,
    currentStage: IStage,
    pipeline: IPipeline,
  ): Promise<IGceServerGroupCommand> {
    return this.commandBuilder.buildServerGroupCommandFromPipeline(
      application,
      originalCluster,
      currentStage,
      pipeline,
    );
  }

  public async configureCommand(
    application: Application,
    command: IGceServerGroupCommand,
  ): Promise<IGceServerGroupCommand> {
    const nextCommand = cloneDeep(command);
    await this.configurationService.configureCommand(application, nextCommand);
    return nextCommand;
  }

  public applyCommandHandler(
    command: IGceServerGroupCommand,
    handler: GceCommandHandlerName,
  ): Promise<IGceServerGroupCommandUpdate> {
    return this.applyUpdate(command, (nextCommand) => nextCommand[handler]?.(nextCommand));
  }

  public applyConfigurationUpdate(
    command: IGceServerGroupCommand,
    method: GceConfigurationUpdateMethod,
  ): Promise<IGceServerGroupCommandUpdate> {
    return this.applyUpdate(command, (nextCommand) => this.configurationService[method](nextCommand));
  }

  public applyConfigurationRefresh(
    command: IGceServerGroupCommand,
    method: GceConfigurationRefreshMethod,
    skipCommandReconfiguration?: boolean,
  ): Promise<IGceServerGroupCommandUpdate> {
    return this.applyUpdate(command, async (nextCommand) => {
      if (method === 'refreshInstanceTypes') {
        await this.configurationService.refreshInstanceTypes(nextCommand);
      } else {
        await this.configurationService[method](nextCommand, skipCommandReconfiguration);
      }
      return EMPTY_UPDATE_RESULT;
    });
  }

  private async applyUpdate(
    command: IGceServerGroupCommand,
    update: (
      nextCommand: IGceServerGroupCommand,
    ) => IGceServerGroupCommandUpdateResult | void | Promise<IGceServerGroupCommandUpdateResult | void>,
  ): Promise<IGceServerGroupCommandUpdate> {
    const nextCommand = cloneDeep(command);
    const result = (await update(nextCommand)) || EMPTY_UPDATE_RESULT;
    nextCommand.viewState = {
      ...nextCommand.viewState,
      dirty: { ...nextCommand.viewState.dirty, ...result.dirty },
    };
    nextCommand.processCommandUpdateResult?.(result);
    return { command: nextCommand, result };
  }
}
