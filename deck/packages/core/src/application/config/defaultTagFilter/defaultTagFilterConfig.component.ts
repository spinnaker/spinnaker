import { module } from 'angular';
import { DefaultTagFilterConfig } from './DefaultTagFilterConfig';
import { angularComponentFromReact } from '../../../angular/angularComponentFromReact';

export const DEFAULT_TAG_FILTER_CONFIG = 'spinnaker.micros.application.defaultTagFilterConfig.component';
module(DEFAULT_TAG_FILTER_CONFIG, []).component(
  'defaultTagFilterConfig',
  angularComponentFromReact(DefaultTagFilterConfig, 'defaultTagFilterConfig', [
    'defaultTagFilterConfigs',
    'isSaving',
    'saveError',
    'updateDefaultTagFilterConfigs',
  ]),
);
