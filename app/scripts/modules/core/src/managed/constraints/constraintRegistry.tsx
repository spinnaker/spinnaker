import React from 'react';
import { DateTime } from 'luxon';

import { relativeTime, timestamp } from '../../utils';
import { IStatefulConstraint, StatefulConstraintStatus } from '../../domain';
import { IconNames } from '../../presentation';

const NO_FAILURE_MESSAGE = 'no details available';

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

export const isConstraintSupported = (type: string) => statefulConstraintOptionsByType.hasOwnProperty(type);
export const getStatefulConstraintConfig = (type: string) => statefulConstraintOptionsByType[type];
export const getStatefulConstraintActions = ({ type, status }: IStatefulConstraint) =>
  statefulConstraintOptionsByType[type]?.overrideActions?.[status] ?? null;

// Later, this will become a "proper" registry so we can separate configs
// into their own files and extend them dynamically at runtime.
// For now let's get more of the details settled and iterated on.
export const statefulConstraintOptionsByType: { [type: string]: IStatefulConstraintConfig } = {
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
