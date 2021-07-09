import { IController, module } from 'angular';

import { Application } from '../../application.model';
import { TaskReader } from '../../../index';
import { ApplicationReader } from '../../service/ApplicationReader';
import { ApplicationWriter } from '../../service/ApplicationWriter';
import { ApplicationDataSource } from '../../service/applicationDataSource';

import './applicationDataSourceEditor.component.less';

export class DataSourceEditorController implements IController {
  public application: Application;

  public model: any = {};
  public explicitlyEnabled: string[] = [];
  public explicitlyDisabled: string[] = [];
  public isDirty = false;
  public saving = false;
  public saveError = false;
  public original: string;

  public dataSources: ApplicationDataSource[];

  public $onInit() {
    if (this.application.notFound || this.application.hasError) {
      return;
    }
    if (!this.application.attributes) {
      this.application.attributes = {};
    }
    if (!this.application.attributes.dataSources) {
      this.application.attributes.dataSources = { enabled: [], disabled: [] };
    }
    this.dataSources = this.application.dataSources.filter((ds) => ds.visible && ds.optional && !ds.hidden);
    this.explicitlyEnabled = this.application.attributes.dataSources.enabled;
    this.explicitlyDisabled = this.application.attributes.dataSources.disabled;
    this.dataSources.forEach((ds) => {
      this.model[ds.key] = !ds.disabled;
    });
    this.original = JSON.stringify(this.model);
  }

  public dataSourceChanged(key: string): void {
    if (this.model[key]) {
      if (!this.explicitlyEnabled.includes(key)) {
        this.explicitlyEnabled.push(key);
      }
      this.explicitlyDisabled = this.explicitlyDisabled.filter((s) => s !== key);
    } else {
      if (!this.explicitlyDisabled.includes(key)) {
        this.explicitlyDisabled.push(key);
      }
      this.explicitlyEnabled = this.explicitlyEnabled.filter((s) => s !== key);
    }
    this.isDirty = JSON.stringify(this.model) !== this.original;
  }

  public revert() {
    this.$onInit();
  }

  public save() {
    this.saving = true;
    this.saveError = false;
    const newDataSources = { enabled: this.explicitlyEnabled, disabled: this.explicitlyDisabled };
    ApplicationWriter.updateApplication({
      name: this.application.name,
      accounts: this.application.attributes.accounts,
      dataSources: newDataSources,
    })
      .then((task) => {
        return TaskReader.waitUntilTaskCompletes(task);
      })
      .then(
        () => {
          this.application.attributes.dataSources = newDataSources;
          ApplicationReader.setDisabledDataSources(this.application);
          this.application.refresh(true);
          this.saving = false;
          this.isDirty = false;
          this.$onInit();
        },
        () => {
          this.saving = false;
          this.saveError = true;
        },
      );
  }
}

const applicationDataSourceEditorComponent: ng.IComponentOptions = {
  bindings: {
    application: '=',
  },
  controller: DataSourceEditorController,
  templateUrl: require('./applicationDataSourceEditor.component.html'),
};

export const APPLICATION_DATA_SOURCE_EDITOR = 'spinnaker.core.application.config.applicationDataSourceEditor';
module(APPLICATION_DATA_SOURCE_EDITOR, []).component(
  'applicationDataSourceEditor',
  applicationDataSourceEditorComponent,
);
