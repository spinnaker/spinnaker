export interface IDockerImageParts {
  organization: string;
  repository: string;
  tag?: string;
  digest?: string;
}

export class DockerImageUtils {
  // Split the image id up into the selectable parts to feed the UI
  public static splitImageId(imageId: string): IDockerImageParts {
    const parts = imageId.split('/');
    const organization = parts.length > 1 ? parts.shift() : '';
    const rest = parts.shift().split(':');
    const repository = organization.length > 0 ? `${organization}/${rest.shift()}` : rest.shift();

    const lookup = rest.shift();
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

    return `${parts.repository}:${parts.digest ? parts.digest : parts.tag}`;
  }
}
