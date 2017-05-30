import * as React from 'react';
import * as ReactGA from 'react-ga';
import autoBindMethods from 'class-autobind-decorator';
import { uniq } from 'lodash';

import { Application } from 'core/application';
import { IEntityTags, IEntityTag } from 'core/domain';
import { EntityTagEditor, GroupedNotificationList, IEntityTagEditorProps, NotificationList } from 'core/entityTag';
import { Placement, HoverablePopover } from 'core/presentation';
import { ReactInjector } from 'core/reactShims';
import { noop } from 'core/utils';
import { ITaskMonitorConfig } from 'core/task';
import { CategorizedNotifications } from './CategorizedNotifications';
import { NotificationCategories, INotificationCategory } from './notificationCategories';

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
@autoBindMethods
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
      list.concat(entityTags[type].map(entityTag => ({ entityTags, entityTag })));
    const notifications: INotification[] = tags.filter(x => !!x).reduce(buildNotifications, []);

    const count = notifications.length;

    const severity = uniq(notifications.map(notification => notification.entityTag.category))
      .map(category => NotificationCategories.getCategory(category))
      .reduce((max: number, category: INotificationCategory) => Math.max(max, category.severity), 0);

    return { notifications, count, severity };
  }

  public componentWillReceiveProps(nextProps: INotificationsPopoverProps): void {
    this.setState(this.getState(nextProps));
  }

  public handleEditNotification(notification: INotification): void {
    const { entity, application, onUpdate } = this.props;
    const { entityTags, entityTag } = notification;

    const tag = {
      name: entityTag.name,
      value: {
        message: entityTag.value['message'],
        type: entityTag.value['type'],
      }
    };

    const props: IEntityTagEditorProps = {
      tag: tag,
      isNew: false,
      owner: entity,
      entityType: entityTags.entityRef.entityType,
      application: application,
      onUpdate: onUpdate,
      ownerOptions: null,
      entityRef: entityTags.entityRef,
    };

    EntityTagEditor.show(props);
  }

  public handleDeleteNotification(notification: INotification): void {
    const { entityTagWriter, confirmationModalService } = ReactInjector;
    const { application, entity, onUpdate } = this.props;
    const { entityTags, entityTag } = notification;
    const type = entityTag.value['type'];

    const taskMonitorConfig: ITaskMonitorConfig = {
      application: application,
      title: `Deleting ${type} on ${entity.name}`,
      onTaskComplete: () => onUpdate(),
    };

    confirmationModalService.confirm({
      header: `Really delete ${type}?`,
      buttonText: `Delete ${type}`,
      provider: entity.cloudProvider,
      account: entity.account,
      applicationName: application.name,
      taskMonitorConfig: taskMonitorConfig,
      submitMethod: () => entityTagWriter.deleteEntityTag(application, entity, entityTags, entityTag.name)
    });
  }

  public fireGAEvent(): void {
    const analyticsLabel = this.props.gaLabelFn(this.props);
    ReactGA.event({ action: 'SPAN', category: 'Alerts hovered', label: analyticsLabel });
  }

  private renderNotifications(): JSX.Element {
    const { type, grouped, categorized } = this.props;
    const { notifications } = this.state;
    const { title } = types[type];

    if (categorized) {
      return <CategorizedNotifications notifications={notifications} onEditTag={this.handleEditNotification} onDeleteTag={this.handleDeleteNotification} grouped={grouped} title={title} />
    } else if (grouped) {
      return <GroupedNotificationList notifications={notifications} />
    } else {
      return <NotificationList notifications={notifications} onEditTag={this.handleEditNotification} onDeleteTag={this.handleDeleteNotification} />;
    }
  }

  public render() {
    const { type, className, placement, hOffsetPercent } = this.props;
    const { count, severity } = this.state;
    if (count < 1) {
      return null;
    }

    // e.g., alerts-severity-0 alerts-severity-2 notices-severity-0
    const severityClass = `${type}-severity-${Math.min(Math.max(severity, 0), 2)}`;

    const { title, icon } = types[type];
    const Notifications = this.renderNotifications();

    return (
      <span className={`tag-marker small ${className || ''}`} onMouseEnter={this.fireGAEvent}>
        <HoverablePopover
          placement={placement}
          hOffsetPercent={hOffsetPercent}
          template={Notifications}
          title={title}
          className={`no-padding notifications-popover ${severityClass}`}
        >
          <i className={`notification fa ${icon} ${severityClass}`}/>
        </HoverablePopover>
      </span>
    );
  }
}

