import { IManagedResourceSummary, IManagedArtifactVersion } from '../domain';

export const getResourceName = ({ moniker: { app, stack, detail } }: IManagedResourceSummary) =>
  [app, stack, detail].filter(Boolean).join('-');

export const getArtifactVersionDisplayName = ({ displayName, build, git }: IManagedArtifactVersion) =>
  build?.id ? `#${build?.id}` : git?.commit || displayName || 'unknown';
