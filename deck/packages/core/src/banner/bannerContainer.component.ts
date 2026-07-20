import { module } from 'angular';

import { BannerContainer } from './BannerContainer';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

export const CORE_BANNER_CONTAINER_COMPONENT = 'spinnaker.core.banner.container';
export const name = CORE_BANNER_CONTAINER_COMPONENT;

module(name, []).component('bannerContainer', angularComponentFromReact(BannerContainer, 'bannerContainer', ['app']));
