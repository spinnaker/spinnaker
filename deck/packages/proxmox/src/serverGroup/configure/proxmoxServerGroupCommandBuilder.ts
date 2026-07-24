import type { Application, IAccountDetails, IMoniker } from '@spinnaker/core';
import { AccountService, SETTINGS } from '@spinnaker/core';

export interface IProxmoxServerGroupCommandViewState {
  mode: string;
  submitButtonLabel: string;
  disableStrategySelection?: boolean;
  hideClusterNamePreview?: boolean;
  pipeline?: any;
  stage?: any;
}

export interface IProxmoxServerGroupCommandBackingData {
  accounts: IAccountDetails[];
}

/** Mirrors clouddriver's ProxmoxDeployDescription. */
export interface IProxmoxServerGroupCommand {
  application: string;
  stack?: string;
  freeFormDetails?: string;
  credentials: string;
  account?: string;
  region: string;
  moniker?: IMoniker;
  cloudProvider: string;
  selectedProvider: string;
  vmType: string;
  templateVmid?: number;
  templateNode?: string;
  fullClone: boolean;
  storage: string;
  memory: number;
  cores: number;
  sockets: number;
  net0: string;
  ipconfig0: string;
  diskSize?: string;
  diskDevice: string;
  bios?: string;
  strategy?: string;
  backingData?: IProxmoxServerGroupCommandBackingData;
  viewState: IProxmoxServerGroupCommandViewState;
}

const proxmoxDefaults = (): { account?: string; region?: string } => SETTINGS.providers?.proxmox?.defaults ?? {};

const buildCommand = async (
  application: Application,
  mode: string,
  existing?: Partial<IProxmoxServerGroupCommand>,
): Promise<IProxmoxServerGroupCommand> => {
  const accounts = await AccountService.listAccounts('proxmox');
  const defaults = proxmoxDefaults();
  const defaultAccount =
    accounts.find((a) => a.name === defaults.account)?.name ?? accounts[0]?.name ?? defaults.account;

  const command: IProxmoxServerGroupCommand = {
    application: application.name,
    stack: '',
    freeFormDetails: '',
    credentials: defaultAccount,
    region: defaults.region ?? '',
    cloudProvider: 'proxmox',
    selectedProvider: 'proxmox',
    vmType: 'qemu',
    templateVmid: undefined,
    templateNode: '',
    fullClone: true,
    storage: 'local-lvm',
    memory: 512,
    cores: 1,
    sockets: 1,
    net0: 'virtio,bridge=vmbr0',
    ipconfig0: 'ip=dhcp',
    diskSize: '',
    diskDevice: 'scsi0',
    bios: '',
    viewState: {
      mode,
      submitButtonLabel: mode === 'createPipeline' || mode === 'editPipeline' ? 'Done' : 'Create',
    },
    ...existing,
  };
  command.backingData = { accounts };
  return command;
};

/**
 * Instantiated by core's ProviderServiceDelegate ("serverGroup.commandBuilder"), which news this
 * class up with $q; the argument is unused since these methods return native promises.
 */
export class ProxmoxServerGroupCommandBuilder {
  public buildNewServerGroupCommand(
    application: Application,
    options?: { mode?: string },
  ): PromiseLike<IProxmoxServerGroupCommand> {
    return buildCommand(application, options?.mode ?? 'create');
  }

  public buildServerGroupCommandFromExisting(
    application: Application,
    serverGroup: any,
    mode = 'clone',
  ): PromiseLike<IProxmoxServerGroupCommand> {
    const launchConfig = serverGroup.launchConfig ?? {};
    return buildCommand(application, mode, {
      credentials: serverGroup.account,
      region: serverGroup.region,
      stack: serverGroup.moniker?.stack ?? '',
      freeFormDetails: serverGroup.moniker?.detail ?? '',
      memory: launchConfig.memoryMb,
      cores: launchConfig.cores ?? launchConfig.cpus,
      sockets: launchConfig.sockets ?? 1,
      bios: launchConfig.bios ?? '',
    });
  }

  public async buildNewServerGroupCommandForPipeline(
    currentStage: any,
    pipeline: any,
  ): Promise<IProxmoxServerGroupCommand> {
    // The deploy stage passes this command straight to the CloneServerGroupModal, so it must be
    // fully populated; the application is resolvable from the pipeline config.
    const command = await buildCommand({ name: pipeline?.application } as Application, 'editPipeline');
    command.viewState = {
      ...command.viewState,
      pipeline,
      stage: currentStage,
      disableStrategySelection: false,
    };
    return command;
  }

  public async buildServerGroupCommandFromPipeline(
    application: Application,
    cluster: any,
    currentStage: any,
    pipeline: any,
  ): Promise<IProxmoxServerGroupCommand> {
    const command = await buildCommand(application, 'editPipeline', {
      ...cluster,
      credentials: cluster.account ?? cluster.credentials,
    });
    command.viewState = {
      ...command.viewState,
      pipeline,
      stage: currentStage,
    };
    return command;
  }
}
