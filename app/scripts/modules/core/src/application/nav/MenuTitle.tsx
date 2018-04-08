import * as React from 'react';
import { UISref } from '@uirouter/react';

import { Application } from 'core/application';
import { IEntityTags } from 'core/domain';
import { DataSourceNotifications } from 'core/entityTag/notifications/DataSourceNotifications';

import { NavIcon } from './NavIcon';
import { IDataSourceCategory } from './ApplicationHeader';

export interface IMenuTitleProps {
  bsRole: string;
  isActive: boolean;
  category: IDataSourceCategory;
  application: Application;
  runningCount?: number;
  tags?: IEntityTags[];
  closeMenu: () => void;
}

/**
 * The ONLY reason for this class is to avoid a TS warning driven by the Bootstrap Dropdown component checking for a
 * child with a "bsRole" prop. At this point, it's not totally clear what using the Dropdown buys us, rather than just
 * doing it via custom components, but there are probably reasons to keep it...
 *
 * This could be a SFC; however, the React Bootstrap Dropdown attempts to add a ref to the element, which throws a
 * warning ("Stateless function components cannot be given refs. Attempts to access this ref will fail.") and it's
 * basically the same LOC to make it a plain old Component and avoid the warning .
 */
export class MenuTitle extends React.Component<IMenuTitleProps> {

  public render() {
    const { category, application, runningCount, tags, isActive, closeMenu } = this.props;
    const defaultDataSource = category.dataSources[0];
    return (
      <UISref to={defaultDataSource.sref}>
        <a className={`nav-item horizontal middle ${isActive ? 'active' : ''}`}>
            <span className={`horizontal middle ${isActive ? 'active' : ''}`} onClick={closeMenu}>
              <NavIcon icon={category.icon}/>
              {' ' + category.label}
              {runningCount > 0 && <span className="badge badge-running-count">{runningCount}</span>}
              <DataSourceNotifications tags={tags} application={application} tabName={category.label}/>
            </span>
        </a>
      </UISref>
    );
  }
};
