import { flatMap, get, set } from 'lodash';

import { REST } from 'core/api';
import {
  IManagedApplicationEnvironmentSummary,
  IManagedApplicationSummary,
  IManagedArtifactVersionEnvironment,
  IManagedResourceDiff,
  IManagedResourceEvent,
  IManagedResourceEventHistory,
  IManagedResourceEventHistoryResponse,
  ManagedResourceStatus,
} from 'core/domain';

import { isDependsOnConstraint } from './constraints/DependsOn';

const KIND_NAME_MATCHER = /.*\/(.*?)@/i;
const RESOURCE_DIFF_LIST_MATCHER = /^(.*)\[(.*)\]$/i;

export const getKindName = (kind: string) => {
  const match = kind.match(KIND_NAME_MATCHER);
  const extractedKind = match && match[1];

  return extractedKind || kind;
};

export const getResourceKindForLoadBalancerType = (type: string) => {
  switch (type) {
    case 'classic':
      return 'classic-load-balancer';
    case 'application':
      return 'application-load-balancer';
    default:
      return null;
  }
};

const transformManagedResourceDiff = (diff: IManagedResourceEventHistoryResponse[0]['delta']): IManagedResourceDiff => {
  if (!diff) return {};
  return Object.keys(diff).reduce((transformed, key) => {
    const diffNode = diff[key];
    const fieldKeys = flatMap<string, string>(key.split('/').filter(Boolean), (fieldKey) => {
      // Region keys currently come wrapped in {}, which is distracting and not useful. Let's trim those off.
      if (fieldKey.startsWith('{') && fieldKey.endsWith('}')) {
        return fieldKey.substring(1, fieldKey.length - 1);
      }

      // When items are added or removed from lists/sets, the API gives back a key like parentField[listItem].
      // This trips up our slash-bashed hierarchy and means we need to extract both componnts of the list syntax,
      // then flatten them out into the array of nested fields
      const listMatch = fieldKey.match(RESOURCE_DIFF_LIST_MATCHER);

      if (listMatch) {
        const parentField = listMatch[1];
        const listItem = listMatch[2];

        return [parentField, listItem];
      }

      return fieldKey;
    });
    const path = `["${fieldKeys.join(`"]["fields"]["`)}"]`;

    const existingTransformedNode: IManagedResourceDiff = get(transformed, path);
    set(transformed, path, {
      ...existingTransformedNode,
      key,
      diffType: diffNode.state,
      actual: diffNode.current,
      desired: diffNode.desired,
    });
    return transformed;
  }, {} as IManagedResourceDiff);
};

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

export class ManagedReader {
  private static decorateResources(response: IManagedApplicationEnvironmentSummary) {
    // Individual resources don't update their status when an application is paused/resumed,
    // so for now let's swap to a PAUSED status and keep things simpler in downstream components.
    if (response.applicationPaused) {
      response.resources.forEach((resource) => (resource.status = ManagedResourceStatus.PAUSED));
    }

    response.resources.forEach((resource) => (resource.isPaused = resource.status === ManagedResourceStatus.PAUSED));

    return response;
  }

  private static sortEnvironments(response: IManagedApplicationEnvironmentSummary) {
    // TODO: move this function to keel or optimize it and write some tests
    try {
      response.artifacts.map((artifact) => {
        artifact.versions.map((version) => {
          version.environments.sort((a, b) => {
            if (isEnvDependsOn(a, b)) {
              return -1;
            } else if (isEnvDependsOn(b, a)) {
              return 1;
            }
            // Prioritize if has any depends on
            if (envHasAnyDependsOn(a)) return -1;
            if (envHasAnyDependsOn(b)) return 1;
            return 0;
          });
        });
      });
    } catch (e) {
      console.error('Failed to sort environments');
    }
    return response;
  }

  public static getApplicationSummary(app: string): PromiseLike<IManagedApplicationSummary<'resources'>> {
    return REST('/managed/application').path(app).query({ entities: 'resources' }).get().then(this.decorateResources);
  }

  public static getEnvironmentsSummary(app: string): PromiseLike<IManagedApplicationEnvironmentSummary> {
    return REST('/managed/application')
      .path(app)
      .query({ entities: ['resources', 'artifacts', 'environments'], maxArtifactVersions: 30 })
      .get()
      .then(this.decorateResources)
      .then(this.sortEnvironments);
  }

  public static getResourceHistory(resourceId: string): PromiseLike<IManagedResourceEventHistory> {
    return REST('/history')
      .path(resourceId)
      .query({ limit: 100 })
      .get()
      .then((response: IManagedResourceEventHistoryResponse) => {
        response.forEach((event) => {
          if (event.delta) {
            ((event as unknown) as IManagedResourceEvent).delta = transformManagedResourceDiff(event.delta);
          }
        });
        return (response as unknown) as IManagedResourceEventHistory;
      });
  }
}
