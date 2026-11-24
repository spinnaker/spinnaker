import { artifactDelimiter } from './artifactDelimiter';
import type { IArtifact } from '../domain';

export function artifactName(artifact: IArtifact, name?: string): string {
  const identifier = name || artifact.name || artifact.reference;
  return artifact.version ? `${identifier}${artifactDelimiter(artifact)}${artifact.version}` : `${identifier}`;
}
