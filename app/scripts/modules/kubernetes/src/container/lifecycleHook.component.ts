import { IComponentOptions, IController, module } from 'angular';

interface IKubernetesHandler {
  type: 'EXEC' | 'HTTP';
  execAction?: IKubernetesExecAction;
  httpGetAction?: IKubernetesHttpGetAction;
}

interface IKubernetesExecAction {
  commands: string[];
}

interface IKubernetesHttpGetAction {
  path: string;
  port: number;
  host: string;
  uriScheme: string;
  httpHeaders: IKeyValuePair[];
}

interface IKeyValuePair {
  name: string;
  value: string;
}

class KubernetesLifecycleHookConfigurerCtrl implements IController {
  public heading: string;
  public handler: IKubernetesHandler;
  public execActionCommandsViewValue: string;
  private onHandlerChange: Function;
  private handlerEnabled = false;

  public $onInit(): void {
    this.handlerEnabled = !!this.handler;
    if (this.handlerEnabled) {
      this.initializeHandler();
    }
  }

  public toggleHandlerEnabled(): void {
    if (this.handlerEnabled) {
      this.initializeHandler();
    } else {
      this.handler = null;
      this.execActionCommandsViewValue = null;
      this.onHandlerChange({ handler: null });
    }
  }

  public handlerTypeModelToViewValue(handlerTypeModelValue: 'EXEC' | 'HTTP'): 'exec' | 'httpGet' {
    switch (handlerTypeModelValue) {
      case 'EXEC':
        return 'exec';
      case 'HTTP':
        return 'httpGet';
    }
  }

  public onExecActionCommandsViewValueChange(): void {
    this.handler.execAction.commands = this.execActionCommandsViewValue.split(' ');
  }

  public onHandlerTypeChange(): void {
    switch (this.handler.type) {
      case 'EXEC':
        this.handler.execAction = { commands: [] };
        delete this.handler.httpGetAction;
        break;
      case 'HTTP':
        this.handler.httpGetAction = { path: '/', port: null, host: null, uriScheme: 'HTTP', httpHeaders: [] };
        delete this.handler.execAction;
        delete this.execActionCommandsViewValue;
        break;
    }
  }

  public addHttpHeader(): void {
    this.handler.httpGetAction.httpHeaders.push({ name: '', value: '' });
  }

  public deleteHttpHeader(index: number): void {
    this.handler.httpGetAction.httpHeaders.splice(index, 1);
    if (!this.handler.httpGetAction.httpHeaders.length) {
      delete this.handler.httpGetAction.httpHeaders;
    }
  }

  private initializeHandler(): void {
    if (!this.handler) {
      this.handler = { type: 'EXEC', execAction: { commands: [] } };
      this.onHandlerChange({ handler: this.handler });
    } else if (this.handler.type === 'EXEC') {
      this.execActionCommandsViewValue = this.handler.execAction.commands.join(' ');
    }
  }
}

const kubernetesLifecycleHookConfigurer: IComponentOptions = {
  bindings: {
    heading: '@',
    handler: '<',
    onHandlerChange: '&',
  },
  templateUrl: require('./lifecycleHook.component.html'),
  controller: KubernetesLifecycleHookConfigurerCtrl,
  transclude: true
};

export const KUBERNETES_LIFECYCLE_HOOK_CONFIGURER = 'spinnaker.kubernetes.lifecycleHookConfigurer.component';
module(KUBERNETES_LIFECYCLE_HOOK_CONFIGURER, []).component(
  'kubernetesLifecycleHookConfigurer',
  kubernetesLifecycleHookConfigurer,
);
