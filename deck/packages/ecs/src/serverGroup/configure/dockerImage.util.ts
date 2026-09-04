import type { IEcsDockerImage } from './serverGroupConfiguration.service';

export function buildEcsImageId(image: any): string {
  if (image.imageId) {
    return image.imageId;
  }
  if (image.fromContext) {
    return image.imageLabelOrSha;
  }
  if (image.fromTrigger && !image.tag) {
    return `${image.registry}/${image.repository} (Tag resolved at runtime)`;
  }
  if (image.registry && image.repository && image.tag) {
    return `${image.registry}/${image.repository}:${image.tag}`;
  }
  return image.reference || image.repository || '';
}

export function normalizeEcsDockerImage(image: any): IEcsDockerImage {
  return {
    ...image,
    imageId: buildEcsImageId(image),
    message: image.message || '',
    fromTrigger: !!image.fromTrigger,
    fromContext: !!image.fromContext,
    stageId: image.stageId || '',
    imageLabelOrSha: image.imageLabelOrSha || '',
  };
}
