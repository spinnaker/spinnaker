import { module } from 'angular';
import { withErrorBoundary } from 'core';
import { react2angular } from 'react2angular';

import { ViewScalingActivitiesLink } from './ViewScalingActivitiesLink';

export const VIEW_SCALING_ACTIVITIES_LINK = 'spinnaker.core.serverGroup.details.viewScalingActivities.link';

module(VIEW_SCALING_ACTIVITIES_LINK, []).component(
  'viewScalingActivitiesLink',
  react2angular(withErrorBoundary(ViewScalingActivitiesLink, 'viewScalingActivitiesLink'), ['serverGroup']),
);
