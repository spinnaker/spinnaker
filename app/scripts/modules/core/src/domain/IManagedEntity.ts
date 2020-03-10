import { IMoniker } from 'core/naming';

export enum ManagedResourceStatus {
  ACTUATING = 'ACTUATING',
  CREATED = 'CREATED',
  DIFF = 'DIFF',
  ERROR = 'ERROR',
  HAPPY = 'HAPPY',
  PAUSED = 'PAUSED',
  RESUMED = 'RESUMED',
  UNHAPPY = 'UNHAPPY',
  UNKNOWN = 'UNKNOWN',
}

export interface IManagedResourceSummary {
  id: string;
  kind: string;
  status: ManagedResourceStatus;
  isPaused: boolean;
  moniker: IMoniker;
  locations: {
    account: string;
    regions: Array<{ name: string }>;
  };
}

export interface IManagedEnviromentSummary {
  name: string;
  resources: string[];
  artifacts: Array<{
    name: string;
    type: string;
    statuses: string[];
    versions: {
      current?: string;
      deploying?: string;
      pending: string[];
      approved: string[];
      previous: string[];
      vetoed: string[];
    };
  }>;
}

export interface IManagedArtifactVersion {
  version: string;
  environments: Array<{
    name: string;
    state: string;
    deployedAt?: string;
    replacedAt?: string;
    replacedBy?: string;
  }>;
}

export interface IManagedArtifactSummary {
  name: string;
  type: string;
  versions: IManagedArtifactVersion[];
}

export interface IManagedApplicationEnvironmentsSummary extends IManagedApplicationSummary {
  environments: IManagedEnviromentSummary[];
  artifacts: IManagedArtifactSummary[];
}

export interface IManagedApplicationEntities {
  resources: IManagedResourceSummary[];
  environments: IManagedEnviromentSummary[];
  artifacts: IManagedArtifactSummary[];
}

export type IManagedApplicationSummary<T extends keyof IManagedApplicationEntities = 'resources'> = Pick<
  IManagedApplicationEntities,
  T
> & {
  applicationPaused: boolean;
  hasManagedResources: boolean;
};

export interface IManagedResource {
  managedResourceSummary?: IManagedResourceSummary;
  isManaged?: boolean;
}

export enum ManagedResourceEventType {
  ResourceCreated = 'ResourceCreated',
  ResourceUpdated = 'ResourceUpdated',
  ResourceDeleted = 'ResourceDeleted',
  ResourceMissing = 'ResourceMissing',
  ResourceValid = 'ResourceValid',
  ResourceDeltaDetected = 'ResourceDeltaDetected',
  ResourceDeltaResolved = 'ResourceDeltaResolved',
  ResourceActuationLaunched = 'ResourceActuationLaunched',
  ResourceCheckError = 'ResourceCheckError',
  ResourceCheckUnresolvable = 'ResourceCheckUnresolvable',
  ResourceActuationPaused = 'ResourceActuationPaused',
  ResourceActuationResumed = 'ResourceActuationResumed',
}

export interface IManagedResourceDiff {
  [fieldName: string]: {
    key: string;
    diffType: 'CHANGED' | 'ADDED' | 'REMOVED';
    desired?: string;
    actual?: string;
    fields: IManagedResourceDiff;
  };
}

export interface IManagedResourceEvent {
  type: ManagedResourceEventType;
  kind: string;
  id: string;
  application: string;
  timestamp: string;
  plugin?: string;
  tasks?: Array<{ id: string; name: string }>;
  delta?: IManagedResourceDiff;
  // We really should not have 3 different versions of basically
  // the same field, but right now we do.
  message?: string;
  reason?: string;
  exceptionMessage?: string;
}

export type IManagedResourceEventHistory = IManagedResourceEvent[];

export type IManagedResourceEventHistoryResponse = Array<
  Omit<IManagedResourceEvent, 'delta'> & {
    delta?: {
      [key: string]: {
        state: 'CHANGED' | 'ADDED' | 'REMOVED';
        desired: string;
        current: string;
      };
    };
  }
>;
