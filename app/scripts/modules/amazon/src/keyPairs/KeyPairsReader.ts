import { IKeyPair } from 'amazon/domain';

import { REST } from '@spinnaker/core';

export class KeyPairsReader {
  public static listKeyPairs(): PromiseLike<IKeyPair[]> {
    return REST('/keyPairs')
      .useCache()
      .get()
      .then((keyPairs: IKeyPair[]) => keyPairs.sort((a, b) => a.keyName.localeCompare(b.keyName)));
  }
}
