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

export interface IManagedApplicationSummary {
  applicationPaused: boolean;
  hasManagedResources: boolean;
  resources: IManagedResourceSummary[];
}

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
  apiVersion: string;
  kind: string;
  id: string;
  application: string;
  timestamp: string;
  plugin?: string;
  tasks?: Array<{ id: string; name: string }>;
  delta?: IManagedResourceDiff;
  message?: string;
  reason?: string;
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
