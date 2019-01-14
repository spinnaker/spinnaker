import { module } from 'angular';
import { IArtifact, IExpectedArtifact } from 'core/domain';

export function summarizeExpectedArtifact(excludeKeys = ['customKind', 'kind']) {
  return function(expected: IExpectedArtifact): string {
    if (!expected) {
      return '';
    }

    return Object.keys(expected.matchArtifact)
      .filter((k: keyof IArtifact) => expected.matchArtifact[k])
      .filter(k => !excludeKeys.includes(k))
      .map((k: keyof IArtifact) => `${k}: ${expected.matchArtifact[k]}`)
      .join(', ');
  };
}

export const SUMMARIZE_EXPECTED_ARTIFACT_FILTER = 'spinnaker.core.artifacts.expected.service';
module(SUMMARIZE_EXPECTED_ARTIFACT_FILTER, []).filter('summarizeExpectedArtifact', summarizeExpectedArtifact);
