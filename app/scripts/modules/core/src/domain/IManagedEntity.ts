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
