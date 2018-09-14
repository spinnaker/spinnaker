import { module } from 'angular';
import { ROBOT_TO_HUMAN_FILTER } from 'core/presentation/robotToHumanFilter/robotToHuman.filter';
import { OVERRIDE_REGISTRY } from 'core/overrideRegistry';
import { SchedulerFactory } from 'core/scheduler/SchedulerFactory';
import { Application } from './application.model';

import { IDataSourceConfig } from './service/applicationDataSource';

export class ApplicationModelBuilder {
  /** This is mostly used in tests */
  public createApplicationForTests(name: string, ...dataSources: IDataSourceConfig[]): Application {
    return new Application(name, SchedulerFactory.createScheduler(), dataSources);
  }

  public createStandaloneApplication(name: string): Application {
    const application = new Application(name, SchedulerFactory.createScheduler(), []);
    application.isStandalone = true;
    return application;
  }

  public createNotFoundApplication(name: string): Application {
    const config: IDataSourceConfig = { key: 'serverGroups', lazy: true };
    const application = new Application(name, SchedulerFactory.createScheduler(), [config]);
    application.notFound = true;
    return application;
  }
}

export const APPLICATION_MODEL_BUILDER = 'spinnaker.core.application.model.builder';

module(APPLICATION_MODEL_BUILDER, [
  ROBOT_TO_HUMAN_FILTER,
  OVERRIDE_REGISTRY,
  require('@uirouter/angularjs').default,
]).service('applicationModelBuilder', ApplicationModelBuilder);
