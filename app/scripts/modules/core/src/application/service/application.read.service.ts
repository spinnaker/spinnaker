import { IFilterService, ILogService, IPromise, IQService, module } from 'angular';

import { UIRouter } from '@uirouter/core';

import { API } from 'core/api/ApiService';
import { SchedulerFactory } from 'core/scheduler/SchedulerFactory';
import { Application } from '../application.model';
import { ApplicationDataSource, IDataSourceConfig } from '../service/applicationDataSource';
import { ApplicationDataSourceRegistry } from './ApplicationDataSourceRegistry';
import { ROBOT_TO_HUMAN_FILTER } from 'core/presentation/robotToHumanFilter/robotToHuman.filter';
import { InferredApplicationWarningService } from './InferredApplicationWarningService';

export interface IApplicationDataSourceAttribute {
  enabled: string[];
  disabled: string[];
}

export interface IApplicationSummary {
  accounts?: string;
  aliases?: string;
  cloudProviders?: string;
  createTs?: string;
  description?: string;
  email?: string;
  name: string;
  pdApiKey?: string;
  updateTs?: string;
}

export class ApplicationReader {
  private applicationMap: Map<string, IApplicationSummary> = new Map<string, IApplicationSummary>();

  public constructor(
    private $q: IQService,
    private $log: ILogService,
    private $filter: IFilterService,
    private $uiRouter: UIRouter,
  ) {
    'ngInject';
  }

  public listApplications(populateMap = false): IPromise<IApplicationSummary[]> {
    return API.all('applications')
      .useCache()
      .getList()
      .then((applications: IApplicationSummary[]) => {
        if (populateMap) {
          const tmpMap: Map<string, IApplicationSummary> = new Map<string, IApplicationSummary>();
          applications.forEach((application: IApplicationSummary) => tmpMap.set(application.name, application));
          this.applicationMap = tmpMap;
        }
        return applications;
      });
  }

  public getApplication(name: string, expand = true): IPromise<Application> {
    return API.one('applications', name)
      .withParams({ expand: expand })
      .get()
      .then((fromServer: Application) => {
        const application: Application = new Application(
          fromServer.name,
          SchedulerFactory.createScheduler(),
          this.$q,
          this.$log,
        );
        application.attributes = fromServer.attributes;
        this.splitAttributes(application.attributes, ['accounts', 'cloudProviders']);
        this.addDataSources(application);
        application.refresh();
        return application;
      });
  }

  public getApplicationMap(): Map<string, IApplicationSummary> {
    return this.applicationMap;
  }

  private splitAttributes(attributes: any, fields: string[]) {
    fields.forEach(field => {
      if (attributes[field]) {
        if (!Array.isArray(attributes[field])) {
          attributes[field] = attributes[field].split(',');
        }
      } else {
        attributes[field] = [];
      }
    });
  }

  private addDataSources(application: Application): void {
    const dataSources: IDataSourceConfig[] = ApplicationDataSourceRegistry.getDataSources();
    dataSources.forEach((ds: IDataSourceConfig) => {
      const dataSource: ApplicationDataSource = new ApplicationDataSource(
        ds,
        application,
        this.$q,
        this.$log,
        this.$filter,
        this.$uiRouter,
      );
      application.dataSources.push(dataSource);
      application[ds.key] = dataSource;
    });
    this.setDisabledDataSources(application);
  }

  public setDisabledDataSources(application: Application) {
    const allDataSources: ApplicationDataSource[] = application.dataSources,
      appDataSources: IApplicationDataSourceAttribute = application.attributes.dataSources;
    if (!appDataSources) {
      allDataSources.filter(ds => ds.optIn).forEach(ds => this.setDataSourceDisabled(ds, application, true));
      if (InferredApplicationWarningService.isInferredApplication(application)) {
        allDataSources
          .filter(ds => ds.requireConfiguredApp)
          .forEach(ds => this.setDataSourceDisabled(ds, application, true));
      }
    } else {
      allDataSources.forEach(ds => {
        if (ds.optional) {
          if (ds.optIn) {
            this.setDataSourceDisabled(ds, application, !appDataSources.enabled.includes(ds.key));
          } else {
            this.setDataSourceDisabled(ds, application, appDataSources.disabled.includes(ds.key));
          }
        }
      });
    }
    allDataSources.filter(ds => ds.requiresDataSource).forEach(ds => {
      const parent = allDataSources.find(p => p.key === ds.requiresDataSource);
      if (parent && parent.disabled) {
        this.setDataSourceDisabled(ds, application, true);
      }
    });
  }

  private setDataSourceDisabled(dataSource: ApplicationDataSource, application: Application, disabled: boolean) {
    dataSource.disabled = disabled;
    if (dataSource.badge) {
      application.dataSources.find(ds => ds.key === dataSource.badge).disabled = disabled;
    }
  }
}

export const APPLICATION_READ_SERVICE = 'spinnaker.core.application.read.service';

module(APPLICATION_READ_SERVICE, [ROBOT_TO_HUMAN_FILTER, require('@uirouter/angularjs').default]).service(
  'applicationReader',
  ApplicationReader,
);
