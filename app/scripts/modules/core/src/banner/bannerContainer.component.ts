import { module } from 'angular';
import { react2angular } from 'react2angular';

import { BannerContainer } from './BannerContainer';
import { withErrorBoundary } from '../presentation/SpinErrorBoundary';

export const CORE_BANNER_CONTAINER_COMPONENT = 'spinnaker.core.banner.container';
export const name = CORE_BANNER_CONTAINER_COMPONENT;

module(name, []).component(
  'bannerContainer',
  react2angular(withErrorBoundary(BannerContainer, 'bannerContainer'), ['app']),
);
