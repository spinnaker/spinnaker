import React from 'react';
import { DateTime } from 'luxon';

import { relativeTime, timestamp } from '../../utils';
import { IStatefulConstraint, StatefulConstraintStatus, IStatelessConstraint } from '../../domain';
import { IconNames } from '../../presentation';

const NO_FAILURE_MESSAGE = 'no details available';
const UNKNOWN_CONSTRAINT_ICON = 'mdConstraintGeneric';

const throwUnhandledStatusError = (status: string) => {
  throw new Error(`Unhandled constraint status "${status}", no constraint summary available`);
};

const { NOT_EVALUATED, PENDING, FAIL, OVERRIDE_PASS, OVERRIDE_FAIL } = StatefulConstraintStatus;

export interface IConstraintOverrideAction {
  title: string;
  pass: boolean;
}

interface IStatefulConstraintConfig {
  iconName: IconNames;
  shortSummary: (constraint: IStatefulConstraint) => React.ReactNode;
  overrideActions: { [status in StatefulConstraintStatus]?: IConstraintOverrideAction[] };
}

interface IStatelessConstraintConfig {
  iconName: IconNames;
  shortSummary: {
    pass: (constraint: IStatelessConstraint) => React.ReactNode;
    fail: (constraint: IStatelessConstraint) => React.ReactNode;
  };
}

export const isConstraintSupported = (type: string) =>
  statefulConstraintOptionsByType.hasOwnProperty(type) || statelessConstraintOptionsByType.hasOwnProperty(type);

export const isConstraintStateful = (constraint: IStatefulConstraint | IStatelessConstraint) =>
  statefulConstraintOptionsByType.hasOwnProperty(constraint.type);

export const getConstraintIcon = (type: string) =>
  (statefulConstraintOptionsByType[type]?.iconName || statelessConstraintOptionsByType[type]?.iconName) ??
  UNKNOWN_CONSTRAINT_ICON;

export const getConstraintActions = (constraint: IStatefulConstraint | IStatelessConstraint) => {
  if (!isConstraintStateful(constraint)) {
    return null;
  }

  const { type, status } = constraint as IStatefulConstraint;
  return statefulConstraintOptionsByType[type]?.overrideActions?.[status] ?? null;
};

export const getConstraintSummary = (constraint: IStatefulConstraint | IStatelessConstraint) => {
  if (isConstraintStateful(constraint)) {
    return getStatefulConstraintSummary(constraint as IStatefulConstraint);
  } else {
    return getStatelessConstraintSummary(constraint as IStatelessConstraint);
  }
};

const getStatefulConstraintSummary = (constraint: IStatefulConstraint) =>
  statefulConstraintOptionsByType[constraint.type]?.shortSummary(constraint) ?? null;

const getStatelessConstraintSummary = (constraint: IStatelessConstraint) => {
  const { pass, fail } = statelessConstraintOptionsByType[constraint.type]?.shortSummary ?? {};
  return (constraint.currentlyPassing ? pass?.(constraint) : fail?.(constraint)) ?? null;
};

// Later, this will become a "proper" registry so we can separate configs
// into their own files and extend them dynamically at runtime.
// For now let's get more of the details settled and iterated on.
const statefulConstraintOptionsByType: { [type: string]: IStatefulConstraintConfig } = {
  'manual-judgement': {
    iconName: 'manualJudgement',
    shortSummary: ({ status, judgedAt, judgedBy, startedAt, comment }: IStatefulConstraint) => {
      const startedAtMillis = DateTime.fromISO(startedAt).toMillis();
      const judgedAtMillis = DateTime.fromISO(judgedAt).toMillis();

      switch (status) {
        case NOT_EVALUATED:
          return 'Manual judgement will be required before promotion';
        case PENDING:
          return (
            <span>
              Awaiting manual judgement since {relativeTime(startedAtMillis)}{' '}
              <span className="text-italic text-regular sp-margin-xs-left">({timestamp(startedAtMillis)})</span>
            </span>
          );
        case OVERRIDE_PASS:
        case OVERRIDE_FAIL:
          return (
            <span className="sp-group-margin-xs-xaxis">
              Manually {status === OVERRIDE_PASS ? 'approved' : 'rejected'} {relativeTime(judgedAtMillis)}{' '}
              <span className="text-italic text-regular sp-margin-xs-left">({timestamp(judgedAtMillis)})</span>{' '}
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
    },
  },
};
