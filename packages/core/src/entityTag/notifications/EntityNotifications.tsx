import React from 'react';

import { EphemeralPopover } from './EphemeralPopover';
import { NotificationsPopover } from './NotificationsPopover';
import { Application } from '../../application';
import { IEntityTag, IEntityTags } from '../../domain';
import { Placement } from '../../presentation';
import { noop } from '../../utils';

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
export class EntityNotifications extends React.Component<IEntityNotificationsProps> {
  public static defaultProps: Partial<IEntityNotificationsProps> = {
    placement: 'bottom',
    hOffsetPercent: '50%',
    className: '',
    onUpdate: noop,
  };

  private getAlertAnalyticsLabel = (): string => {
    const { entity, pageLocation, entityType } = this.props;
    const entityTags: IEntityTags = entity.entityTags;

    const { account, region, entityId } = entityTags.entityRef;
    const alertsStr = entityTags.alerts.map((tag: IEntityTag) => tag.name).join(',');

    return [pageLocation, entityType, account, region, entityId, region, alertsStr].join(':');
  };

  private getNoticeAnalyticsLabel = (): string => {
    const { entity, pageLocation, entityType } = this.props;
    const entityTags: IEntityTags = entity.entityTags;

    const { account, region, entityId } = entityTags.entityRef;
    const noticesStr = entityTags.notices.map((tag: IEntityTag) => tag.name).join(',');

    return [pageLocation, entityType, account, region, entityId, noticesStr].join(':');
  };

  public render() {
    const { entity, application, placement, hOffsetPercent, className, onUpdate } = this.props;
    const entityTags: IEntityTags = entity && entity.entityTags;

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

        <EphemeralPopover entity={entity} />
      </div>
    );
  }
}
