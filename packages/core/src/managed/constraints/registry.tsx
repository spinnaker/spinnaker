import { isEmpty } from 'lodash';
import { DateTime } from 'luxon';
import React from 'react';

import { IconNames } from '@spinnaker/presentation';

import { AllowedTimesDescription, AllowedTimesTitle } from './AllowedTimes';
import { DependsOnTitle } from './DependsOn';
import { ManualJudgementTitle } from './ManualJudgement';
import { ConstraintStatus, IBaseConstraint, IConstraint, IManagedArtifactVersionEnvironment } from '../../domain';
import { BasePluginManager } from '../plugins/BasePluginManager';

const UNKNOWN_CONSTRAINT_ICON = 'mdConstraintGeneric';

const constraintHasNotStarted: ConstraintStatus[] = ['PENDING', 'NOT_EVALUATED'];
const constraintBlocked: ConstraintStatus[] = ['BLOCKED', 'NOT_EVALUATED'];
interface IConstraintOverrideAction {
  title: string;
  pass: boolean;
}

export const hasSkippedConstraint = (constraint: IConstraint, environment: IManagedArtifactVersionEnvironment) =>
  environment.state === 'skipped' && constraintHasNotStarted.includes(constraint.status);

type RelaxedConstraint = IBaseConstraint | IConstraint;
export interface IConstraintHandler<K = string> {
  /** The type of the constraint - versioning is supported by adding @{version}, e.g. myConstraint@ */
  kind: K;
  /** The icon can be a string (from IconNames) or a partial map from statuses to IconNames */
  iconName: IconNames | { [status in ConstraintStatus | 'DEFAULT']?: IconNames };

  /** Render function of the constraint title */
  titleRender: React.ComponentType<{ constraint: RelaxedConstraint }>;

  /** Optional render function of the constraint description */
  descriptionRender?: React.ComponentType<{ constraint: RelaxedConstraint }>;

  /** Display actions to override the constraint - (fail or pass) */
  overrideActions?: { [status in ConstraintStatus]?: IConstraintOverrideAction[] };
}

class ConstraintsManager extends BasePluginManager<IConstraintHandler> {
  getIcon(constraint: IConstraint | IBaseConstraint) {
    const iconName = this.getHandler(constraint.type)?.iconName;
    if (typeof iconName === 'string') {
      return iconName;
    }
    return iconName?.[constraint.status] || iconName?.['DEFAULT'] || UNKNOWN_CONSTRAINT_ICON;
  }

  renderTitle(constraint: IConstraint): React.ReactNode {
    const Component = this.getHandler(constraint.type)?.titleRender;
    if (Component) {
      return <Component constraint={constraint} />;
    }
    return `${constraint.type} constraint - ${constraint.status}`;
  }

  hasContent(constraint: IConstraint): boolean {
    const overrideActions = this.getActions(constraint);
    return Boolean(this.getHandler(constraint.type)?.descriptionRender) || !isEmpty(overrideActions);
  }

  renderDescription(constraint: IConstraint): React.ReactNode {
    const Component = this.getHandler(constraint.type)?.descriptionRender;
    if (Component) {
      return <Component constraint={constraint} />;
    }
    return null;
  }

  getTimestamp(constraint: IConstraint, environment: IManagedArtifactVersionEnvironment) {
    const { startedAt, judgedAt } = constraint;

    // PENDING and NOT_EVALUATED constraints stop running once an environment is skipped, however, their status do not change.
    // We need to ignore them
    if (hasSkippedConstraint(constraint, environment)) {
      return undefined;
    }
    const finalTime = judgedAt ?? startedAt;
    return finalTime ? DateTime.fromISO(finalTime) : undefined;
  }

  getActions(constraint: IConstraint, environmentState?: IManagedArtifactVersionEnvironment['state']) {
    if (environmentState === 'skipped' || constraintBlocked.includes(constraint.status)) {
      return undefined;
    }
    const actions = this.getHandler(constraint.type)?.overrideActions;
    return actions?.[constraint.status];
  }
}

const baseHandlers: Array<IConstraintHandler<IConstraint['type']>> = [
  {
    kind: 'allowed-times',
    iconName: { DEFAULT: 'mdConstraintAllowedTimes' },
    titleRender: AllowedTimesTitle,
    descriptionRender: AllowedTimesDescription,
    overrideActions: {
      FAIL: [
        {
          title: 'Skip constraint',
          pass: true,
        },
      ],
    },
  },
  {
    kind: 'depends-on',
    iconName: { DEFAULT: 'mdConstraintDependsOn' },
    titleRender: DependsOnTitle,
  },
  {
    kind: 'manual-judgement',
    iconName: {
      PASS: 'manualJudgementApproved',
      OVERRIDE_PASS: 'manualJudgementApproved',
      FAIL: 'manualJudgementRejected',
      OVERRIDE_FAIL: 'manualJudgementRejected',
      DEFAULT: 'manualJudgement',
    },
    titleRender: ManualJudgementTitle,
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
];

export const constraintsManager = new ConstraintsManager(baseHandlers);
