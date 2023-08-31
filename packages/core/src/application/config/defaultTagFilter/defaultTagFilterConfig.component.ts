import { module } from 'angular';
import { react2angular } from 'react2angular';
import { DefaultTagFilterConfig } from './DefaultTagFilterConfig';
import { withErrorBoundary } from '../../../presentation/SpinErrorBoundary';

export const DEFAULT_TAG_FILTER_CONFIG = 'spinnaker.micros.application.defaultTagFilterConfig.component';
module(DEFAULT_TAG_FILTER_CONFIG, []).component(
  'defaultTagFilterConfig',
  react2angular(withErrorBoundary(DefaultTagFilterConfig, 'defaultTagFilterConfig'), [
    'defaultTagFilterConfigs',
    'isSaving',
    'saveError',
    'updateDefaultTagFilterConfigs',
  ]),
);
