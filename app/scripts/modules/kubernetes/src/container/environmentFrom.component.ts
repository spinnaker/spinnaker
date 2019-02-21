import { IComponentOptions, IController, module } from 'angular';

interface IKubernetesEnvironmentFrom {
  prefix: string;
  configMapRef?: IKubernetesConfigMapRef;
  secretRef?: IKubernetesSecretRef;
}

interface IKubernetesConfigMapRef {
  name: string;
  optional: boolean;
}

interface IKubernetesSecretRef {
  name: string;
  optional: boolean;
}

const CONFIGMAP_TYPE = 'Config Map';
const SECRET_TYPE = 'Secret';
const DEFAULT_ENVFROM_TYPE = CONFIGMAP_TYPE;

class KubernetesEnvironmentFromCtrl implements IController {
  public envFrom: IKubernetesEnvironmentFrom[];
  public envFromSourceTypes: string[];
  public sourceTypes: string[] = [CONFIGMAP_TYPE, SECRET_TYPE];

  public $onInit(): void {
    if (!this.envFrom) {
      this.envFrom = [];
    }

    this.mapEnvFromSourceTypes();
  }

  public mapEnvFromSourceTypes(): void {
    this.envFromSourceTypes = this.envFrom.map(envFrom => {
      if (envFrom.configMapRef) {
        return CONFIGMAP_TYPE;
      } else {
        return SECRET_TYPE;
      }
    });
  }

  public removeEnvFrom(index: number): void {
    this.envFrom.splice(index, 1);
    this.envFromSourceTypes.splice(index, 1);
  }

  public addEnvFrom(): void {
    this.envFrom.push({ prefix: '' });
    this.envFromSourceTypes.push(DEFAULT_ENVFROM_TYPE);
  }

  public updateEnvFrom(index: number): void {
    const envFrom = this.envFrom[index];
    const sourceType = this.envFromSourceTypes[index];
    switch (sourceType) {
      case CONFIGMAP_TYPE:
        delete envFrom.secretRef;
        break;
      case SECRET_TYPE:
        delete envFrom.configMapRef;
        break;
    }
  }

  public initOptional(source: any): boolean {
    return (source && source.optional == null) || source.optional;
  }
}

const kubernetesContainerEnvironmentFrom: IComponentOptions = {
  bindings: {
    envFrom: '=',
  },
  templateUrl: require('./environmentFrom.component.html'),
  controller: KubernetesEnvironmentFromCtrl
};

export const KUBERNETES_CONTAINER_ENVIRONMENT_FROM = 'spinnaker.kubernetes.container.environmentFrom.component';
module(KUBERNETES_CONTAINER_ENVIRONMENT_FROM, []).component(
  'kubernetesContainerEnvironmentFrom',
  kubernetesContainerEnvironmentFrom,
);
