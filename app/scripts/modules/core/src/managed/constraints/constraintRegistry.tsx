import { DateTime } from 'luxon';
import React from 'react';

import { IconNames } from '@spinnaker/presentation';

import { DeploymentWindow } from '../artifactDetail/constraints/DeploymentWindow';
import {
  ConstraintStatus,
  IAllowedTimesConstraint,
  IConstraint,
  IDependsOnConstraint,
  IManagedArtifactVersionEnvironment,
} from '../../domain';

const NO_FAILURE_MESSAGE = 'no details available';
const UNKNOWN_CONSTRAINT_ICON = 'mdConstraintGeneric';

const constraintHasNotStarted: ConstraintStatus[] = ['PENDING', 'NOT_EVALUATED'];

export interface IConstraintOverrideAction {
  title: string;
  pass: boolean;
}

export const hasSkippedConstraint = (constraint: IConstraint, environment: IManagedArtifactVersionEnvironment) =>
  environment.state === 'skipped' && constraintHasNotStarted.includes(constraint.status);

interface IConstraintRenderer {
  iconName: { [status in ConstraintStatus | 'DEFAULT']?: IconNames };
  renderFn: (constraint: IConstraint) => React.ReactNode;
  overrideActions?: { [status in ConstraintStatus]?: IConstraintOverrideAction[] };
}

export const getConstraintIcon = (constraint: IConstraint) => {
  const iconsByStatus = constraintsRenderer[constraint.type]?.iconName;
  return iconsByStatus?.[constraint.status] || iconsByStatus?.['DEFAULT'] || UNKNOWN_CONSTRAINT_ICON;
};

export const renderConstraint = (constraint: IConstraint): React.ReactNode => {
  const renderFn = constraintsRenderer[constraint.type]?.renderFn;
  return renderFn?.(constraint) || `${constraint.type} constraint - ${constraint.status}`;
};

export const getConstraintTimestamp = (constraint: IConstraint, environment: IManagedArtifactVersionEnvironment) => {
  const { startedAt, judgedAt } = constraint;

  // PENDING and NOT_EVALUATED constraints stop running once an environment is skipped, however, their status do not change.
  // We need to ignore them
  if (hasSkippedConstraint(constraint, environment)) {
    return undefined;
  }
  const finalTime = judgedAt ?? startedAt;
  return finalTime ? DateTime.fromISO(finalTime) : undefined;
};

export const getConstraintActions = (constraint: IConstraint, environment: IManagedArtifactVersionEnvironment) => {
  if (environment.state === 'skipped') {
    return undefined;
  }
  const actions = constraintsRenderer[constraint.type]?.overrideActions;
  return actions?.[constraint.status];
};

export const isConstraintSupported = (type: string) => {
  return constraintsRenderer.hasOwnProperty(type);
};

// Later, this will become a "proper" registry so we can separate configs
// into their own files and extend them dynamically at runtime.
// For now let's get more of the details settled and iterated on.
const constraintsRenderer: { [type in IConstraint['type']]?: IConstraintRenderer } = {
  'allowed-times': {
    iconName: { DEFAULT: 'mdConstraintAllowedTimes' },
    renderFn: (constraint: IAllowedTimesConstraint) => {
      const windows = constraint.attributes.allowedTimes;
      switch (constraint.status) {
        case 'FAIL':
          return 'Failed to deploy within the allowed';
        case 'OVERRIDE_PASS':
          return 'Deployment window constraint was overridden';
        case 'PASS':
          return (
            <div>
              Deployed during one of the previous open windows:
              <DeploymentWindow windows={windows} />
            </div>
          );
        case 'PENDING':
          return (
            <div>
              Deployment can only occur during the following window{windows.length > 1 ? 's' : ''}:
              <DeploymentWindow windows={windows} />
            </div>
          );
        default:
          return (
            <div>
              Allowed times constraint - {constraint.status}:
              <DeploymentWindow windows={windows} />
            </div>
          );
      }
    },
  },
  'depends-on': {
    iconName: { DEFAULT: 'mdConstraintDependsOn' },
    renderFn: (constraint: IDependsOnConstraint) => {
      const prerequisiteEnv = constraint.attributes.dependsOnEnvironment.toUpperCase();
      switch (constraint.status) {
        case 'PASS':
        case 'OVERRIDE_PASS':
          return `Deployed to prerequisite environment (${prerequisiteEnv}) successfully`;
        case 'FAIL':
          return `Prerequisite environment (${prerequisiteEnv}) deployment failed`;
        case 'OVERRIDE_FAIL':
          return `Overriding prerequisite environment (${prerequisiteEnv}) deployment failure`;
        default:
          return `Awaiting deployment to prerequisite environment (${prerequisiteEnv})`;
      }
    },
  },
  'manual-judgement': {
    iconName: {
      PASS: 'manualJudgementApproved',
      OVERRIDE_PASS: 'manualJudgementApproved',
      FAIL: 'manualJudgementRejected',
      OVERRIDE_FAIL: 'manualJudgementRejected',
      DEFAULT: 'manualJudgement',
    },
    renderFn: (constraint: IConstraint) => {
      switch (constraint.status) {
        case 'NOT_EVALUATED':
          return 'Manual judgement will be required before promotion';
        case 'PENDING':
          return 'Awaiting manual judgement';
        case 'OVERRIDE_PASS':
        case 'OVERRIDE_FAIL':
          return (
            <span>
              <span>Manually {constraint.status === 'OVERRIDE_PASS' ? 'approved' : 'rejected'}</span>{' '}
              <span className="text-regular">—</span> <span className="text-regular">by {constraint.judgedBy}</span>
            </span>
          );
        case 'FAIL':
          return (
            <span>
              Something went wrong <span className="text-regular">—</span>{' '}
              <span className="text-regular">{constraint.comment || NO_FAILURE_MESSAGE}</span>
            </span>
          );
        default:
          console.error(new Error(`Unrecognized constraint status - ${JSON.stringify(constraint)}`));
          return `${constraint.type} constraint - ${constraint.status}`;
      }
    },
    overrideActions: {
      PENDING: [
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
