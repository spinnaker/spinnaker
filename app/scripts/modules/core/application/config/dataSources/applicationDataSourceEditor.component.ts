import {Application} from '../../application.model.ts';
import {ApplicationDataSource} from '../../service/applicationDataSource.ts';

import './applicationDataSourceEditor.component.less';

export class DataSourceEditorController implements ng.IComponentController {

  static get $inject() { return ['applicationWriter']; }

  public application: Application;

  public model: any = {};
  public explicitlyEnabled: string[] = [];
  public explicitlyDisabled: string[] = [];
  public isDirty: boolean = false;
  public saving: boolean = false;
  public saveError: boolean = false;
  public original: string;

  public dataSources: ApplicationDataSource[];

  constructor(private applicationWriter: any) {}

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

  public dataSourceChanged(key) {
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
    let newDataSources = { enabled: this.explicitlyEnabled, disabled: this.explicitlyDisabled };
    this.applicationWriter.updateApplication({
      name: this.application.name,
      accounts: this.application.attributes.accounts,
      dataSources: newDataSources,
    })
      .then(() => {
        this.application.attributes.dataSources = newDataSources;
        this.explicitlyEnabled.forEach(key => this.application.getDataSource(key).disabled = false);
        this.explicitlyDisabled.forEach(key => this.application.getDataSource(key).disabled = true);
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
  public controller: ng.IComponentController = DataSourceEditorController;
  public templateUrl: string = require('./applicationDataSourceEditor.component.html');

}

const moduleName = 'spinnaker.core.application.config.applicationDataSourceEditor';

angular.module(moduleName, [
  require('../../service/applications.write.service.js')
])
  .component('applicationDataSourceEditor', new ApplicationDataSourceEditorComponent());

export default moduleName;
