import {module} from 'angular';

import {ApplicationDataSource, DataSourceConfig} from './service/applicationDataSource';
import {Application} from './application.model';
import {SCHEDULER_FACTORY, SchedulerFactory} from 'core/scheduler/scheduler.factory';

export class ApplicationModelBuilder {

  constructor(private $log: ng.ILogService,
              private $q: ng.IQService,
              private $filter: any,
              private schedulerFactory: SchedulerFactory) {
    'ngInject';
  }

  /**
   * This is only used in tests
   */
  public createApplication(...dataSources: any[]): Application {
    if (Array.isArray(dataSources[0])) {
      dataSources = dataSources[0];
    }
    const application = new Application('app', this.schedulerFactory.createScheduler(), this.$q, this.$log);
    dataSources.forEach(ds => this.addDataSource(new DataSourceConfig(ds), application));
    return application;
  }

  public createStandaloneApplication(name: string): Application {
    const application = new Application(name, this.schedulerFactory.createScheduler(), this.$q, this.$log);
    application.isStandalone = true;
    return application;
  }

  public createNotFoundApplication(name: string): Application {
    const application = new Application(name, this.schedulerFactory.createScheduler(), this.$q, this.$log);
    application.notFound = true;
    return application;
  }

  private addDataSource(config: DataSourceConfig, application: Application): void {
    const source = new ApplicationDataSource(config, application, this.$q, this.$log, this.$filter);
    application.dataSources.push(source);
    application[config.key] = source;
  }

}

export let applicationModelBuilder: ApplicationModelBuilder = undefined;
export const APPLICATION_MODEL_BUILDER = 'spinnaker.core.application.model.builder';

module(APPLICATION_MODEL_BUILDER, [
  require('../presentation/robotToHumanFilter/robotToHuman.filter'),
  SCHEDULER_FACTORY,
])
  .service('applicationModelBuilder', ApplicationModelBuilder)
  .run(($injector: any) => applicationModelBuilder = <ApplicationModelBuilder>$injector.get('applicationModelBuilder'));
