import { pick, uniq } from 'lodash';
import React from 'react';

import { CategorizedNotifications } from './CategorizedNotifications';
import { EntityTagEditor, IEntityTagEditorProps } from '../EntityTagEditor';
import { GroupedNotificationList } from './GroupedNotificationList';
import { NotificationList } from './NotificationList';
import { Application } from '../../application';
import { ConfirmationModalService } from '../../confirmationModal';
import { IEntityTag, IEntityTags } from '../../domain';
import { EntityTagWriter } from '../entityTags.write.service';
import { INotificationCategory, NotificationCategories } from './notificationCategories';
import { HoverablePopover, IHoverablePopoverContentsProps, Placement } from '../../presentation';
import { ITaskMonitorConfig } from '../../task';
import { logger, noop } from '../../utils';

import './notifications.less';
import './notifications.popover.less';

export type NotificationType = 'alerts' | 'notices';

/** A UI notification. */
export interface INotification {
  /** the notification message, category, etc */
  entityTag: IEntityTag;
  /** the enclosing IEntityTags that the notification lives on */
  entityTags: IEntityTags;
}

export interface INotificationsPopoverProps {
  /** The entity that "owns" the tags */
  entity?: any;
  /** The entity tags which contain the notifications */
  tags: IEntityTags[];
  /** The type of notifications (which is also the JS property to use) */
  type?: NotificationType;
  /** The global application object */
  application: Application;

  /**
   * Group identical messages from multiple server groups together
   * When grouped, the edit/delete controls do not render.
   */
  grouped?: boolean;
  /** Show categories */
  categorized?: boolean;
  /** Tooltip placement */
  placement?: Placement;
  /** e.g. '25%' */
  hOffsetPercent?: string;
  /** class for the popover span */
  className?: string;

  /** A function that returns the google analytics event string */
  gaLabelFn?(props: INotificationsPopoverProps): string;

  /** Callback after a tag has been updated */
  onUpdate?(): void;
}

const types = {
  alerts: { title: 'Alerts', icon: 'fa-exclamation-triangle' },
  notices: { title: 'Notices', icon: 'fa-flag' },
};

export interface INotificationsPopoverState {
  notifications: INotification[];
  count: number;
  severity: number;
}

/**
 * A configurable popover which renders notifications
 */
export class NotificationsPopover extends React.Component<INotificationsPopoverProps, INotificationsPopoverState> {
  public static defaultProps: Partial<INotificationsPopoverProps> = {
    tags: [],
    categorized: true,
    className: '',
    entity: null,
    grouped: false,
    onUpdate: noop,
    placement: 'bottom',
    type: 'alerts',
  };

  constructor(props: INotificationsPopoverProps) {
    super(props);
    this.state = this.getState(props);
  }

  private getState(newProps: INotificationsPopoverProps): INotificationsPopoverState {
    const { tags, type } = newProps;

    const buildNotifications = (list: INotification[], entityTags: IEntityTags) =>
      list.concat(entityTags[type].map((entityTag) => ({ entityTags, entityTag })));
    const notifications: INotification[] = tags.filter((x) => !!x).reduce(buildNotifications, []);

    const count = notifications.length;

    const severity = uniq(notifications.map((notification) => notification.entityTag.category))
      .map((category) => NotificationCategories.getCategory(category))
      .reduce((max: number, category: INotificationCategory) => Math.max(max, category.severity), 0);

    return { notifications, count, severity };
  }

  public componentWillReceiveProps(nextProps: INotificationsPopoverProps): void {
    this.setState(this.getState(nextProps));
  }

  public handleEditNotification(notification: INotification): void {
    const { entity, application, onUpdate } = this.props;
    const { entityTags, entityTag } = notification;

    const tag = pick(entityTag, 'name', 'namespace', 'category') as IEntityTag;
    tag.value = { ...entityTag.value };

    const props: IEntityTagEditorProps = {
      tag,
      isNew: false,
      owner: entity,
      entityType: entityTags.entityRef.entityType,
      application,
      onUpdate,
      ownerOptions: null,
      entityRef: entityTags.entityRef,
    };

    EntityTagEditor.show(props);
  }

  public handleDeleteNotification(notification: INotification): void {
    const { application, entity, onUpdate } = this.props;
    const { entityTags, entityTag } = notification;
    const type = entityTag.value['type'];

    const taskMonitorConfig: ITaskMonitorConfig = {
      application,
      title: `Deleting ${type} on ${entity.name}`,
      onTaskComplete: () => application.entityTags.refresh().then(() => onUpdate()),
    };

    ConfirmationModalService.confirm({
      header: `Really delete ${type}?`,
      buttonText: `Delete ${type}`,
      account: entity.account,
      taskMonitorConfig,
      submitMethod: () => EntityTagWriter.deleteEntityTag(application, entity, entityTags, entityTag.name),
    });
  }

  public fireGAEvent = (): void => {
    const analyticsLabel = this.props.gaLabelFn(this.props);
    logger.log({ action: 'SPAN', category: 'Alerts hovered', data: { label: analyticsLabel } });
  };

  private PopoverContent = ({ hidePopover }: IHoverablePopoverContentsProps) => {
    const { type, categorized, grouped } = this.props;
    const { notifications } = this.state;

    const handleEditNotification = (notification: INotification) => {
      hidePopover();
      this.handleEditNotification(notification);
    };

    const handleDeleteNotification = (notification: INotification) => {
      hidePopover();
      this.handleDeleteNotification(notification);
    };

    return (
      <NotificationsPopoverContents
        type={type}
        categorized={categorized}
        grouped={grouped}
        notifications={notifications}
        hidePopover={hidePopover}
        handleEditNotification={handleEditNotification}
        handleDeleteNotification={handleDeleteNotification}
      />
    );
  };

  public render() {
    const { type, className, placement, hOffsetPercent } = this.props;
    const { count, severity } = this.state;
    const { title, icon } = types[type];
    if (count < 1) {
      return null;
    }

    // e.g., alerts-severity-0 alerts-severity-2 notices-severity-0
    const severityClass = `${type}-severity-${Math.min(Math.max(severity, 0), 2)}`;

    return (
      <span className={`tag-marker small ${className || ''}`} onMouseEnter={this.fireGAEvent}>
        <HoverablePopover
          delayShow={100}
          placement={placement}
          hOffsetPercent={hOffsetPercent}
          Component={this.PopoverContent}
          title={title}
          className={`no-padding notifications-popover ${severityClass}`}
        >
          <i className={`notification fa ${icon} ${severityClass}`} />
        </HoverablePopover>
      </span>
    );
  }
}

interface INotificationsProps extends IHoverablePopoverContentsProps {
  type: 'alerts' | 'notices';
  grouped: boolean;
  categorized: boolean;
  notifications: INotification[];
  hidePopover: () => void;
  handleEditNotification: (notification: INotification) => void;
  handleDeleteNotification: (notification: INotification) => void;
}

const NotificationsPopoverContents = (props: INotificationsProps) => {
  const { type, grouped, categorized, notifications, handleEditNotification, handleDeleteNotification } = props;
  const { title } = types[type];

  if (categorized) {
    return (
      <CategorizedNotifications
        notifications={notifications}
        onEditTag={handleEditNotification}
        onDeleteTag={handleDeleteNotification}
        grouped={grouped}
        title={title}
      />
    );
  } else if (grouped) {
    return <GroupedNotificationList notifications={notifications} />;
  } else {
    return (
      <NotificationList
        notifications={notifications}
        onEditTag={handleEditNotification}
        onDeleteTag={handleDeleteNotification}
      />
    );
  }
};
