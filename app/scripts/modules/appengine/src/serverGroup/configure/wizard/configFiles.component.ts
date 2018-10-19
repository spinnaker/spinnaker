import { IController, module } from 'angular';
import { AppengineSourceType } from '../serverGroupCommandBuilder.service';

class AppengineConfigFileConfigurerCtrl implements IController {
  public command: { configFiles: string[]; sourceType: string };

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
      const cursorPosition = event.target.selectionStart;
      const inputValue = event.target.value;
      event.target.value = `${inputValue.substring(0, cursorPosition)}  ${inputValue.substring(cursorPosition)}`;
      event.target.selectionStart += 2;
    }
  }

  public isContainerImageSource(): boolean {
    return this.command.sourceType === AppengineSourceType.CONTAINER_IMAGE;
  }
}

class AppengineConfigFileConfigurerComponent implements ng.IComponentOptions {
  public bindings: any = { command: '=' };
  public controller: any = AppengineConfigFileConfigurerCtrl;
  public templateUrl = require('./configFiles.component.html');
}

export const APPENGINE_CONFIG_FILE_CONFIGURER = 'spinnaker.appengine.configFileConfigurer.component';
module(APPENGINE_CONFIG_FILE_CONFIGURER, []).component(
  'appengineConfigFileConfigurer',
  new AppengineConfigFileConfigurerComponent(),
);
