import { ApplicationDataSourceRegistry } from './ApplicationDataSourceRegistry';
import { InferredApplicationWarningService } from './InferredApplicationWarningService';
import { REST } from '../../api';
import { Application } from '../application.model';
import { SchedulerFactory } from '../../scheduler';
import { ApplicationDataSource } from '../service/applicationDataSource';

export interface IApplicationDataSourceAttribute {
  enabled: string[];
  disabled: string[]; ///./app/scripts/modules/core/src/pipeline/service/execution.service.ts;
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
  slackChannel?: { name: string };
  updateTs?: string;
}

export class ApplicationReader {
  public static listApplications(): PromiseLike<IApplicationSummary[]> {
    return REST('/applications').useCache().get();
  }

  public static getApplicationAttributes(name: string): PromiseLike<any> {
    return REST('/applications')
      .path(name)
      .query({ expand: false })
      .get()
      .then((fromServer: Application) => {
        this.splitAttributes(fromServer.attributes, ['accounts', 'cloudProviders']);
        return fromServer.attributes;
      });
  }

  public static getApplicationPermissions(applicationName: string): PromiseLike<any> {
    return REST('/applications')
      .path(applicationName)
      .query({
        expand: false,
      })
      .get()
      .then((application: Application) => {
        return application.attributes.permissions;
      });
  }

  public static getApplication(name: string, expand = true): PromiseLike<Application> {
    return REST('/applications')
      .path(name)
      .query({ expand: expand })
      .get()
      .then((fromServer: Application) => {
        const configs = ApplicationDataSourceRegistry.getDataSources();
        const application: Application = new Application(fromServer.name, SchedulerFactory.createScheduler(), configs);
        application.attributes = fromServer.attributes;
        this.splitAttributes(application.attributes, ['accounts', 'cloudProviders']);
        this.setDisabledDataSources(application);
        application.refresh();
        return application;
      });
  }

  private static splitAttributes(attributes: any, fields: string[]) {
    fields.forEach((field) => {
      if (attributes[field]) {
        if (!Array.isArray(attributes[field])) {
          attributes[field] = attributes[field].split(',').map((s: string) => s.trim());
        }
      } else {
        attributes[field] = [];
      }
    });
  }

  public static setDisabledDataSources(application: Application) {
    const allDataSources = application.dataSources;
    const appDataSources: IApplicationDataSourceAttribute = application.attributes.dataSources;

    if (!appDataSources) {
      allDataSources.filter((ds) => ds.optIn).forEach((ds) => this.setDataSourceDisabled(ds, application, true));
      if (InferredApplicationWarningService.isInferredApplication(application)) {
        allDataSources
          .filter((ds) => ds.requireConfiguredApp)
          .forEach((ds) => this.setDataSourceDisabled(ds, application, true));
      }
    } else {
      allDataSources.forEach((ds) => {
        if (ds.optional) {
          if (ds.optIn) {
            this.setDataSourceDisabled(ds, application, !appDataSources.enabled.includes(ds.key));
          } else {
            this.setDataSourceDisabled(ds, application, appDataSources.disabled.includes(ds.key));
          }
        }
      });
    }
    allDataSources
      .filter((ds) => ds.requiresDataSource)
      .forEach((ds) => {
        const parent = allDataSources.find((p) => p.key === ds.requiresDataSource);
        if (parent) {
          this.setDataSourceDisabled(ds, application, parent.disabled);
        }
      });
  }

  private static setDataSourceDisabled(dataSource: ApplicationDataSource, application: Application, disabled: boolean) {
    dataSource.disabled = disabled;
    if (dataSource.badge) {
      application.dataSources.find((ds) => ds.key === dataSource.badge).disabled = disabled;
    }
  }
}
