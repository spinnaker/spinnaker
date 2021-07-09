import { module } from 'angular';
import { react2angular } from 'react2angular';

import { EntityNotifications } from './EntityNotifications';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

export const ENTITY_NOTIFICATIONS = 'spinnaker.core.entityTag.alerts.entitynotifications';
const ngmodule = module(ENTITY_NOTIFICATIONS, []);

ngmodule.component(
  'entityNotificationsWrapper',
  react2angular(withErrorBoundary(EntityNotifications, 'entityNotificationsWrapper'), [
    'entity',
    'application',
    'placement',
    'hOffsetPercent',
    'className',
    'pageLocation',
    'entityType',
    'onUpdate',
  ]),
);

ngmodule.component('entityNotifications', {
  template: `
    <entity-notifications-wrapper
      entity="$ctrl.entity"
      application="$ctrl.application"
      placement="$ctrl.placement"
      h-offset-percent="$ctrl.hOffsetPercent"
      class-name="$ctrl.className"
      entity-type="$ctrl.entityType"
      page-location="$ctrl.pageLocation"
      on-update="$ctrl.onUpdate"
    ></entity-notifications-wrapper>
  `,
  bindings: {
    entity: '<',
    application: '<',
    placement: '@',
    hOffsetPercent: '@',
    className: '@',
    entityType: '@',
    pageLocation: '@',
    onUpdate: '&',
  },
});
