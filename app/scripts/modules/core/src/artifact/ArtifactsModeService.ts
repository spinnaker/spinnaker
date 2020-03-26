import { SETTINGS } from 'core/config';

/**
 * Currently, because there are two artifacts feature flags, there are four
 * possible configuration states:
 * 1. `artifacts` disabled, `artifactsRewrite` disabled
 * 2. `artifacts` enabled, `artifactsRewrite` disabled
 * 3. `artifacts` disabled, `artifactsRewrite` enabled
 * 4. `artifacts` enabled, `artifactsRewrite` enabled
 *
 * However, only three UI experiences should be possible:
 * 1. No artifacts UI
 * 2. The legacy artifacts UI
 * 3. The standard ("rewrite") artifacts UI
 *
 * This service provides a layer of abstraction over the existing feature flag
 * checks in terms of these three possible experiences. As we deprecate these
 * feature flags in favor of an enabled-by-default standard artifacts UI, as
 * described in https://github.com/spinnaker/governance/pull/111, it will be
 * helpful to have this logic isolated to a single service.
 */

export enum ArtifactsMode {
  DISABLED,
  LEGACY,
  STANDARD,
}

export class ArtifactsModeService {
  public static readonly artifactsMode = ArtifactsModeService.getArtifactsMode();

  private static getArtifactsMode(): ArtifactsMode {
    if (SETTINGS.feature.artifactsRewrite === true) {
      return ArtifactsMode.STANDARD;
    }
    if (SETTINGS.feature.artifacts === true) {
      return ArtifactsMode.LEGACY;
    }
    return ArtifactsMode.DISABLED;
  }
}
