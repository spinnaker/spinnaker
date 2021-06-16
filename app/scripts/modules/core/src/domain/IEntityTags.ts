export interface IEntityTagsMetadata {
  name: string;
  created: number;
  lastModified: number;
  createdBy: string;
  lastModifiedBy: string;
}

export interface ICreationServerGroup {
  name: string;
  imageId: string;
  imageName: string;
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
  previousServerGroup?: ICreationServerGroup;
}

export interface IEntityTag {
  name: string;
  value: any;
  created?: number;
  lastModified?: number;
  createdBy?: string;
  lastModifiedBy?: string;
  namespace?: string;
  category?: string;
}

export interface ICreationMetadataTag extends IEntityTag {
  value: ICreationMetadata;
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
  [attribute: string]: any;
  account?: string;
  accountId?: string;
  cloudProvider?: string;
  entityId: string;
  entityType: string;
  region?: string;
  vpcId?: string;
}
