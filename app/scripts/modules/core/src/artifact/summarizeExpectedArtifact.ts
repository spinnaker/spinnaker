import { copy, module } from 'angular';
import { IArtifact, IExpectedArtifact } from 'core/domain';

export function summarizeExpectedArtifact() {
  return function(expected: IExpectedArtifact): string {
    if (!expected) {
      return '';
    }

    const artifact = copy(expected.matchArtifact);
    return Object.keys(artifact)
      .filter((k: keyof IArtifact) => artifact[k])
      .filter(k => k !== 'kind')
      .map((k: keyof IArtifact) => `${k}: ${artifact[k]}`)
      .join(', ');
  };
}

export const SUMMARIZE_EXPECTED_ARTIFACT_FILTER = 'spinnaker.core.artifacts.expected.service';
module(SUMMARIZE_EXPECTED_ARTIFACT_FILTER, []).filter('summarizeExpectedArtifact', summarizeExpectedArtifact);
