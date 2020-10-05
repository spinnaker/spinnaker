import React from 'react';
import { DateTime } from 'luxon';

import {
  IStatefulConstraint,
  StatefulConstraintStatus,
  IStatelessConstraint,
  IManagedArtifactVersionEnvironment,
} from '../../domain';
import { IconNames } from '../../presentation';

const NO_FAILURE_MESSAGE = 'no details available';
const UNKNOWN_CONSTRAINT_ICON = 'mdConstraintGeneric';

const throwUnhandledStatusError = (status: string) => {
  throw new Error(`Unhandled constraint status "${status}", no constraint summary available`);
};

const { NOT_EVALUATED, PENDING, PASS, FAIL, OVERRIDE_PASS, OVERRIDE_FAIL } = StatefulConstraintStatus;

export interface IConstraintOverrideAction {
  title: string;
  pass: boolean;
}

interface IStatefulConstraintConfig {
  iconName: { [status in StatefulConstraintStatus | 'DEFAULT']?: IconNames };
  shortSummary: (constraint: IStatefulConstraint, environment: IManagedArtifactVersionEnvironment) => React.ReactNode;
  overrideActions: { [status in StatefulConstraintStatus]?: IConstraintOverrideAction[] };
}

interface IStatelessConstraintConfig {
  iconName: IconNames;
  shortSummary: {
    pass: (constraint: IStatelessConstraint) => React.ReactNode;
    fail: (constraint: IStatelessConstraint) => React.ReactNode;
    skipped: (constraint: IStatelessConstraint) => React.ReactNode;
  };
}

export const isConstraintSupported = (type: string) =>
  statefulConstraintOptionsByType.hasOwnProperty(type) || statelessConstraintOptionsByType.hasOwnProperty(type);

export const isConstraintStateful = (constraint: IStatefulConstraint | IStatelessConstraint) =>
  statefulConstraintOptionsByType.hasOwnProperty(constraint.type);

export const getConstraintIcon = (constraint: IStatefulConstraint | IStatelessConstraint) => {
  if (!isConstraintStateful(constraint)) {
    return statelessConstraintOptionsByType[constraint.type]?.iconName ?? UNKNOWN_CONSTRAINT_ICON;
  }

  const { type, status } = constraint as IStatefulConstraint;
  const iconNamesByStatus = statefulConstraintOptionsByType[type]?.iconName;

  return iconNamesByStatus?.[status] || iconNamesByStatus?.['DEFAULT'] || UNKNOWN_CONSTRAINT_ICON;
};

export const getConstraintTimestamp = (
  constraint: IStatefulConstraint | IStatelessConstraint,
  environment: IManagedArtifactVersionEnvironment,
) => {
  if (!isConstraintStateful(constraint)) {
    return null;
  }

  const { status, startedAt, judgedAt } = constraint as IStatefulConstraint;

  if (environment.state === 'skipped' && [PENDING, NOT_EVALUATED].includes(status)) {
    return null;
  }

  switch (status) {
    case PENDING:
      return startedAt ? DateTime.fromISO(startedAt) : null;

    case PASS:
    case FAIL:
    case OVERRIDE_PASS:
    case OVERRIDE_FAIL:
      return judgedAt ? DateTime.fromISO(judgedAt) : null;

    default:
      return null;
  }
};

export const getConstraintActions = (
  constraint: IStatefulConstraint | IStatelessConstraint,
  environment: IManagedArtifactVersionEnvironment,
) => {
  if (!isConstraintStateful(constraint) || environment.state === 'skipped') {
    return null;
  }

  const { type, status } = constraint as IStatefulConstraint;
  return statefulConstraintOptionsByType[type]?.overrideActions?.[status] ?? null;
};

export const getConstraintSummary = (
  constraint: IStatefulConstraint | IStatelessConstraint,
  environment: IManagedArtifactVersionEnvironment,
) => {
  if (isConstraintStateful(constraint)) {
    return getStatefulConstraintSummary(constraint as IStatefulConstraint, environment);
  } else {
    return getStatelessConstraintSummary(constraint as IStatelessConstraint, environment);
  }
};

const getStatefulConstraintSummary = (
  constraint: IStatefulConstraint,
  environment: IManagedArtifactVersionEnvironment,
) => statefulConstraintOptionsByType[constraint.type]?.shortSummary(constraint, environment) ?? null;

const getStatelessConstraintSummary = (
  constraint: IStatelessConstraint,
  environment: IManagedArtifactVersionEnvironment,
) => {
  const { pass, fail, skipped } = statelessConstraintOptionsByType[constraint.type]?.shortSummary ?? {};
  if (environment.state === 'skipped') {
    return skipped(constraint);
  } else {
    return (constraint.currentlyPassing ? pass?.(constraint) : fail?.(constraint)) ?? null;
  }
};

// Later, this will become a "proper" registry so we can separate configs
// into their own files and extend them dynamically at runtime.
// For now let's get more of the details settled and iterated on.
const statefulConstraintOptionsByType: { [type: string]: IStatefulConstraintConfig } = {
  'manual-judgement': {
    iconName: {
      [PASS]: 'manualJudgementApproved',
      [OVERRIDE_PASS]: 'manualJudgementApproved',
      [FAIL]: 'manualJudgementRejected',
      [OVERRIDE_FAIL]: 'manualJudgementRejected',
      DEFAULT: 'manualJudgement',
    },
    shortSummary: (
      { status, judgedBy, comment }: IStatefulConstraint,
      environment: IManagedArtifactVersionEnvironment,
    ) => {
      if (environment.state === 'skipped' && [PENDING, NOT_EVALUATED].includes(status)) {
        return 'Manual judgement skipped';
      }

      switch (status) {
        case NOT_EVALUATED:
          return 'Manual judgement will be required before promotion';
        case PENDING:
          return 'Awaiting manual judgement';
        case OVERRIDE_PASS:
        case OVERRIDE_FAIL:
          return (
            <span className="sp-group-margin-xs-xaxis">
              <span>Manually {status === OVERRIDE_PASS ? 'approved' : 'rejected'}</span>{' '}
              <span className="text-regular">—</span> <span className="text-regular">by {judgedBy}</span>
            </span>
          );
        case FAIL:
          return (
            <span className="sp-group-margin-xs-xaxis">
              Something went wrong <span className="text-regular">—</span>{' '}
              <span className="text-regular">{comment || NO_FAILURE_MESSAGE}</span>
            </span>
          );
        default:
          return throwUnhandledStatusError(status);
      }
    },
    overrideActions: {
      [PENDING]: [
        {
          title: 'Reject',
          pass: false,
        },
        {
          title: 'Approve',
          pass: true,
        },
      ],
    },
  },
};

const statelessConstraintOptionsByType: { [type: string]: IStatelessConstraintConfig } = {
  'depends-on': {
    iconName: 'mdConstraintDependsOn',
    shortSummary: {
      pass: ({ attributes: { environment } }) =>
        `Already deployed to prerequisite environment ${environment?.toUpperCase()}`,
      fail: ({ attributes: { environment } }) =>
        `Deployment to ${environment?.toUpperCase()} will be required before promotion`,
      skipped: ({ currentlyPassing, attributes: { environment } }) =>
        currentlyPassing
          ? `Already deployed to prerequisite environment ${environment?.toUpperCase()}`
          : `Skipped check for prerequisite environment ${environment?.toUpperCase()}`,
    },
  },
};
