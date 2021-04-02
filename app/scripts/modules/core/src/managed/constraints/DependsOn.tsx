import React from 'react';

import { IConstraint, IDependsOnConstraint } from 'core/domain';

export const isDependsOnConstraint = (constraint: IConstraint): constraint is IDependsOnConstraint => {
  return constraint.type === 'depends-on';
};

const getTitle = (constraint: IDependsOnConstraint) => {
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
};

export const DependsOnTitle = ({ constraint }: { constraint: IDependsOnConstraint }) => {
  return <>{getTitle(constraint)}</>;
};
