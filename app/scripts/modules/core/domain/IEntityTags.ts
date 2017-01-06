export interface IEntityTagsMetadata {
  name: string;
  created: number;
  lastModified: number;
  createdBy: string;
  lastModifiedBy: string;
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
}

export interface IEntityRef {
  cloudProvider: string;
  entityType: string;
  entityId: string;
  [attribute: string]: any;
}
