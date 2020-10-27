
import { $q } from 'ngimport';

import { API } from '@spinnaker/core';
export interface ITencentcloudSnapshot {
  diskSize: string;
  diskType: string;
  diskUsage: 'SYSTEM_DISK' | 'DATA_DISK';
  snapshotId: string;
}
export interface ITencentcloudImage {
  accounts: string[];
  images: {
    [region: string]: string[];
  };
  imgIds: {
    [region: string]: string[];
  };
  attributes: {
    createdTime?: string;
    creationDate?: string;
    snapshotSet?: ITencentcloudSnapshot[];
    osPlatform: string;
  };
  imageName: string;
  tags: {
    [tag: string]: string;
  };
  tagsByImageId: {
    [imageId: string]: ITencentcloudImage['tags'];
  };
}

export class TencentcloudImageReader {
  public findImages(params: { q: string; region?: string }): PromiseLike<ITencentcloudImage[]> {
    if (!params.q || params.q.length < 3) {
      return $q.when([{ message: 'Please enter at least 3 characters...', disabled: true }]) as any;
    }

    return API.one('images', 'find')
      .withParams({ ...params, provider: 'tencentcloud' })
      .get()
      .catch(() => [] as ITencentcloudImage[]);
  }

  public getImage(name: string, region: string, credentials: string): PromiseLike<ITencentcloudImage> {
    return API.one('images')
      .one(credentials)
      .one(region)
      .one(name)
      .withParams({ provider: 'tencentcloud' })
      .get()
      .then((results: any[]) => (results && results.length ? results[0] : null))
      .catch(() => null as ITencentcloudImage);
  }
}
