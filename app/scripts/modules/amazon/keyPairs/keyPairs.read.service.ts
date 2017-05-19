import { module, IPromise } from 'angular';

import { API_SERVICE, Api } from '@spinnaker/core';

import { IKeyPair } from 'amazon/domain';

export class KeyPairsReader {

  constructor(private API: Api) {
    'ngInject';
  }

  public listKeyPairs(): IPromise<IKeyPair[]> {
    return this.API.all('keyPairs').useCache().getList()
      .then((keyPairs: IKeyPair[]) => keyPairs.sort((a, b) => a.keyName.localeCompare(b.keyName)));
  }
}

export const KEY_PAIRS_READ_SERVICE = 'spinnaker.amazon.keyPairs.read.service';
module(KEY_PAIRS_READ_SERVICE, [API_SERVICE]).service('keyPairsReader', KeyPairsReader);
