import { stringify } from 'yaml';
import { StoredFile } from './stored_file';

export abstract class StoredYml extends StoredFile {
  override toString(): string {
    return stringify(this);
  }
}
