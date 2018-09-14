import { module } from 'angular';

import { ROBOT_TO_HUMAN_FILTER } from 'core/presentation/robotToHumanFilter/robotToHuman.filter';
import { OVERRIDE_REGISTRY } from 'core/overrideRegistry';
import { SchedulerFactory } from 'core/scheduler/SchedulerFactory';
import { Application } from './application.model';

import { ApplicationDataSource, IDataSourceConfig } from './service/applicationDataSource';

export class ApplicationModelBuilder {
  constructor(private $log: ng.ILogService, private $q: ng.IQService, private $filter: any) {
    'ngInject';
  }

  /**
   * This is mostly used in tests
   */
  public createApplicationForTests(name: string, ...dataSources: IDataSourceConfig[]): Application {
    const application = new Application(name, SchedulerFactory.createScheduler(), this.$q, this.$log);
    dataSources.forEach(ds => this.addDataSource(ds, application));
    return application;
  }

  public createStandaloneApplication(name: string): Application {
    const application = new Application(name, SchedulerFactory.createScheduler(), this.$q, this.$log);
    application.isStandalone = true;
    return application;
  }

  public createNotFoundApplication(name: string): Application {
    const application = new Application(name, SchedulerFactory.createScheduler(), this.$q, this.$log);
    this.addDataSource({ key: 'serverGroups', lazy: true }, application);
    application.notFound = true;
    return application;
  }

  private addDataSource(config: IDataSourceConfig, application: Application): void {
    const source = new ApplicationDataSource(config, application, this.$q, this.$log, this.$filter);
    application.dataSources.push(source);
    application[config.key] = source;
  }
}

export const APPLICATION_MODEL_BUILDER = 'spinnaker.core.application.model.builder';

module(APPLICATION_MODEL_BUILDER, [
  ROBOT_TO_HUMAN_FILTER,
  OVERRIDE_REGISTRY,
  require('@uirouter/angularjs').default,
]).service('applicationModelBuilder', ApplicationModelBuilder);
