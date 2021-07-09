import { isDependsOnConstraint } from '../constraints/DependsOn';
import { IManagedApplicationEnvironmentSummary, IManagedArtifactVersionEnvironment } from '../../domain';

const isEnvDependsOn = (
  env: IManagedArtifactVersionEnvironment,
  maybeDependsOnEnv: IManagedArtifactVersionEnvironment,
) => {
  return env.constraints?.some(
    (constraint) =>
      isDependsOnConstraint(constraint) && constraint.attributes.dependsOnEnvironment === maybeDependsOnEnv.name,
  );
};

const envHasAnyDependsOn = (env: IManagedArtifactVersionEnvironment) => {
  return env.constraints?.some((constraint) => constraint.type === 'depends-on');
};

const compareEnvironments = (a: IManagedArtifactVersionEnvironment, b: IManagedArtifactVersionEnvironment) => {
  if (isEnvDependsOn(a, b)) {
    return -1;
  } else if (isEnvDependsOn(b, a)) {
    return 1;
  }
  // Prioritize if has any depends on
  const aHasDependsOn = envHasAnyDependsOn(a);
  const bHasDependsOn = envHasAnyDependsOn(b);
  if (aHasDependsOn && bHasDependsOn) return 0;
  if (aHasDependsOn) return -1;
  if (bHasDependsOn) return 1;
  return 0;
};

export const sortEnvironments = (response: IManagedApplicationEnvironmentSummary) => {
  // Sort environment by depends-on constraint. Environments with that constraint should appear first.
  try {
    const allEnvironments: { [env: string]: IManagedArtifactVersionEnvironment } = {};

    response.artifacts.forEach((artifact) => {
      artifact.versions.forEach((version) => {
        // This will be used for sorting the environments later.
        // We pick the first occurence of each environment, could be risky once we have versioned environments
        version.environments.forEach((env) => {
          if (!(env.name in allEnvironments)) {
            allEnvironments[env.name] = env;
          }
        });
        version.environments.sort(compareEnvironments);
      });
    });

    response.environments.sort((a, b) => {
      if (!(a.name in allEnvironments)) return 1; // This should not happen
      if (!(b.name in allEnvironments)) return -1; // This should not happen
      return compareEnvironments(allEnvironments[a.name], allEnvironments[b.name]);
    });
  } catch (e) {
    console.error('Failed to sort environments');
  }
  return response;
};
