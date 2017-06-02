import { module } from 'angular';

import { react2angular } from 'react2angular';
import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';

import { IEntityTags, IEntityTag } from 'core/domain';
import { Placement } from 'core/presentation';
import { Application } from 'core/application';
import { noop } from 'core/utils';
import { NotificationsPopover } from './NotificationsPopover';

export interface IEntityNotificationsProps {
  entity: any;
  application: Application;

  placement?: Placement;
  // eg: '25%'
  hOffsetPercent?: string;

  className?: string;

  pageLocation: string;
  entityType: string;

  onUpdate?(): void;
}

/**
 * A notifications popover for alerts and notices.
 * Shows the notifications for a single entity (not rolled up, and not grouped by message)
 */
@autoBindMethods
export class EntityNotifications extends React.Component<IEntityNotificationsProps, void> {
  public static defaultProps: Partial<IEntityNotificationsProps> = {
    placement: 'bottom',
    hOffsetPercent: '50%',
    className: '',
    onUpdate: noop,
  };

  private getAlertAnalyticsLabel(): string {
    const { entity, pageLocation, entityType } = this.props;
    const entityTags: IEntityTags = entity.entityTags;

    const { account, region, entityId } = entityTags.entityRef;
    const alertsStr = entityTags.alerts.map((tag: IEntityTag) => tag.name).join(',');

    return [ pageLocation, entityType, account, region, entityId, region, alertsStr ].join(':');
  }

  private getNoticeAnalyticsLabel(): string {
    const { entity, pageLocation, entityType } = this.props;
    const entityTags: IEntityTags = entity.entityTags;

    const { account, region, entityId } = entityTags.entityRef;
    const noticesStr = entityTags.notices.map((tag: IEntityTag) => tag.name).join(',');

    return [ pageLocation, entityType, account, region, entityId, noticesStr ].join(':');
  }

  public render() {
    const { entity, application, placement, hOffsetPercent, className, onUpdate } = this.props;
    const entityTags: IEntityTags = entity.entityTags;

    const tags = entityTags ? [entityTags] : [];

    return (
      <div className="entity-notifications">
        <NotificationsPopover
          entity={entity}
          tags={tags}
          application={application}
          type="alerts"
          gaLabelFn={this.getAlertAnalyticsLabel}
          grouped={false}
          categorized={true}
          className={className}
          placement={placement}
          hOffsetPercent={hOffsetPercent}
          onUpdate={onUpdate}
        />

        <NotificationsPopover
          entity={entity}
          tags={tags}
          application={application}
          type="notices"
          gaLabelFn={this.getNoticeAnalyticsLabel}
          grouped={false}
          categorized={false}
          className={className}
          placement={placement}
          hOffsetPercent={hOffsetPercent}
          onUpdate={onUpdate}
        />
      </div>
    );
  }
}


export const ENTITY_NOTIFICATIONS = 'spinnaker.core.entityTag.alerts.entitynotifications';
const ngmodule = module(ENTITY_NOTIFICATIONS, []);

ngmodule.component('entityNotificationsWrapper', react2angular(EntityNotifications, [
  'entity', 'application', 'placement', 'hOffsetPercent', 'className', 'pageLocation', 'entityType', 'onUpdate'
]));


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
  }
});
