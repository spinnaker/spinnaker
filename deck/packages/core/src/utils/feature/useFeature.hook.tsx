import React from 'react';

import type { FeatureFlags } from './FeatureContext';
import { FeatureFlagsContext } from './FeatureContext';

/**
 * A hook to access the current set of feature flags from the context.
 * If no provider is found, it will default to `SETTINGS.feature`.
 *
 * @returns {FeatureFlags} - The feature flags currently in the context.
 */
export function useFeatures(): FeatureFlags {
  return React.useContext(FeatureFlagsContext);
}

/**
 * A hook to determine if a particular feature is enabled.
 *
 * @param {keyof FeatureFlags} feature - The name of the feature flag to check.
 * @returns {boolean} - True if the feature is enabled; otherwise false.
 */
export function useFeature(feature: keyof FeatureFlags): boolean {
  const features = useFeatures();
  return features[feature];
}
