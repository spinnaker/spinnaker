import type { IArtifact } from '../domain';

export function artifactDelimiter(artifact: IArtifact): string {
  switch (artifact.type) {
    case 'docker/image':
      return ':';
    default:
      return ' - ';
  }
}
