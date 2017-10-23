import { module } from 'angular';
import { UIRouter } from '@uirouter/core';

import { ROBOT_TO_HUMAN_FILTER } from 'core/presentation/robotToHumanFilter/robotToHuman.filter';
import { SCHEDULER_FACTORY, SchedulerFactory } from 'core/scheduler/scheduler.factory';
import { Application } from './application.model';

import { ApplicationDataSource, IDataSourceConfig } from './service/applicationDataSource';

export class ApplicationModelBuilder {

  constructor(private $log: ng.ILogService,
              private $q: ng.IQService,
              private $filter: any,
              private $uiRouter: UIRouter,
              private schedulerFactory: SchedulerFactory) {
    'ngInject';
  }

  /**
   * This is mostly used in tests
   */
  public createApplication(name: string, ...dataSources: any[]): Application {
    if (Array.isArray(dataSources[0])) {
      dataSources = dataSources[0];
    }
    const application = new Application(name, this.schedulerFactory.createScheduler(), this.$q, this.$log);
    dataSources.forEach(ds => this.addDataSource(ds, application));
    return application;
  }

  public createStandaloneApplication(name: string): Application {
    const application = new Application(name, this.schedulerFactory.createScheduler(), this.$q, this.$log);
    application.isStandalone = true;
    return application;
  }

  public createNotFoundApplication(name: string): Application {
    const application = new Application(name, this.schedulerFactory.createScheduler(), this.$q, this.$log);
    this.addDataSource({ key: 'serverGroups', lazy: true }, application);
    application.notFound = true;
    return application;
  }

  private addDataSource(config: IDataSourceConfig, application: Application): void {
    const source = new ApplicationDataSource(config, application, this.$q, this.$log, this.$filter, this.$uiRouter);
    application.dataSources.push(source);
    application[config.key] = source;
  }

}

export const APPLICATION_MODEL_BUILDER = 'spinnaker.core.application.model.builder';

module(APPLICATION_MODEL_BUILDER, [
  SCHEDULER_FACTORY,
  ROBOT_TO_HUMAN_FILTER,
  require('@uirouter/angularjs').default,
])
  .service('applicationModelBuilder', ApplicationModelBuilder);
