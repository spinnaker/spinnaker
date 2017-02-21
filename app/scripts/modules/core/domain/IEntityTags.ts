export interface IEntityTagsMetadata {
  name: string;
  created: number;
  lastModified: number;
  createdBy: string;
  lastModifiedBy: string;
}

export interface ICreationMetadata {
  executionType: 'orchestration' | 'pipeline';
  stageId?: string;
  executionId?: string;
  pipelineConfigId?: string;
  application?: string;
  user?: string;
  description?: string;
  comments?: string;
}

export interface ICreationMetadataTag extends IEntityTag {
  value: ICreationMetadata;
}

export interface IEntityTag {
  name: string;
  value: any;
  created?: number;
  lastModified?: number;
  createdBy?: string;
  lastModifiedBy?: string;
}

export interface IEntityTags {
  id: string;
  idPattern?: string;
  lastModified?: number;
  lastModifiedBy?: string;
  tags: IEntityTag[];
  tagsMetadata: IEntityTagsMetadata[];
  entityRef: IEntityRef;
  alerts: IEntityTag[];
  notices: IEntityTag[];
  creationMetadata?: ICreationMetadataTag;
}

export interface IEntityRef {
  cloudProvider: string;
  entityType: string;
  entityId: string;
  [attribute: string]: any;
}
