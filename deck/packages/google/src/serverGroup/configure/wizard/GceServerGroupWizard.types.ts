import type {
  Application,
  IPipeline,
  IServerGroup,
  IStage,
  IWizardPageComponent,
  IWizardPageInjectedProps,
} from '@spinnaker/core';

export const GCE_SERVER_GROUP_OPERATION_MODES = ['create', 'clone', 'createPipeline', 'editPipeline'] as const;

export type GceServerGroupOperationMode = typeof GCE_SERVER_GROUP_OPERATION_MODES[number];
export type GceServerGroupLocationMode = 'zonal' | 'regional';

export interface IGceServerGroupCommandUpdateResult {
  dirty: Record<string, unknown>;
}

export interface IGceServerGroupCommand {
  application?: string;
  backingData?: any;
  capacity?: {
    desired?: number | null;
    max?: number | null;
    min?: number | null;
  };
  credentials?: string | null;
  distributionPolicy?: {
    targetShape?: string | null;
    zones?: string[];
  };
  freeFormDetails?: string | null;
  image?: string | null;
  processCommandUpdateResult?: (result: IGceServerGroupCommandUpdateResult) => void;
  region?: string | null;
  regional: boolean;
  selectZones?: boolean;
  stack?: string | null;
  viewState: {
    dirty?: Record<string, unknown>;
    disableImageSelection?: boolean;
    mode: GceServerGroupOperationMode;
    [key: string]: any;
  };
  zone?: string | null;
  [key: string]: any;
}

export interface IGceServerGroupCommandDefaults {
  account?: string;
  mode?: GceServerGroupOperationMode;
  region?: string;
  zone?: string;
}

export interface IGceServerGroupCommandBuilderAdapter {
  buildNewServerGroupCommand(
    application: Application,
    defaults?: IGceServerGroupCommandDefaults,
  ): Promise<IGceServerGroupCommand>;
  buildNewServerGroupCommandForPipeline(currentStage: IStage, pipeline: IPipeline): Promise<IGceServerGroupCommand>;
  buildServerGroupCommandFromExisting(
    application: Application,
    serverGroup: IServerGroup,
    mode?: GceServerGroupOperationMode,
  ): Promise<IGceServerGroupCommand>;
  buildServerGroupCommandFromPipeline(
    application: Application,
    originalCluster: Partial<IGceServerGroupCommand>,
    currentStage: IStage,
    pipeline: IPipeline,
  ): Promise<IGceServerGroupCommand>;
}

export type GceConfigurationUpdateMethod =
  | 'configureImages'
  | 'configureInstanceTypes'
  | 'configureLoadBalancerOptions'
  | 'configureSubnets'
  | 'configureZones';

export type GceConfigurationRefreshMethod =
  | 'refreshHealthChecks'
  | 'refreshInstanceTypes'
  | 'refreshLoadBalancers'
  | 'refreshSecurityGroups';

export type GceCommandHandlerName =
  | 'credentialsChanged'
  | 'customInstanceChanged'
  | 'networkChanged'
  | 'regionChanged'
  | 'regionalChanged'
  | 'selectZonesChanged'
  | 'zoneChanged';

export interface IGceServerGroupConfigurationAdapter {
  configureCommand(application: Application, command: IGceServerGroupCommand): Promise<void>;
  configureImages(command: IGceServerGroupCommand): IGceServerGroupCommandUpdateResult;
  configureInstanceTypes(command: IGceServerGroupCommand): IGceServerGroupCommandUpdateResult;
  configureLoadBalancerOptions(command: IGceServerGroupCommand): IGceServerGroupCommandUpdateResult;
  configureSubnets(command: IGceServerGroupCommand): IGceServerGroupCommandUpdateResult;
  configureZones(command: IGceServerGroupCommand): IGceServerGroupCommandUpdateResult;
  refreshHealthChecks(command: IGceServerGroupCommand, skipCommandReconfiguration?: boolean): Promise<void>;
  refreshInstanceTypes(command: IGceServerGroupCommand): Promise<void>;
  refreshLoadBalancers(command: IGceServerGroupCommand, skipCommandReconfiguration?: boolean): Promise<void>;
  refreshSecurityGroups(command: IGceServerGroupCommand, skipCommandReconfiguration?: boolean): Promise<void>;
}

export interface IGceServerGroupCommandUpdate {
  command: IGceServerGroupCommand;
  result: IGceServerGroupCommandUpdateResult;
}

export interface IGceServerGroupWizardAdapter {
  buildNewServerGroupCommand(
    application: Application,
    defaults?: IGceServerGroupCommandDefaults,
  ): Promise<IGceServerGroupCommand>;
  configureCommand(application: Application, command: IGceServerGroupCommand): Promise<IGceServerGroupCommand>;
  applyCommandHandler(
    command: IGceServerGroupCommand,
    handler: GceCommandHandlerName,
  ): Promise<IGceServerGroupCommandUpdate>;
  applyConfigurationUpdate(
    command: IGceServerGroupCommand,
    method: GceConfigurationUpdateMethod,
  ): Promise<IGceServerGroupCommandUpdate>;
  applyConfigurationRefresh(
    command: IGceServerGroupCommand,
    method: GceConfigurationRefreshMethod,
    skipCommandReconfiguration?: boolean,
  ): Promise<IGceServerGroupCommandUpdate>;
}

export interface IGceServerGroupWizardCommandState {
  command: IGceServerGroupCommand;
  fieldOrigins: Record<string, IGceServerGroupWizardFieldOrigin>;
  formikValues: IGceServerGroupCommand;
  requestGeneration: number;
}

export interface IGceServerGroupWizardFieldOrigin {
  generation: number;
  source: 'result' | 'user';
}

export interface IGceServerGroupWizardPageProps {
  app: Application;
  commandState?: IGceServerGroupWizardCommandState;
  formik: IWizardPageInjectedProps<IGceServerGroupCommand>['formik'];
  onLoadingChanged?: (isLoading: boolean) => void;
  adapter?: IGceServerGroupWizardAdapter;
}

export type GceServerGroupWizardPageContract = IWizardPageComponent<IGceServerGroupCommand>;

export interface IPersistedReference<T> {
  unresolved: boolean;
  value: T;
}

export interface IGceServerGroupCommandValidationErrors {
  application?: string;
  capacity?: { desired?: string };
  credentials?: string;
  distributionPolicy?: { zones?: string };
  freeFormDetails?: string;
  image?: string;
  region?: string;
  stack?: string;
  zone?: string;
}
