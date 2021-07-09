import { IManagedArtifactVersion } from '../domain';

export const getArtifactVersionDisplayName = ({ displayName, build, git }: IManagedArtifactVersion) =>
  build?.id ? `#${build?.id}` : git?.commit || displayName || 'unknown';
