import {module} from 'angular';

import {Application} from '../../application.model';
import {ApplicationDataSource} from '../../service/applicationDataSource';
import {APPLICATION_WRITE_SERVICE, ApplicationWriter} from 'core/application/service/application.write.service';

import './applicationDataSourceEditor.component.less';

export class DataSourceEditorController implements ng.IComponentController {

  public application: Application;

  public model: any = {};
  public explicitlyEnabled: string[] = [];
  public explicitlyDisabled: string[] = [];
  public isDirty = false;
  public saving = false;
  public saveError = false;
  public original: string;

  public dataSources: ApplicationDataSource[];

  constructor(private applicationWriter: ApplicationWriter) { 'ngInject'; }

  public $onInit() {
    if (this.application.notFound) {
      return;
    }
    if (!this.application.attributes) {
      this.application.attributes = {};
    }
    if (!this.application.attributes.dataSources) {
      this.application.attributes.dataSources = { enabled: [], disabled: [] };
    }
    this.dataSources = this.application.dataSources.filter(ds => ds.visible && ds.optional);
    this.explicitlyEnabled = this.application.attributes.dataSources.enabled;
    this.explicitlyDisabled = this.application.attributes.dataSources.disabled;
    this.dataSources.forEach(ds => {
      this.model[ds.key] = !ds.disabled;
    });
    this.original = JSON.stringify(this.model);
  }

  public dataSourceChanged(key: string): void {
    if (this.model[key]) {
      if (!this.explicitlyEnabled.includes(key)) {
        this.explicitlyEnabled.push(key);
      }
      this.explicitlyDisabled = this.explicitlyDisabled.filter(s => s !== key);
    } else {
      if (!this.explicitlyDisabled.includes(key)) {
        this.explicitlyDisabled.push(key);
      }
      this.explicitlyEnabled = this.explicitlyEnabled.filter(s => s !== key);
    }
    this.isDirty = JSON.stringify(this.model) !== this.original;
  };

  public revert() {
    this.$onInit();
  };

  public save() {
    this.saving = true;
    this.saveError = false;
    const newDataSources = { enabled: this.explicitlyEnabled, disabled: this.explicitlyDisabled };
    this.applicationWriter.updateApplication({
      name: this.application.name,
      accounts: this.application.attributes.accounts,
      dataSources: newDataSources,
    })
      .then(() => {
        this.application.attributes.dataSources = newDataSources;
        this.explicitlyEnabled
          .filter(k => this.application.getDataSource(k))
          .forEach(key => this.application.getDataSource(key).disabled = false);
        this.explicitlyDisabled
          .filter(k => this.application.getDataSource(k))
          .forEach(key => this.application.getDataSource(key).disabled = true);
        this.application.refresh(true);
        this.saving = false;
        this.isDirty = false;
        this.$onInit();
      }, () => {
        this.saving = false;
        this.saveError = true;
      });
  }
}

class ApplicationDataSourceEditorComponent implements ng.IComponentOptions {
  public bindings: any = {
    application: '='
  };
  public controller: any = DataSourceEditorController;
  public templateUrl: string = require('./applicationDataSourceEditor.component.html');
}

export const APPLICATION_DATA_SOURCE_EDITOR = 'spinnaker.core.application.config.applicationDataSourceEditor';
module(APPLICATION_DATA_SOURCE_EDITOR, [
  APPLICATION_WRITE_SERVICE
])
  .component('applicationDataSourceEditor', new ApplicationDataSourceEditorComponent());
