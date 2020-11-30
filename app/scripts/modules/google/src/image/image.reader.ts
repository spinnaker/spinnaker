import { REST } from '@spinnaker/core';

export interface IGceImage {
  imageName: string;
}

export class GceImageReader {
  public static findImages(params: { account?: string; provider?: string; q?: string }): PromiseLike<IGceImage[]> {
    return REST('/images/find')
      .query(params)
      .get()
      .catch(() => [] as IGceImage[]);
  }

  public static getImage(/*amiName: string, region: string, credentials: string*/): PromiseLike<IGceImage> {
    // GCE images are not regional so we don't need to retrieve ids scoped to regions.
    return null;
  }
}
