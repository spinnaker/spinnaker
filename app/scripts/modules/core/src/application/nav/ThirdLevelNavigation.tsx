import * as React from 'react';
import { UIRouterContext } from '@uirouter/react-hybrid';
import { UISref, UISrefActive } from '@uirouter/react';
import { NavIcon } from 'core/application/nav/NavIcon';
import { BindAll } from 'lodash-decorators';

import { Application } from 'core/application';
import { DataSourceNotifications } from 'core/entityTag/notifications/DataSourceNotifications';
import { IDataSourceCategory } from './ApplicationHeader';

export interface IThirdLevelNavigationProps {
  category: IDataSourceCategory;
  application: Application;
}

@UIRouterContext
@BindAll()
export class ThirdLevelNavigation extends React.Component<IThirdLevelNavigationProps> {

  public render() {
    const { category, application } = this.props;
    if (!category || category.dataSources.length < 2) {
      return <div style={{ marginTop: '10px' }}/>;
    }
    return (
      <div className="container application-header application-nav hidden-xs">
        <div className="third-level-nav horizontal middle">
        <h3>{category.label}</h3>
        <div className="nav-section horizontal middle">
          {category.dataSources.map(dataSource => (
            <UISrefActive class="active" key={dataSource.key}>
              <UISref to={dataSource.sref}>
                <a className="nav-item horizontal middle">

                  <NavIcon icon={dataSource.icon}/>

                  {' ' + dataSource.label}

                  <DataSourceNotifications tags={dataSource.alerts} application={application} tabName={category.label}/>

                  {dataSource.badge && application.getDataSource(dataSource.badge).data.length > 0 &&
                    <span className="badge">{application.getDataSource(dataSource.badge).data.length}</span>}

                </a>
              </UISref>
            </UISrefActive>
          ))}
        </div>
        </div>
      </div>
    );
  }
}
