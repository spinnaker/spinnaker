import type { IConstraint, IDependsOnConstraint } from '../../domain';

export const isDependsOnConstraint = (constraint: IConstraint): constraint is IDependsOnConstraint => {
  return constraint.type === 'depends-on';
};

export const getDependsOnStatus = ({ constraint }: { constraint: IDependsOnConstraint }): string => {
  const prerequisiteEnv = constraint.attributes.dependsOnEnvironment.toUpperCase();
  switch (constraint.status) {
    case 'PASS':
      return `deployment to ${prerequisiteEnv} succeeded`;
    case 'FORCE_PASS':
      return `deployment to ${prerequisiteEnv} was overridden`;
    case 'FAIL':
      return `deployment to ${prerequisiteEnv} failed`;
    case 'PENDING':
      return `awaiting deployment to ${prerequisiteEnv}`;
    default:
      return `Environment: ${prerequisiteEnv}, status: ${constraint.status}`;
  }
};
