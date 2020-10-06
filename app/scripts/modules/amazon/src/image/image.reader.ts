import { IPromise } from 'angular';
import { $q } from 'ngimport';

import { API } from '@spinnaker/core';

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
  public findImages(params: { q: string; region?: string }): IPromise<IAmazonImage[]> {
    if (!params.q || params.q.length < 3) {
      return $q.when([{ message: 'Please enter at least 3 characters...', disabled: true }]) as any;
    }

    return API.one('images/find')
      .withParams(params)
      .get()
      .catch(() => [] as IAmazonImage[]);
  }

  public getImage(amiName: string, region: string, credentials: string): IPromise<IAmazonImage> {
    return API.one('images')
      .one(credentials)
      .one(region)
      .one(amiName)
      .withParams({ provider: 'aws' })
      .get()
      .then((results: any[]) => (results && results.length ? results[0] : null))
      .catch(() => null as IAmazonImage);
  }
}
