import classNames from 'classnames';
import { Application } from 'core/application';
import { INotification, INotificationTypeConfig } from 'core/domain';
import { Registry } from 'core/registry';
import { useData } from 'core/presentation';
import { capitalize, filter, flatten, get, isEmpty } from 'lodash';
import React from 'react';

import { AppNotificationsService, IAppNotifications } from './AppNotificationsService';
import { EditNotificationModal } from './modal/EditNotificationModal';
import { NotificationTransformer } from './notification.transformer';

export interface INotificationsListProps {
  application?: Application;
  level: string;
  stageType?: string;
  sendNotifications?: boolean;
  handleSendNotificationsChanged?: (event: React.ChangeEvent<HTMLInputElement>) => void;
  notifications: INotification[];
  updateNotifications: (notifications: INotification[]) => void;
}

export const NotificationsList = ({
  application,
  level,
  notifications,
  stageType,
  sendNotifications,
  handleSendNotificationsChanged,
  updateNotifications,
}: INotificationsListProps) => {
  const [isDirty, setIsDirty] = React.useState<boolean>(false);
  const [indexEdited, setIndexEdited] = React.useState<number>(null);

  const supportedNotificationTypes = React.useMemo(
    () => Registry.pipeline.getNotificationTypes().map((type: INotificationTypeConfig) => type.key),
    [],
  );
  const { result: appNotifications, refresh: refreshAppNotifications } = useData(
    () => AppNotificationsService.getNotificationsForApplication(application.name),
    {} as IAppNotifications,
    [application.name],
  );

  const transfromNotifications = () => {
    /** Transforms the notifications to a table friendly format */
    const results = filter(
      flatten(
        supportedNotificationTypes.map((type) => {
          return get(appNotifications, type) || [];
        }),
      ),
      (allow) => allow !== undefined && allow.level === 'application',
    );
    updateNotifications(results);
    setIsDirty(false);
  };

  const editNotification = (newNotification: INotification) => {
    let notificationsToSave = notifications || [];
    if (indexEdited || indexEdited === 0) {
      notificationsToSave[indexEdited] = newNotification;
    } else {
      notificationsToSave = notificationsToSave.concat(newNotification);
    }
    setIsDirty(true);
    setIndexEdited(null);

    return saveNotifications(notificationsToSave);
  };

  const addNotification = () => openEditModal();

  const removeNotification = (index: number) => {
    const newNotifications = [...notifications];
    newNotifications.splice(index, 1);
    updateNotifications(newNotifications);
    setIsDirty(true);
  };

  const saveNotifications = (newNotifications: INotification[]) => {
    const toSaveNotifications: IAppNotifications = {
      application: application.name,
    };

    newNotifications.forEach((n) => {
      if (isEmpty(get(toSaveNotifications, n.type))) {
        toSaveNotifications[n.type] = [];
      }
      (toSaveNotifications[n.type] as INotification[]).push(n);
    });

    return AppNotificationsService.saveNotificationsForApplication(application.name, toSaveNotifications).then(() =>
      refreshAppNotifications(),
    );
  };

  const openEditModal = (notification?: INotification, index?: number) => {
    setIndexEdited(index);
    EditNotificationModal.show({
      notification: notification || { level, when: [] },
      level: level,
      stageType,
      editNotification,
    });
  };

  React.useEffect(() => {
    if (level === 'application') {
      transfromNotifications();
    }
  }, []);

  React.useEffect(() => {
    transfromNotifications();
  }, [appNotifications]);

  return (
    <>
      {level === 'stage' && stageType !== 'manualJudgment' && (
        <div className="form-group">
          <div className="col-md-9 col-md-offset-1">
            <div className="checkbox">
              <label>
                <input type="checkbox" onChange={handleSendNotificationsChanged} checked={sendNotifications} />
                <strong>Send notifications for this stage</strong>
              </label>
            </div>
          </div>
        </div>
      )}
      {(level !== 'stage' || sendNotifications) && (
        <div className="row">
          <div className={'col-md-12'}>
            <table className="table table-condensed">
              {notifications && notifications.length > 0 && (
                <thead>
                  <tr>
                    <th>Type</th>
                    <th>Details</th>
                    <th>Notify When</th>
                    <th>Actions</th>
                  </tr>
                </thead>
              )}
              <tbody>
                {notifications &&
                  notifications.map((n, i) => {
                    return (
                      <tr key={i} className={classNames({ 'templated-pipeline-item': n.inherited })}>
                        <td>{capitalize(n.type)}</td>
                        <td>{NotificationTransformer.getNotificationDetails(n)}</td>
                        <td>
                          {n.when &&
                            n.when.map((w, k) => (
                              <div key={k}>
                                {NotificationTransformer.getNotificationWhenDisplayName(w, level, stageType)}
                              </div>
                            ))}
                        </td>
                        <td>
                          {!n.inherited && (
                            <>
                              <button className="btn btn-xs btn-link" onClick={() => openEditModal(n, i)}>
                                Edit
                              </button>
                              <button className="btn btn-xs btn-link pad-left" onClick={() => removeNotification(i)}>
                                Remove
                              </button>
                            </>
                          )}
                          {n.inherited && <span className="btn btn-xs pad-left">Inherited from template</span>}
                        </td>
                      </tr>
                    );
                  })}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={7}>
                    <button className="btn btn-block add-new" onClick={() => addNotification()}>
                      <span className="glyphicon glyphicon-plus-sign" /> Add Notification Preference
                    </button>
                  </td>
                </tr>
              </tfoot>
            </table>
          </div>
        </div>
      )}
      {level === 'application' && (
        <div className="row main-footer">
          <div className="col-md-3">
            {isDirty && (
              <button className="btn btn-default" onClick={transfromNotifications} style={{ visibility: 'visible' }}>
                <span className="glyphicon glyphicon-flash" /> Revert
              </button>
            )}
          </div>
          <div className="col-md-9 text-right">
            {isDirty && (
              <button className="btn btn-primary" onClick={() => saveNotifications(notifications)}>
                <span className="far fa-check-circle" /> Save Changes
              </button>
            )}
            {!isDirty && (
              <span className="btn btn-link disabled">
                <span className="far fa-check-circle" /> In sync with server
              </span>
            )}
          </div>
        </div>
      )}
    </>
  );
};
