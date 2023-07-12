import type { IArtifact, IExecutionContext } from '../../../../../domain';
import { ARTIFACT_TYPE_EMBEDDED, ARTIFACT_TYPE_REMOTE } from '../../../../../domain';

// IArtifact type is wrong and does not represent the real value
export const getBakedArtifacts = (context: IExecutionContext): Array<IArtifact & { reference: string }> => {
  if ('artifacts' in context) {
    return context.artifacts.filter(
      (a: IArtifact) => (a.type === ARTIFACT_TYPE_EMBEDDED || a.type === ARTIFACT_TYPE_REMOTE) && a.reference,
    );
  } else {
    return [];
  }
};
