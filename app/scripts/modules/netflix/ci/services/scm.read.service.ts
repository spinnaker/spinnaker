import {IPromise, module} from 'angular';

import {Api, API_SERVICE} from 'core/api/api.service';

export interface IOrganization {
  id: string;
  displayName: string;
}

export interface IRepository {
  name: string;
}

export interface ICommit {
  id: string;
  displayId: string;
  message: string;
  author: {username: string, email: string};
  authoredTs: number;
}

export interface IBranch {
  id: string;
  displayId: string;
  latestCommitId: string;
  'default': boolean;
}

export interface ITag {
  id: string;
  displayId: string;
  commitId: string;
}

export class ScmReader {
  constructor(private API: Api) { 'ngInject'; }

  public getOrganizations(type = 'stash', limit = 1000): IPromise<IOrganization[]> {
    return this.API.all('scm', type).withParams({limit}).getList().then((r: any) => r.data);
  }

  public getRepositories(org: string, type = 'stash', limit = 1000): IPromise<IRepository[]> {
    return this.API.all('scm', type, 'orgs', org, 'repos').withParams({limit}).getList().then((r: any) => r.data);
  }

  public getCommits(org: string, repository: string, type = 'stash', limit = 250): IPromise<ICommit[]> {
    return this.API.all('scm', type, 'orgs', org, 'repos', repository, 'commits').withParams({limit}).getList().then((r: any) => r.data);
  }

  public getBranches(org: string, repository: string, type = 'stash', limit = 100): IPromise<IBranch[]> {
    return this.API.all('scm', type, 'orgs', org, 'repos', repository, 'branches').withParams({limit}).getList().then((r: any) => r.data);
  }

  public getTags(org: string, repository: string, type = 'stash', limit = 200): IPromise<ITag[]> {
    return this.API.all('scm', type, 'orgs', org, 'repos', repository, 'tags').withParams({limit}).getList().then((r: any) => r.data);
  }
}

export const SCM_READ_SERVICE = 'spinnaker.netflix.ci.scm.read.service';
module(SCM_READ_SERVICE, [API_SERVICE]).service('scmReader', ScmReader);
