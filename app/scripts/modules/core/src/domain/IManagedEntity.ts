import { MdConstraintStatus } from '../managed/graphql/graphql-sdk';
import { IMoniker } from '../naming';

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
  WAITING = 'WAITING',
}

type DeprecatedStatus = 'OVERRIDE_PASS' | 'OVERRIDE_FAIL' | 'NOT_EVALUATED'; // will be removed in future versions
export type ConstraintStatus = DeprecatedStatus | MdConstraintStatus;

// Warning! Chaning this interface might affect existing plugins. Please make sure you don't break the API
export interface IBaseConstraint {
  type: string;
  status: ConstraintStatus;
  startedAt?: string;
  judgedAt?: string;
  judgedBy?: string;
  comment?: string;
}

export interface IDependsOnConstraint extends IBaseConstraint {
  type: 'depends-on';
  attributes: { dependsOnEnvironment: string };
}

export interface AllowedTimeWindow {
  days: number[];
  hours: number[];
}
export interface IAllowedTimesConstraint extends IBaseConstraint {
  type: 'allowed-times';
  attributes: { allowedTimes: AllowedTimeWindow[]; timezone?: string } | null;
}

export interface IManualJudgementConstraint extends IBaseConstraint {
  type: 'manual-judgement';
}

export type IConstraint = IBaseConstraint | IManualJudgementConstraint | IDependsOnConstraint | IAllowedTimesConstraint;

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

export interface IVerification {
  id: string;
  type: string;
  status: 'NOT_EVALUATED' | 'PENDING' | 'PASS' | 'FAIL' | 'OVERRIDE_PASS' | 'OVERRIDE_FAIL';
  startedAt?: string;
  completedAt?: string;
  link?: string;
}

export interface IPinned {
  at: string;
  by: string;
  comment?: string;
}

export interface IManagedArtifactVersionEnvironment {
  name: string;
  state: 'current' | 'deploying' | 'approved' | 'pending' | 'previous' | 'vetoed' | 'skipped';
  pinned?: IPinned;
  vetoed?: {
    at: string;
    by: string;
    comment?: string;
  };
  deployedAt?: string;
  replacedAt?: string;
  replacedBy?: string;
  constraints?: IConstraint[];
  compareLink?: string;
  verifications?: IVerification[];
}

export interface IManagedArtifactVersionLifecycleStep {
  // likely more scopes + types later, but hard-coding to avoid premature abstraction for now
  scope: 'PRE_DEPLOYMENT';
  type: 'BUILD' | 'BAKE';
  id: string;
  status: 'NOT_STARTED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'ABORTED' | 'UNKNOWN';
  startedAt?: string;
  completedAt?: string;
  link?: string;
}

export interface IManagedArtifactVersion {
  version: string;
  displayName: string;
  createdAt?: string;
  environments: IManagedArtifactVersionEnvironment[];
  lifecycleSteps?: IManagedArtifactVersionLifecycleStep[];
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

export type ManagedResourceEventType =
  | 'ResourceCreated'
  | 'ResourceUpdated'
  | 'ResourceDeleted'
  | 'ResourceMissing'
  | 'ResourceValid'
  | 'ResourceDeltaDetected'
  | 'ResourceDeltaResolved'
  | 'ResourceActuationLaunched'
  | 'ResourceCheckError'
  | 'ResourceCheckUnresolvable'
  | 'ResourceActuationPaused'
  | 'ResourceActuationVetoed'
  | 'ResourceActuationResumed';

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
  displayName: string;
  level: 'SUCCESS' | 'INFO' | 'WARNING' | 'ERROR';
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
