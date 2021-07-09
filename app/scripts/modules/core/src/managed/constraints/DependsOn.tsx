import React from 'react';

import { IConstraint, IDependsOnConstraint } from '../../domain';

export const isDependsOnConstraint = (constraint: IConstraint): constraint is IDependsOnConstraint => {
  return constraint.type === 'depends-on';
};

const getTitle = (constraint: IDependsOnConstraint) => {
  const prerequisiteEnv = constraint.attributes.dependsOnEnvironment.toUpperCase();
  switch (constraint.status) {
    case 'PASS':
      return `Prerequisite deployment to ${prerequisiteEnv} succeeded`;
    case 'FORCE_PASS':
      return `Prerequisite deployment to ${prerequisiteEnv} was overridden`;
    case 'FAIL':
      return `Prerequisite deployment to ${prerequisiteEnv} failed`;
    case 'PENDING':
      return `Awaiting prerequisite deployment to ${prerequisiteEnv}`;
    default:
      return `Depends on ${prerequisiteEnv} - ${constraint.status}`;
  }
};

export const DependsOnTitle = ({ constraint }: { constraint: IDependsOnConstraint }) => {
  return <>{getTitle(constraint)}</>;
};
