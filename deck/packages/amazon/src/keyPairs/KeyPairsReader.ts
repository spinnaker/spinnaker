import { REST } from '@spinnaker/core';
import type { IKeyPair } from '../domain';

export class KeyPairsReader {
  public static listKeyPairs(): Promise<IKeyPair[]> {
    return REST('/keyPairs')
      .useCache()
      .get()
      .then((keyPairs: IKeyPair[]) => keyPairs.sort((a, b) => a.keyName.localeCompare(b.keyName)));
  }
}
