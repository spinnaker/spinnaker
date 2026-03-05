import * as React from 'react';

import type { IFeatures } from '../../config';
import { SETTINGS } from '../../config';
import { useFeatures } from './useFeature.hook';

/**
 * Type alias for feature flags, extending from IFeatures interface.
 */
export type FeatureFlags = IFeatures;

/**
 * Creates a React Context for feature flags.
 * Defaults to `SETTINGS.feature` if no provider is found in the component tree.
 */
export const FeatureFlagsContext = React.createContext<FeatureFlags>(SETTINGS.feature);

/**
 * Merges two FeatureFlags objects. Flags in `b` will override flags in `a`.
 *
 * @param {FeatureFlags} a - The base feature flags.
 * @param {FeatureFlags} b - The overriding feature flags.
 * @returns {FeatureFlags} - A new object containing merged flags from `a` and `b`.
 */
function mergeFeatures(a: FeatureFlags, b: FeatureFlags): FeatureFlags {
  return { ...a, ...b };
}

/**
 * A context provider component that merges the global feature flags
 * from `SETTINGS.feature` with any feature flags passed in via props.
 *
 * @param {Object} props
 * @param {FeatureFlags} [props.features] - Feature flags to merge with the global flags.
 * @param {React.ReactNode} props.children - Components that will consume the merged flags.
 * @returns {JSX.Element} - The provider that holds merged feature flags in context.
 */
export function FeaturesProvider({
  features = {},
  children,
}: {
  features?: FeatureFlags;
  children: React.ReactNode;
}): JSX.Element {
  const currentFeatures = useFeatures();
  // Merge global settings with any flags provided to the provider
  const mergedFeatures = React.useMemo(() => mergeFeatures(currentFeatures, features), [features]);
  return <FeatureFlagsContext.Provider value={mergedFeatures}>{children}</FeatureFlagsContext.Provider>;
}
