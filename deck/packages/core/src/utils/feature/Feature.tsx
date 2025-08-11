import * as React from 'react';

import type { FeatureFlags } from './FeatureContext';
import { useFeature } from './useFeature.hook';

interface FeatureProps {
  feature: keyof FeatureFlags;
  children?: React.ReactNode;
  render?: React.ReactNode;
}

/**
 * Renders content conditionally based on the specified feature flag.
 *
 * @param {Object} props - The properties for the Feature component.
 * @param {keyof FeatureFlags} props.feature - The name of the feature flag to check.
 * @param {React.ReactNode} [props.children] - The content to render if the feature is enabled.
 * @param {React.ReactNode} [props.render=children] - Optional custom render content; defaults to `children`.
 *
 * @returns {JSX.Element | null} - The rendered content if the feature is enabled; otherwise, `null`.
 *
 * @example
 * // Example usage with children
 * <Feature feature="slack">
 *   <SlackComponent />
 * </Feature>
 *
 * @example
 * // Example usage with a custom render prop
 * <Feature
 *   feature="entityTags"
 *   render={<EntityTagsComponent />}
 * />
 */
export function Feature({ feature, children, render = children }: FeatureProps): JSX.Element | null {
  const hasFeature = useFeature(feature);

  // If the feature is disabled, render nothing.
  if (!hasFeature) {
    return null;
  }

  // Otherwise, render the node content.
  return <React.Fragment>{render}</React.Fragment>;
}

/**
 * An alias of the `<Feature />` component.
 * Renders the provided content if the specified feature is enabled.
 *
 * @example
 * <IfFeatureEnabled feature="slack">
 *   <SlackComponent />
 * </IfFeatureEnabled>
 */
export function IfFeatureEnabled({ feature, children, render = children }: FeatureProps): JSX.Element | null {
  return <Feature feature={feature} render={render} />;
}

/**
 * Higher-order function to wrap a component with a feature flag check.
 * Ensures that the component only renders if the specified feature is enabled.
 *
 * @template Props
 * @param {keyof FeatureFlags} feature - The name of the feature flag to check.
 * @returns {(Component: React.ComponentType<Props>) => React.ComponentType<Props>} -
 * A higher-order component (HOC) that conditionally renders the wrapped component.
 *
 * @example
 * // Example usage
 * const EnhancedComponent = withFeature('newFeature')(MyComponent);
 *
 * <EnhancedComponent someProp="value" />;
 */
export function withFeature<Props extends object>(
  feature: keyof FeatureFlags,
): (Component: React.ComponentType<Props>) => React.ComponentType<Props> {
  return function wrapWithFeature(Component: React.ComponentType<Props>) {
    /**
     * Wraps the provided component with the <Feature> component,
     * checking the specified feature flag before rendering.
     *
     * @param {Props} props - Props passed to the wrapped component.
     * @returns {JSX.Element} - The wrapped component if the feature is enabled, or nothing if it is disabled.
     */
    function WithFeature(props: Props): JSX.Element {
      return (
        <Feature feature={feature}>
          <Component {...props} />
        </Feature>
      );
    }

    WithFeature.displayName = `WithFeature(${Component.displayName || Component.name})`;

    return WithFeature;
  };
}
