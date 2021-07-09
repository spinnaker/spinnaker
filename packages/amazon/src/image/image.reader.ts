import { $q } from 'ngimport';

import { REST } from '@spinnaker/core';

export interface IAmazonImage {
  accounts: string[];
  amis: {
    [region: string]: string[];
  };
  attributes: {
    creationDate: string;
    virtualizationType: string;
  };
  imageName: string;
  tags: {
    [tag: string]: string;
  };
  tagsByImageId: {
    [imageId: string]: IAmazonImage['tags'];
  };
}

export class AwsImageReader {
  public findImages(params: { q: string; region?: string }): PromiseLike<IAmazonImage[]> {
    if (!params.q || params.q.length < 3) {
      return $q.when([{ message: 'Please enter at least 3 characters...', disabled: true }]) as any;
    }

    return REST('/images/find')
      .query(params)
      .get()
      .catch(() => [] as IAmazonImage[]);
  }

  public getImage(amiName: string, region: string, credentials: string): PromiseLike<IAmazonImage> {
    return REST('/images')
      .path(credentials, region, amiName)
      .query({ provider: 'aws' })
      .get()
      .then((results: any[]) => (results && results.length ? results[0] : null))
      .catch(() => null as IAmazonImage);
  }
}
