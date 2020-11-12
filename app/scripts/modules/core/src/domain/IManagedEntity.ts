import { IMoniker } from 'core/naming';

export enum ManagedResourceStatus {
  ACTUATING = 'ACTUATING',
  CREATED = 'CREATED',
  DIFF = 'DIFF',
  DIFF_NOT_ACTIONABLE = 'DIFF_NOT_ACTIONABLE',
  CURRENTLY_UNRESOLVABLE = 'CURRENTLY_UNRESOLVABLE',
  MISSING_DEPENDENCY = 'MISSING_DEPENDENCY',
  ERROR = 'ERROR',
  HAPPY = 'HAPPY',
  PAUSED = 'PAUSED',
  RESUMED = 'RESUMED',
  UNHAPPY = 'UNHAPPY',
  UNKNOWN = 'UNKNOWN',
}

export enum StatefulConstraintStatus {
  NOT_EVALUATED = 'NOT_EVALUATED',
  PENDING = 'PENDING',
  PASS = 'PASS',
  FAIL = 'FAIL',
  OVERRIDE_PASS = 'OVERRIDE_PASS',
  OVERRIDE_FAIL = 'OVERRIDE_FAIL',
}

export interface IStatefulConstraint {
  type: string;
  status: StatefulConstraintStatus;
  startedAt?: string;
  judgedAt?: string;
  judgedBy?: string;
  comment?: string;
}

export interface IDependsOnConstraint {
  type: 'depends-on';
  currentlyPassing: boolean;
  attributes: { environment: string };
}

// more stateless types coming soon
export type IStatelessConstraint = IDependsOnConstraint;

export interface IManagedResourceSummary {
  id: string;
  kind: string;
  status: ManagedResourceStatus;
  isPaused: boolean;
  displayName: string;
  locations: {
    account: string;
    regions: Array<{ name: string }>;
  };
  moniker?: IMoniker;
  artifact?: {
    name: string;
    type: string;
    reference: string;
  };
}

export interface IManagedEnvironmentSummary {
  name: string;
  resources: string[];
  artifacts: Array<{
    name: string;
    type: string;
    reference: string;
    statuses: string[];
    pinnedVersion?: string;
    versions: {
      current?: string;
      deploying?: string;
      pending: string[];
      approved: string[];
      previous: string[];
      vetoed: string[];
      skipped: string[];
    };
  }>;
}

export interface IManagedArtifactVersion {
  version: string;
  displayName: string;
  createdAt?: string;
  environments: Array<{
    name: string;
    state: 'current' | 'deploying' | 'approved' | 'pending' | 'previous' | 'vetoed' | 'skipped';
    pinned?: {
      at: string;
      by: string;
      comment?: string;
    };
    vetoed?: {
      at: string;
      by: string;
      comment?: string;
    };
    deployedAt?: string;
    replacedAt?: string;
    replacedBy?: string;
    statefulConstraints?: IStatefulConstraint[];
    statelessConstraints?: IStatelessConstraint[];
    compareLink?: string;
  }>;
  build?: {
    id: number; // deprecated, use number
    number: string;
    uid: string;
    job: {
      link: string;
      name: string;
    };
    startedAt: string;
    completedAt?: string;
    status: 'SUCCESS' | 'UNSTABLE' | 'BUILDING' | 'ABORTED' | 'FAILURE' | 'NOT_BUILT';
  };
  git?: {
    commit: string; // deprecated, use commitInfo
    author: string;
    project: string;
    branch: string;
    repo: {
      name: string;
      link: string;
    };
    pullRequest?: {
      number: string;
      url: string;
    };
    commitInfo: {
      sha: string;
      link: string;
      message: string;
    };
  };
}

export type IManagedArtifactVersionEnvironment = IManagedArtifactSummary['versions'][0]['environments'][0];

export interface IManagedArtifactSummary {
  name: string;
  type: string;
  reference: string;
  versions: IManagedArtifactVersion[];
}

interface IManagedApplicationEntities {
  resources: IManagedResourceSummary[];
  environments: IManagedEnvironmentSummary[];
  artifacts: IManagedArtifactSummary[];
}

export type IManagedApplicationEnvironmentSummary = IManagedApplicationSummary<
  'resources' | 'artifacts' | 'environments'
>;

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
