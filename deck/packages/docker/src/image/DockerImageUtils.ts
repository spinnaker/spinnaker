export interface IDockerImageParts {
  organization: string;
  repository: string;
  tag?: string;
  digest?: string;
}

export class DockerImageUtils {
  // Split the image id up into the selectable parts to feed the UI
  public static splitImageId(imageId = ''): IDockerImageParts {
    let imageParts: string[];
    if (imageId.includes('@')) {
      imageParts = imageId.split('@');
    } else {
      imageParts = imageId.split(':');
    }
    const repository = imageParts[0];
    const repositoryParts = repository.split('/');
    // Everything before the last slash is considered the organization
    const organization = repositoryParts.slice(0, -1).join('/');

    const lookup = imageParts.length > 1 ? imageParts.slice(1).join(':') : '';

    let tag: string;
    let digest: string;
    if (lookup) {
      if (lookup.startsWith('sha256:')) {
        digest = lookup;
      } else {
        tag = lookup;
      }
    }

    return { organization, repository, digest, tag };
  }

  public static generateImageId(parts: IDockerImageParts): string {
    if (!parts.repository || !(parts.digest || parts.tag)) {
      return undefined;
    }

    let imageId: string;

    if (parts.digest) {
      imageId = `${parts.repository}@${parts.digest}`;
    } else {
      imageId = `${parts.repository}:${parts.tag}`;
    }

    return imageId;
  }
}
