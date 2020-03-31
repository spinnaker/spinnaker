import React from 'react';

import { IStatefulConstraint, StatefulConstraintStatus } from '../../domain';
import { NoticeCard } from '../NoticeCard';

import { isConstraintSupported, getStatefulConstraintConfig } from './constraintRegistry';

const { NOT_EVALUATED, PENDING, PASS, FAIL, OVERRIDE_PASS, OVERRIDE_FAIL } = StatefulConstraintStatus;

const constraintCardAppearanceByStatus = {
  [NOT_EVALUATED]: 'neutral',
  [PENDING]: 'info',
  [PASS]: 'success',
  [FAIL]: 'error',
  [OVERRIDE_PASS]: 'success',
  [OVERRIDE_FAIL]: 'error',
} as const;

const throwUnsupportedConstraintError = (type: string) => {
  throw new Error(`Unsupported constraint type ${type} — did you check for constraint support before rendering?`);
};

export interface IConstraintCardProps {
  constraint: IStatefulConstraint;
  className?: string;
}

export const ConstraintCard = ({ constraint, className }: IConstraintCardProps) => {
  const { type, status } = constraint;

  if (!isConstraintSupported(type)) {
    throwUnsupportedConstraintError(type);
  }

  const { iconName, shortSummary } = getStatefulConstraintConfig(type);

  return (
    <NoticeCard
      className={className}
      icon={iconName}
      text={undefined}
      title={shortSummary(constraint)}
      isActive={true}
      noticeType={constraintCardAppearanceByStatus[status]}
    />
  );
};
