import { module } from 'angular';

export interface IDockerImage {
  fromFindImage: boolean;
  fromBake: boolean;
  repository: string;
  tag: string;
  registry: string;
  cluster: string;
  pattern: string;
  fromTrigger: boolean;
}

export function imageId(image: IDockerImage): string {
  if (image.fromFindImage) {
    return `${image.cluster} ${image.pattern}`;
  } else if (image.fromBake) {
    return `${image.repository} (Baked during execution)`;
  } else if (image.fromTrigger && !image.tag) {
    return `${image.registry}/${image.repository} (Tag resolved at runtime)`;
  } else {
    if (image.registry) {
      return `${image.registry}/${image.repository}:${image.tag}`;
    } else {
      return `${image.repository}:${image.tag}`;
    }
  }
}

export function imageIdFilter() {
  return imageId;
}

export const KUBERNETES_IMAGE_ID_FILTER = 'spinnaker.kubernetes.presentation.imageId.filter';
module(KUBERNETES_IMAGE_ID_FILTER, [])
  .filter('kubernetesImageId', imageIdFilter);

