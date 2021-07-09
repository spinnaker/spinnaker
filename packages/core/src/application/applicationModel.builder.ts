import { Application } from './application.model';
import { IServerGroup } from '../domain';
import { SchedulerFactory } from '../scheduler/SchedulerFactory';
import { IDataSourceConfig } from './service/applicationDataSource';

export class ApplicationModelBuilder {
  /** This is mostly used in tests */
  public static createApplicationForTests(name: string, ...dataSources: Array<IDataSourceConfig<any>>): Application {
    return new Application(name, SchedulerFactory.createScheduler(), dataSources);
  }

  public static createStandaloneApplication(name: string): Application {
    const application = new Application(name, SchedulerFactory.createScheduler(), []);
    application.isStandalone = true;
    return application;
  }

  public static createNotFoundApplication(name: string): Application {
    const config: IDataSourceConfig<IServerGroup[]> = { key: 'serverGroups', lazy: true, defaultData: [] };
    const application = new Application(name, SchedulerFactory.createScheduler(), [config]);
    application.notFound = true;
    return application;
  }

  public static createApplicationWithError(name: string): Application {
    const config: IDataSourceConfig<IServerGroup[]> = { key: 'serverGroups', lazy: true, defaultData: [] };
    const application = new Application(name, SchedulerFactory.createScheduler(), [config]);
    application.hasError = true;
    return application;
  }
}
