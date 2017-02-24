import {module} from 'angular';

class AppengineConfigFileConfigurerCtrl implements ng.IComponentController {
  public command: {configFiles: string[]};

  public $onInit(): void {
    if (!this.command.configFiles) {
      this.command.configFiles = [];
    }
  }

  public addConfigFile(): void {
    this.command.configFiles.push('');
  }

  public deleteConfigFile(index: number): void {
    this.command.configFiles.splice(index, 1);
  }

  public mapTabToSpaces(event: any) {
    if (event.which === 9) {
      event.preventDefault();
      let cursorPosition = event.target.selectionStart;
      let inputValue = event.target.value;
      event.target.value = `${inputValue.substring(0, cursorPosition)}  ${inputValue.substring(cursorPosition)}`;
      event.target.selectionStart += 2;
    }
  }
}

class AppengineConfigFileConfigurerComponent implements ng.IComponentOptions {
  public bindings: any = {command: '='};
  public controller: any = AppengineConfigFileConfigurerCtrl;
  public template = `
    <div class="form-horizontal container-fluid">
      <div class="form-group">
        <div class="col-md-3 sm-label-right">
          Application Root 
          <help-field class="help-field-absolute" key="appengine.serverGroup.applicationDirectoryRoot"></help-field>
        </div>
        <div class="col-md-7">
          <input type="text"
                 class="form-control input-sm"
                 name="applicationDirectoryRoot"
                 ng-model="$ctrl.command.applicationDirectoryRoot"/></div>
      </div>
  
      <div class="form-group">
        <div class="col-md-3 sm-label-right">
          Config Filepaths <help-field key="appengine.serverGroup.configFilepaths"></help-field>
        </div>
        <div class="col-md-7">
          <ui-select multiple tagging tagging-label="" style="width: 380px;"
                     name="configFilepaths" ng-model="$ctrl.command.configFilepaths" class="form-control input-sm">
            <ui-select-match>{{$item}}</ui-select-match>
            <ui-select-choices repeat="filepath in []">
              <span ng-bind-html="filepath"></span>
            </ui-select-choices>
          </ui-select>
        </div>
      </div>
 
      <div class="form-group">
        <div class="col-md-3 sm-label-right">
          Config Files <help-field key="appengine.serverGroup.configFiles"></help-field>
        </div>
        <div ng-repeat="configFile in $ctrl.command.configFiles track by $index" >
          <div class="col-md-7" ng-class="{'col-md-offset-3': $index > 0}" style="margin-top: 5px;">
            <textarea cols="46" rows="10"
                      class="small"
                      spellcheck="false"
                      ng-keydown="$ctrl.mapTabToSpaces($event)"
                      style="font-family: Menlo, Monaco, Consolas, 'Courier New', monospace;"
                      ng-model="$ctrl.command.configFiles[$index]"></textarea>
          </div>
          <div class="col-md-1" style="margin-top: 5px;">
            <button type="button" class="btn btn-sm btn-default" ng-click="$ctrl.deleteConfigFile($index)">
              <span class="glyphicon glyphicon-trash"></span> Delete
            </button>
          </div>
        </div>
        <div class="col-md-7" ng-class="{'col-md-offset-3': $ctrl.command.configFiles.length > 0}">
          <button class="btn btn-block btn-add-trigger add-new" ng-click="$ctrl.addConfigFile()">
            <span class="glyphicon glyphicon-plus-sign"></span> Add Config File
          </button>
        </div>
      </div>
    </div>
  `;
}

export const APPENGINE_CONFIG_FILE_CONFIGURER = 'spinnaker.appengine.configFileConfigurer.component';
module(APPENGINE_CONFIG_FILE_CONFIGURER, [])
  .component('appengineConfigFileConfigurer', new AppengineConfigFileConfigurerComponent());
