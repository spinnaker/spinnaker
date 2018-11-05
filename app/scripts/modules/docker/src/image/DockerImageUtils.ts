export interface IDockerImageParts {
  organization: string;
  repository: string;
  tag?: string;
}

export class DockerImageUtils {
  // Split the image id up into the selectable parts to feed the UI
  public static splitImageId(imageId: string): IDockerImageParts {
    const parts = imageId.split('/');
    const organization = parts.length > 1 ? parts.shift() : '';
    const rest = parts.shift().split(':');
    const repository = organization.length > 0 ? `${organization}/${rest.shift()}` : rest.shift();
    const tag = rest.shift();

    return { organization, repository, tag };
  }
}
