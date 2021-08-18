import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';
import { DimensionsEditor } from './DimensionsEditor';

export const AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_ALARM_DIMENSIONSEDITOR_COMPONENT =
  'spinnaker.amazon.serverGroup.details.scalingPolicy.dimensionEditor';
export const name = AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_ALARM_DIMENSIONSEDITOR_COMPONENT; // for backwards compatibility

module(AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_ALARM_DIMENSIONSEDITOR_COMPONENT, []).component(
  'dimensionsEditor',
  react2angular(withErrorBoundary(DimensionsEditor, 'dimensionsEditor'), [
    'alarm',
    'namespaceUpdated',
    'serverGroup',
    'updateAvailableMetrics',
  ]),
);
