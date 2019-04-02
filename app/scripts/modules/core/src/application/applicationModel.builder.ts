import { SchedulerFactory } from 'core/scheduler/SchedulerFactory';
import { Application } from './application.model';

import { IDataSourceConfig } from './service/applicationDataSource';

export class ApplicationModelBuilder {
  /** This is mostly used in tests */
  public static createApplicationForTests(name: string, ...dataSources: IDataSourceConfig[]): Application {
    return new Application(name, SchedulerFactory.createScheduler(), dataSources);
  }

  public static createStandaloneApplication(name: string): Application {
    const application = new Application(name, SchedulerFactory.createScheduler(), []);
    application.isStandalone = true;
    return application;
  }

  public static createNotFoundApplication(name: string): Application {
    const config: IDataSourceConfig = { key: 'serverGroups', lazy: true };
    const application = new Application(name, SchedulerFactory.createScheduler(), [config]);
    application.notFound = true;
    return application;
  }
}
