import { IEntityTag } from 'core/domain';

export interface IEntitySourceProps {
  metadata: IEntityTag;
  relativePath?: string;
}
