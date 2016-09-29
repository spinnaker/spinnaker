import {ApplicationDataSource, DataSourceConfig} from './service/applicationDataSource.ts'
import {module} from 'angular';
import {Application} from './application.model.ts';

/**
 * NOTE: this is only used in tests
 */
export class ApplicationModelBuilder {

  static get $inject() { return ['$log', '$q', '$filter', 'schedulerFactory']; }

  constructor(private $log: ng.ILogService, private $q: ng.IQService, private $filter: any, private schedulerFactory: any) {}

  public createApplication(...dataSources: any[]): Application {
    if (Array.isArray(dataSources[0])) {
      dataSources = dataSources[0];
    }
    let application = new Application('app', this.schedulerFactory.createScheduler(), this.$q, this.$log);
    dataSources.forEach(ds => this.addDataSource(new DataSourceConfig(ds), application));
    return application;
  }

  private addDataSource(config: DataSourceConfig, application: Application): void {
    let source = new ApplicationDataSource(config, application, this.$q, this.$log, this.$filter);
    application.dataSources.push(source);
    application[config.key] = source;
  }

}

const moduleName = 'spinnaker.core.application.model.builder';

module(moduleName, [
  require('../presentation/robotToHumanFilter/robotToHuman.filter'),
  require('../scheduler/scheduler.factory'),
])
  .service('applicationModelBuilder', ApplicationModelBuilder);

export default moduleName;
