import * as React from 'react';
import { UIRouterContext } from '@uirouter/react-hybrid';
import { UISref, UISrefActive } from '@uirouter/react';
import { BindAll } from 'lodash-decorators';

import { Application } from 'core/application';
import { IDataSourceCategory } from './ApplicationHeader';
import { DataSourceEntry } from './DataSourceEntry';

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
                    <DataSourceEntry application={application} dataSource={dataSource}/>
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
