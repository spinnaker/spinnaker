import { module } from 'angular';

import { ViewScalingActivitiesLink } from './ViewScalingActivitiesLink';
import { angularComponentFromReact } from '../../../angular/angularComponentFromReact';

export const VIEW_SCALING_ACTIVITIES_LINK = 'spinnaker.core.serverGroup.details.viewScalingActivities.link';

module(VIEW_SCALING_ACTIVITIES_LINK, []).component(
  'viewScalingActivitiesLink',
  angularComponentFromReact(ViewScalingActivitiesLink, 'viewScalingActivitiesLink', ['serverGroup']),
);
