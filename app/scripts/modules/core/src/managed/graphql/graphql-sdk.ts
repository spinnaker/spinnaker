/* eslint-disable @typescript-eslint/array-type */
import { gql } from '@apollo/client';
import * as Apollo from '@apollo/client';
export type Maybe<T> = T | undefined;
export type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] };
export type MakeOptional<T, K extends keyof T> = Omit<T, K> & { [SubKey in K]?: Maybe<T[SubKey]> };
export type MakeMaybe<T, K extends keyof T> = Omit<T, K> & { [SubKey in K]: Maybe<T[SubKey]> };
const defaultOptions = {};
/** All built-in and custom scalars, mapped to their actual values */
export interface Scalars {
  ID: string;
  String: string;
  Boolean: boolean;
  Int: number;
  Float: number;
  InstantTime: string;
  JSON: any;
}

export interface MdAction {
  __typename?: 'MdAction';
  id: Scalars['String'];
  type: Scalars['String'];
  status: MdActionStatus;
  startedAt?: Maybe<Scalars['InstantTime']>;
  completedAt?: Maybe<Scalars['InstantTime']>;
  link?: Maybe<Scalars['String']>;
  actionType: MdActionType;
}

export type MdActionStatus = 'NOT_EVALUATED' | 'PENDING' | 'PASS' | 'FAIL' | 'FORCE_PASS';

export type MdActionType = 'VERIFICATION' | 'POST_DEPLOY';

export interface MdApplication {
  __typename?: 'MdApplication';
  id: Scalars['String'];
  name: Scalars['String'];
  account: Scalars['String'];
  isPaused?: Maybe<Scalars['Boolean']>;
  environments: Array<MdEnvironment>;
}

export interface MdArtifact {
  __typename?: 'MdArtifact';
  id: Scalars['String'];
  environment: Scalars['String'];
  name: Scalars['String'];
  type: Scalars['String'];
  reference: Scalars['String'];
  versions?: Maybe<Array<MdArtifactVersionInEnvironment>>;
  pinnedVersion?: Maybe<MdPinnedVersion>;
}

export interface MdArtifactVersionsArgs {
  statuses?: Maybe<Array<MdArtifactStatusInEnvironment>>;
  versions?: Maybe<Array<Scalars['String']>>;
  limit?: Maybe<Scalars['Int']>;
}

export type MdArtifactStatusInEnvironment =
  | 'PENDING'
  | 'APPROVED'
  | 'DEPLOYING'
  | 'CURRENT'
  | 'PREVIOUS'
  | 'VETOED'
  | 'SKIPPED';

export interface MdArtifactVersionActionPayload {
  application: Scalars['String'];
  environment: Scalars['String'];
  reference: Scalars['String'];
  comment: Scalars['String'];
  version: Scalars['String'];
}

export interface MdArtifactVersionInEnvironment {
  __typename?: 'MdArtifactVersionInEnvironment';
  id: Scalars['String'];
  version: Scalars['String'];
  buildNumber?: Maybe<Scalars['String']>;
  createdAt?: Maybe<Scalars['InstantTime']>;
  deployedAt?: Maybe<Scalars['InstantTime']>;
  resources?: Maybe<Array<MdResource>>;
  gitMetadata?: Maybe<MdGitMetadata>;
  environment?: Maybe<Scalars['String']>;
  reference: Scalars['String'];
  status?: Maybe<MdArtifactStatusInEnvironment>;
  lifecycleSteps?: Maybe<Array<MdLifecycleStep>>;
  constraints?: Maybe<Array<MdConstraint>>;
  verifications?: Maybe<Array<MdAction>>;
  postDeploy?: Maybe<Array<MdAction>>;
}

export interface MdCommitInfo {
  __typename?: 'MdCommitInfo';
  sha?: Maybe<Scalars['String']>;
  link?: Maybe<Scalars['String']>;
  message?: Maybe<Scalars['String']>;
}

export interface MdComparisonLinks {
  __typename?: 'MdComparisonLinks';
  toPreviousVersion?: Maybe<Scalars['String']>;
  toCurrentVersion?: Maybe<Scalars['String']>;
}

export interface MdConstraint {
  __typename?: 'MdConstraint';
  type: Scalars['String'];
  status: MdConstraintStatus;
  startedAt?: Maybe<Scalars['InstantTime']>;
  judgedAt?: Maybe<Scalars['InstantTime']>;
  judgedBy?: Maybe<Scalars['String']>;
  comment?: Maybe<Scalars['String']>;
  attributes?: Maybe<Scalars['JSON']>;
}

export type MdConstraintStatus = 'BLOCKED' | 'PENDING' | 'PASS' | 'FAIL' | 'FORCE_PASS';

export interface MdConstraintStatusPayload {
  application: Scalars['String'];
  environment: Scalars['String'];
  type: Scalars['String'];
  version: Scalars['String'];
  reference: Scalars['String'];
  status: MdConstraintStatus;
}

export interface MdEnvironment {
  __typename?: 'MdEnvironment';
  id: Scalars['String'];
  name: Scalars['String'];
  state: MdEnvironmentState;
}

export interface MdEnvironmentState {
  __typename?: 'MdEnvironmentState';
  id: Scalars['String'];
  resources?: Maybe<Array<MdResource>>;
  artifacts?: Maybe<Array<MdArtifact>>;
}

export interface MdGitMetadata {
  __typename?: 'MdGitMetadata';
  commit?: Maybe<Scalars['String']>;
  author?: Maybe<Scalars['String']>;
  project?: Maybe<Scalars['String']>;
  branch?: Maybe<Scalars['String']>;
  repoName?: Maybe<Scalars['String']>;
  pullRequest?: Maybe<MdPullRequest>;
  commitInfo?: Maybe<MdCommitInfo>;
  comparisonLinks?: Maybe<MdComparisonLinks>;
}

export type MdLifecycleEventScope = 'PRE_DEPLOYMENT';

export type MdLifecycleEventStatus = 'NOT_STARTED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'ABORTED' | 'UNKNOWN';

export type MdLifecycleEventType = 'BAKE' | 'BUILD';

export interface MdLifecycleStep {
  __typename?: 'MdLifecycleStep';
  scope?: Maybe<MdLifecycleEventScope>;
  type: MdLifecycleEventType;
  id?: Maybe<Scalars['String']>;
  status: MdLifecycleEventStatus;
  text?: Maybe<Scalars['String']>;
  link?: Maybe<Scalars['String']>;
  startedAt?: Maybe<Scalars['InstantTime']>;
  completedAt?: Maybe<Scalars['InstantTime']>;
  artifactVersion?: Maybe<Scalars['String']>;
}

export interface MdLocation {
  __typename?: 'MdLocation';
  account?: Maybe<Scalars['String']>;
  regions?: Maybe<Array<Scalars['String']>>;
}

export interface MdMarkArtifactVersionAsGoodPayload {
  application: Scalars['String'];
  environment: Scalars['String'];
  reference: Scalars['String'];
  version: Scalars['String'];
}

export interface MdMoniker {
  __typename?: 'MdMoniker';
  app?: Maybe<Scalars['String']>;
  stack?: Maybe<Scalars['String']>;
  detail?: Maybe<Scalars['String']>;
}

export interface MdPinnedVersion {
  __typename?: 'MdPinnedVersion';
  id: Scalars['String'];
  name: Scalars['String'];
  reference: Scalars['String'];
  version: Scalars['String'];
  gitMetadata?: Maybe<MdGitMetadata>;
  buildNumber?: Maybe<Scalars['String']>;
  pinnedAt?: Maybe<Scalars['InstantTime']>;
  pinnedBy?: Maybe<Scalars['String']>;
  comment?: Maybe<Scalars['String']>;
}

export interface MdPullRequest {
  __typename?: 'MdPullRequest';
  number?: Maybe<Scalars['String']>;
  link?: Maybe<Scalars['String']>;
}

export interface MdResource {
  __typename?: 'MdResource';
  id: Scalars['String'];
  kind: Scalars['String'];
  moniker?: Maybe<MdMoniker>;
  state?: Maybe<MdResourceActuationState>;
  artifact?: Maybe<MdArtifact>;
  displayName?: Maybe<Scalars['String']>;
  location?: Maybe<MdLocation>;
}

export interface MdResourceActuationState {
  __typename?: 'MdResourceActuationState';
  status: MdResourceActuationStatus;
  reason?: Maybe<Scalars['String']>;
  event?: Maybe<Scalars['String']>;
}

export type MdResourceActuationStatus = 'PROCESSING' | 'UP_TO_DATE' | 'ERROR' | 'WAITING' | 'NOT_MANAGED';

export interface MdUnpinArtifactVersionPayload {
  application: Scalars['String'];
  environment: Scalars['String'];
  reference: Scalars['String'];
}

export interface Mutation {
  __typename?: 'Mutation';
  updateConstraintStatus?: Maybe<Scalars['Boolean']>;
  toggleManagement?: Maybe<Scalars['Boolean']>;
  pinArtifactVersion?: Maybe<Scalars['Boolean']>;
  markArtifactVersionAsBad?: Maybe<Scalars['Boolean']>;
  unpinArtifactVersion?: Maybe<Scalars['Boolean']>;
  markArtifactVersionAsGood?: Maybe<Scalars['Boolean']>;
}

export interface MutationUpdateConstraintStatusArgs {
  payload: MdConstraintStatusPayload;
}

export interface MutationToggleManagementArgs {
  application: Scalars['String'];
  isPaused: Scalars['Boolean'];
}

export interface MutationPinArtifactVersionArgs {
  payload: MdArtifactVersionActionPayload;
}

export interface MutationMarkArtifactVersionAsBadArgs {
  payload: MdArtifactVersionActionPayload;
}

export interface MutationUnpinArtifactVersionArgs {
  payload: MdUnpinArtifactVersionPayload;
}

export interface MutationMarkArtifactVersionAsGoodArgs {
  payload: MdMarkArtifactVersionAsGoodPayload;
}

export interface Query {
  __typename?: 'Query';
  application?: Maybe<MdApplication>;
}

export interface QueryApplicationArgs {
  appName: Scalars['String'];
}

export type FetchApplicationQueryVariables = Exact<{
  appName: Scalars['String'];
  statuses?: Maybe<Array<MdArtifactStatusInEnvironment> | MdArtifactStatusInEnvironment>;
}>;

export type FetchApplicationQuery = { __typename?: 'Query' } & {
  application?: Maybe<
    { __typename?: 'MdApplication' } & Pick<MdApplication, 'id' | 'name' | 'account'> & {
        environments: Array<
          { __typename?: 'MdEnvironment' } & Pick<MdEnvironment, 'id' | 'name'> & {
              state: { __typename?: 'MdEnvironmentState' } & Pick<MdEnvironmentState, 'id'> & {
                  artifacts?: Maybe<
                    Array<
                      { __typename?: 'MdArtifact' } & Pick<
                        MdArtifact,
                        'id' | 'name' | 'environment' | 'type' | 'reference'
                      > & {
                          versions?: Maybe<
                            Array<
                              { __typename?: 'MdArtifactVersionInEnvironment' } & Pick<
                                MdArtifactVersionInEnvironment,
                                'id' | 'buildNumber' | 'version' | 'createdAt' | 'status' | 'deployedAt'
                              > & {
                                  gitMetadata?: Maybe<
                                    { __typename?: 'MdGitMetadata' } & Pick<
                                      MdGitMetadata,
                                      'commit' | 'author' | 'branch'
                                    > & {
                                        commitInfo?: Maybe<
                                          { __typename?: 'MdCommitInfo' } & Pick<
                                            MdCommitInfo,
                                            'sha' | 'link' | 'message'
                                          >
                                        >;
                                        pullRequest?: Maybe<
                                          { __typename?: 'MdPullRequest' } & Pick<MdPullRequest, 'number' | 'link'>
                                        >;
                                        comparisonLinks?: Maybe<
                                          { __typename?: 'MdComparisonLinks' } & Pick<
                                            MdComparisonLinks,
                                            'toPreviousVersion' | 'toCurrentVersion'
                                          >
                                        >;
                                      }
                                  >;
                                  lifecycleSteps?: Maybe<
                                    Array<
                                      { __typename?: 'MdLifecycleStep' } & Pick<
                                        MdLifecycleStep,
                                        'startedAt' | 'completedAt' | 'type' | 'status' | 'link'
                                      >
                                    >
                                  >;
                                  constraints?: Maybe<
                                    Array<
                                      { __typename?: 'MdConstraint' } & Pick<
                                        MdConstraint,
                                        'type' | 'status' | 'judgedBy' | 'judgedAt' | 'attributes'
                                      >
                                    >
                                  >;
                                  verifications?: Maybe<
                                    Array<
                                      { __typename?: 'MdAction' } & Pick<
                                        MdAction,
                                        'id' | 'type' | 'status' | 'startedAt' | 'completedAt' | 'link'
                                      >
                                    >
                                  >;
                                  postDeploy?: Maybe<
                                    Array<
                                      { __typename?: 'MdAction' } & Pick<
                                        MdAction,
                                        'id' | 'type' | 'status' | 'startedAt' | 'completedAt' | 'link'
                                      >
                                    >
                                  >;
                                }
                            >
                          >;
                          pinnedVersion?: Maybe<
                            { __typename?: 'MdPinnedVersion' } & Pick<
                              MdPinnedVersion,
                              'id' | 'version' | 'buildNumber' | 'pinnedAt' | 'pinnedBy' | 'comment'
                            > & {
                                gitMetadata?: Maybe<
                                  { __typename?: 'MdGitMetadata' } & {
                                    commitInfo?: Maybe<{ __typename?: 'MdCommitInfo' } & Pick<MdCommitInfo, 'message'>>;
                                  }
                                >;
                              }
                          >;
                        }
                    >
                  >;
                  resources?: Maybe<
                    Array<
                      { __typename?: 'MdResource' } & Pick<MdResource, 'id' | 'kind' | 'displayName'> & {
                          moniker?: Maybe<{ __typename?: 'MdMoniker' } & Pick<MdMoniker, 'app' | 'stack' | 'detail'>>;
                          location?: Maybe<{ __typename?: 'MdLocation' } & Pick<MdLocation, 'account' | 'regions'>>;
                        }
                    >
                  >;
                };
            }
        >;
      }
  >;
};

export type FetchVersionsHistoryQueryVariables = Exact<{
  appName: Scalars['String'];
  limit?: Maybe<Scalars['Int']>;
}>;

export type FetchVersionsHistoryQuery = { __typename?: 'Query' } & {
  application?: Maybe<
    { __typename?: 'MdApplication' } & Pick<MdApplication, 'id' | 'name' | 'account'> & {
        environments: Array<
          { __typename?: 'MdEnvironment' } & Pick<MdEnvironment, 'id' | 'name'> & {
              state: { __typename?: 'MdEnvironmentState' } & Pick<MdEnvironmentState, 'id'> & {
                  artifacts?: Maybe<
                    Array<
                      { __typename?: 'MdArtifact' } & Pick<
                        MdArtifact,
                        'id' | 'name' | 'environment' | 'type' | 'reference'
                      > & {
                          versions?: Maybe<
                            Array<
                              { __typename?: 'MdArtifactVersionInEnvironment' } & Pick<
                                MdArtifactVersionInEnvironment,
                                'id' | 'buildNumber' | 'version' | 'createdAt' | 'status'
                              > & {
                                  gitMetadata?: Maybe<
                                    { __typename?: 'MdGitMetadata' } & Pick<
                                      MdGitMetadata,
                                      'commit' | 'author' | 'branch'
                                    > & {
                                        commitInfo?: Maybe<
                                          { __typename?: 'MdCommitInfo' } & Pick<
                                            MdCommitInfo,
                                            'sha' | 'link' | 'message'
                                          >
                                        >;
                                        pullRequest?: Maybe<
                                          { __typename?: 'MdPullRequest' } & Pick<MdPullRequest, 'number' | 'link'>
                                        >;
                                      }
                                  >;
                                  lifecycleSteps?: Maybe<
                                    Array<{ __typename?: 'MdLifecycleStep' } & Pick<MdLifecycleStep, 'type' | 'status'>>
                                  >;
                                }
                            >
                          >;
                          pinnedVersion?: Maybe<
                            { __typename?: 'MdPinnedVersion' } & Pick<
                              MdPinnedVersion,
                              'id' | 'version' | 'buildNumber' | 'pinnedAt' | 'pinnedBy' | 'comment'
                            >
                          >;
                        }
                    >
                  >;
                };
            }
        >;
      }
  >;
};

export type FetchVersionQueryVariables = Exact<{
  appName: Scalars['String'];
  versions?: Maybe<Array<Scalars['String']> | Scalars['String']>;
}>;

export type FetchVersionQuery = { __typename?: 'Query' } & {
  application?: Maybe<
    { __typename?: 'MdApplication' } & Pick<MdApplication, 'id' | 'name' | 'account'> & {
        environments: Array<
          { __typename?: 'MdEnvironment' } & Pick<MdEnvironment, 'id' | 'name'> & {
              state: { __typename?: 'MdEnvironmentState' } & Pick<MdEnvironmentState, 'id'> & {
                  artifacts?: Maybe<
                    Array<
                      { __typename?: 'MdArtifact' } & Pick<
                        MdArtifact,
                        'id' | 'name' | 'environment' | 'type' | 'reference'
                      > & {
                          versions?: Maybe<
                            Array<
                              { __typename?: 'MdArtifactVersionInEnvironment' } & Pick<
                                MdArtifactVersionInEnvironment,
                                'id' | 'buildNumber' | 'version' | 'createdAt' | 'status' | 'deployedAt'
                              > & {
                                  gitMetadata?: Maybe<
                                    { __typename?: 'MdGitMetadata' } & Pick<
                                      MdGitMetadata,
                                      'commit' | 'author' | 'branch'
                                    > & {
                                        commitInfo?: Maybe<
                                          { __typename?: 'MdCommitInfo' } & Pick<
                                            MdCommitInfo,
                                            'sha' | 'link' | 'message'
                                          >
                                        >;
                                        pullRequest?: Maybe<
                                          { __typename?: 'MdPullRequest' } & Pick<MdPullRequest, 'number' | 'link'>
                                        >;
                                        comparisonLinks?: Maybe<
                                          { __typename?: 'MdComparisonLinks' } & Pick<
                                            MdComparisonLinks,
                                            'toPreviousVersion' | 'toCurrentVersion'
                                          >
                                        >;
                                      }
                                  >;
                                  lifecycleSteps?: Maybe<
                                    Array<
                                      { __typename?: 'MdLifecycleStep' } & Pick<
                                        MdLifecycleStep,
                                        'startedAt' | 'completedAt' | 'type' | 'status' | 'link'
                                      >
                                    >
                                  >;
                                  constraints?: Maybe<
                                    Array<
                                      { __typename?: 'MdConstraint' } & Pick<
                                        MdConstraint,
                                        'type' | 'status' | 'judgedBy' | 'judgedAt' | 'attributes'
                                      >
                                    >
                                  >;
                                  verifications?: Maybe<
                                    Array<
                                      { __typename?: 'MdAction' } & Pick<
                                        MdAction,
                                        'id' | 'type' | 'status' | 'startedAt' | 'completedAt' | 'link'
                                      >
                                    >
                                  >;
                                  postDeploy?: Maybe<
                                    Array<
                                      { __typename?: 'MdAction' } & Pick<
                                        MdAction,
                                        'id' | 'type' | 'status' | 'startedAt' | 'completedAt' | 'link'
                                      >
                                    >
                                  >;
                                }
                            >
                          >;
                        }
                    >
                  >;
                };
            }
        >;
      }
  >;
};

export type FetchResourceStatusQueryVariables = Exact<{
  appName: Scalars['String'];
}>;

export type FetchResourceStatusQuery = { __typename?: 'Query' } & {
  application?: Maybe<
    { __typename?: 'MdApplication' } & Pick<MdApplication, 'id' | 'name'> & {
        environments: Array<
          { __typename?: 'MdEnvironment' } & Pick<MdEnvironment, 'id' | 'name'> & {
              state: { __typename?: 'MdEnvironmentState' } & Pick<MdEnvironmentState, 'id'> & {
                  resources?: Maybe<
                    Array<
                      { __typename?: 'MdResource' } & Pick<MdResource, 'id' | 'kind'> & {
                          state?: Maybe<
                            { __typename?: 'MdResourceActuationState' } & Pick<
                              MdResourceActuationState,
                              'status' | 'reason' | 'event'
                            >
                          >;
                        }
                    >
                  >;
                };
            }
        >;
      }
  >;
};

export type FetchApplicationManagementStatusQueryVariables = Exact<{
  appName: Scalars['String'];
}>;

export type FetchApplicationManagementStatusQuery = { __typename?: 'Query' } & {
  application?: Maybe<{ __typename?: 'MdApplication' } & Pick<MdApplication, 'id' | 'name' | 'isPaused'>>;
};

export type UpdateConstraintMutationVariables = Exact<{
  payload: MdConstraintStatusPayload;
}>;

export type UpdateConstraintMutation = { __typename?: 'Mutation' } & Pick<Mutation, 'updateConstraintStatus'>;

export type ToggleManagementMutationVariables = Exact<{
  application: Scalars['String'];
  isPaused: Scalars['Boolean'];
}>;

export type ToggleManagementMutation = { __typename?: 'Mutation' } & Pick<Mutation, 'toggleManagement'>;

export const FetchApplicationDocument = gql`
  query fetchApplication($appName: String!, $statuses: [MdArtifactStatusInEnvironment!]) {
    application(appName: $appName) {
      id
      name
      account
      environments {
        id
        name
        state {
          id
          artifacts {
            id
            name
            environment
            type
            reference
            versions(statuses: $statuses) {
              id
              buildNumber
              version
              createdAt
              status
              gitMetadata {
                commit
                author
                branch
                commitInfo {
                  sha
                  link
                  message
                }
                pullRequest {
                  number
                  link
                }
                comparisonLinks {
                  toPreviousVersion
                  toCurrentVersion
                }
              }
              deployedAt
              lifecycleSteps {
                startedAt
                completedAt
                type
                status
                link
              }
              constraints {
                type
                status
                judgedBy
                judgedAt
                attributes
              }
              verifications {
                id
                type
                status
                startedAt
                completedAt
                link
              }
              postDeploy {
                id
                type
                status
                startedAt
                completedAt
                link
              }
            }
            pinnedVersion {
              id
              version
              buildNumber
              pinnedAt
              pinnedBy
              comment
              gitMetadata {
                commitInfo {
                  message
                }
              }
            }
          }
          resources {
            id
            kind
            displayName
            moniker {
              app
              stack
              detail
            }
            location {
              account
              regions
            }
          }
        }
      }
    }
  }
`;

/**
 * __useFetchApplicationQuery__
 *
 * To run a query within a React component, call `useFetchApplicationQuery` and pass it any options that fit your needs.
 * When your component renders, `useFetchApplicationQuery` returns an object from Apollo Client that contains loading, error, and data properties
 * you can use to render your UI.
 *
 * @param baseOptions options that will be passed into the query, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options;
 *
 * @example
 * const { data, loading, error } = useFetchApplicationQuery({
 *   variables: {
 *      appName: // value for 'appName'
 *      statuses: // value for 'statuses'
 *   },
 * });
 */
export function useFetchApplicationQuery(
  baseOptions: Apollo.QueryHookOptions<FetchApplicationQuery, FetchApplicationQueryVariables>,
) {
  const options = { ...defaultOptions, ...baseOptions };
  return Apollo.useQuery<FetchApplicationQuery, FetchApplicationQueryVariables>(FetchApplicationDocument, options);
}
export function useFetchApplicationLazyQuery(
  baseOptions?: Apollo.LazyQueryHookOptions<FetchApplicationQuery, FetchApplicationQueryVariables>,
) {
  const options = { ...defaultOptions, ...baseOptions };
  return Apollo.useLazyQuery<FetchApplicationQuery, FetchApplicationQueryVariables>(FetchApplicationDocument, options);
}
export type FetchApplicationQueryHookResult = ReturnType<typeof useFetchApplicationQuery>;
export type FetchApplicationLazyQueryHookResult = ReturnType<typeof useFetchApplicationLazyQuery>;
export type FetchApplicationQueryResult = Apollo.QueryResult<FetchApplicationQuery, FetchApplicationQueryVariables>;
export const FetchVersionsHistoryDocument = gql`
  query fetchVersionsHistory($appName: String!, $limit: Int) {
    application(appName: $appName) {
      id
      name
      account
      environments {
        id
        name
        state {
          id
          artifacts {
            id
            name
            environment
            type
            reference
            versions(limit: $limit) {
              id
              buildNumber
              version
              createdAt
              status
              gitMetadata {
                commit
                author
                branch
                commitInfo {
                  sha
                  link
                  message
                }
                pullRequest {
                  number
                  link
                }
              }
              lifecycleSteps {
                type
                status
              }
            }
            pinnedVersion {
              id
              version
              buildNumber
              pinnedAt
              pinnedBy
              comment
            }
          }
        }
      }
    }
  }
`;

/**
 * __useFetchVersionsHistoryQuery__
 *
 * To run a query within a React component, call `useFetchVersionsHistoryQuery` and pass it any options that fit your needs.
 * When your component renders, `useFetchVersionsHistoryQuery` returns an object from Apollo Client that contains loading, error, and data properties
 * you can use to render your UI.
 *
 * @param baseOptions options that will be passed into the query, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options;
 *
 * @example
 * const { data, loading, error } = useFetchVersionsHistoryQuery({
 *   variables: {
 *      appName: // value for 'appName'
 *      limit: // value for 'limit'
 *   },
 * });
 */
export function useFetchVersionsHistoryQuery(
  baseOptions: Apollo.QueryHookOptions<FetchVersionsHistoryQuery, FetchVersionsHistoryQueryVariables>,
) {
  const options = { ...defaultOptions, ...baseOptions };
  return Apollo.useQuery<FetchVersionsHistoryQuery, FetchVersionsHistoryQueryVariables>(
    FetchVersionsHistoryDocument,
    options,
  );
}
export function useFetchVersionsHistoryLazyQuery(
  baseOptions?: Apollo.LazyQueryHookOptions<FetchVersionsHistoryQuery, FetchVersionsHistoryQueryVariables>,
) {
  const options = { ...defaultOptions, ...baseOptions };
  return Apollo.useLazyQuery<FetchVersionsHistoryQuery, FetchVersionsHistoryQueryVariables>(
    FetchVersionsHistoryDocument,
    options,
  );
}
export type FetchVersionsHistoryQueryHookResult = ReturnType<typeof useFetchVersionsHistoryQuery>;
export type FetchVersionsHistoryLazyQueryHookResult = ReturnType<typeof useFetchVersionsHistoryLazyQuery>;
export type FetchVersionsHistoryQueryResult = Apollo.QueryResult<
  FetchVersionsHistoryQuery,
  FetchVersionsHistoryQueryVariables
>;
export const FetchVersionDocument = gql`
  query fetchVersion($appName: String!, $versions: [String!]) {
    application(appName: $appName) {
      id
      name
      account
      environments {
        id
        name
        state {
          id
          artifacts {
            id
            name
            environment
            type
            reference
            versions(versions: $versions) {
              id
              buildNumber
              version
              createdAt
              status
              gitMetadata {
                commit
                author
                branch
                commitInfo {
                  sha
                  link
                  message
                }
                pullRequest {
                  number
                  link
                }
                comparisonLinks {
                  toPreviousVersion
                  toCurrentVersion
                }
              }
              deployedAt
              lifecycleSteps {
                startedAt
                completedAt
                type
                status
                link
              }
              constraints {
                type
                status
                judgedBy
                judgedAt
                attributes
              }
              verifications {
                id
                type
                status
                startedAt
                completedAt
                link
              }
              postDeploy {
                id
                type
                status
                startedAt
                completedAt
                link
              }
            }
          }
        }
      }
    }
  }
`;

/**
 * __useFetchVersionQuery__
 *
 * To run a query within a React component, call `useFetchVersionQuery` and pass it any options that fit your needs.
 * When your component renders, `useFetchVersionQuery` returns an object from Apollo Client that contains loading, error, and data properties
 * you can use to render your UI.
 *
 * @param baseOptions options that will be passed into the query, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options;
 *
 * @example
 * const { data, loading, error } = useFetchVersionQuery({
 *   variables: {
 *      appName: // value for 'appName'
 *      versions: // value for 'versions'
 *   },
 * });
 */
export function useFetchVersionQuery(
  baseOptions: Apollo.QueryHookOptions<FetchVersionQuery, FetchVersionQueryVariables>,
) {
  const options = { ...defaultOptions, ...baseOptions };
  return Apollo.useQuery<FetchVersionQuery, FetchVersionQueryVariables>(FetchVersionDocument, options);
}
export function useFetchVersionLazyQuery(
  baseOptions?: Apollo.LazyQueryHookOptions<FetchVersionQuery, FetchVersionQueryVariables>,
) {
  const options = { ...defaultOptions, ...baseOptions };
  return Apollo.useLazyQuery<FetchVersionQuery, FetchVersionQueryVariables>(FetchVersionDocument, options);
}
export type FetchVersionQueryHookResult = ReturnType<typeof useFetchVersionQuery>;
export type FetchVersionLazyQueryHookResult = ReturnType<typeof useFetchVersionLazyQuery>;
export type FetchVersionQueryResult = Apollo.QueryResult<FetchVersionQuery, FetchVersionQueryVariables>;
export const FetchResourceStatusDocument = gql`
  query fetchResourceStatus($appName: String!) {
    application(appName: $appName) {
      id
      name
      environments {
        id
        name
        state {
          id
          resources {
            id
            kind
            state {
              status
              reason
              event
            }
          }
        }
      }
    }
  }
`;

/**
 * __useFetchResourceStatusQuery__
 *
 * To run a query within a React component, call `useFetchResourceStatusQuery` and pass it any options that fit your needs.
 * When your component renders, `useFetchResourceStatusQuery` returns an object from Apollo Client that contains loading, error, and data properties
 * you can use to render your UI.
 *
 * @param baseOptions options that will be passed into the query, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options;
 *
 * @example
 * const { data, loading, error } = useFetchResourceStatusQuery({
 *   variables: {
 *      appName: // value for 'appName'
 *   },
 * });
 */
export function useFetchResourceStatusQuery(
  baseOptions: Apollo.QueryHookOptions<FetchResourceStatusQuery, FetchResourceStatusQueryVariables>,
) {
  const options = { ...defaultOptions, ...baseOptions };
  return Apollo.useQuery<FetchResourceStatusQuery, FetchResourceStatusQueryVariables>(
    FetchResourceStatusDocument,
    options,
  );
}
export function useFetchResourceStatusLazyQuery(
  baseOptions?: Apollo.LazyQueryHookOptions<FetchResourceStatusQuery, FetchResourceStatusQueryVariables>,
) {
  const options = { ...defaultOptions, ...baseOptions };
  return Apollo.useLazyQuery<FetchResourceStatusQuery, FetchResourceStatusQueryVariables>(
    FetchResourceStatusDocument,
    options,
  );
}
export type FetchResourceStatusQueryHookResult = ReturnType<typeof useFetchResourceStatusQuery>;
export type FetchResourceStatusLazyQueryHookResult = ReturnType<typeof useFetchResourceStatusLazyQuery>;
export type FetchResourceStatusQueryResult = Apollo.QueryResult<
  FetchResourceStatusQuery,
  FetchResourceStatusQueryVariables
>;
export const FetchApplicationManagementStatusDocument = gql`
  query fetchApplicationManagementStatus($appName: String!) {
    application(appName: $appName) {
      id
      name
      isPaused
    }
  }
`;

/**
 * __useFetchApplicationManagementStatusQuery__
 *
 * To run a query within a React component, call `useFetchApplicationManagementStatusQuery` and pass it any options that fit your needs.
 * When your component renders, `useFetchApplicationManagementStatusQuery` returns an object from Apollo Client that contains loading, error, and data properties
 * you can use to render your UI.
 *
 * @param baseOptions options that will be passed into the query, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options;
 *
 * @example
 * const { data, loading, error } = useFetchApplicationManagementStatusQuery({
 *   variables: {
 *      appName: // value for 'appName'
 *   },
 * });
 */
export function useFetchApplicationManagementStatusQuery(
  baseOptions: Apollo.QueryHookOptions<
    FetchApplicationManagementStatusQuery,
    FetchApplicationManagementStatusQueryVariables
  >,
) {
  const options = { ...defaultOptions, ...baseOptions };
  return Apollo.useQuery<FetchApplicationManagementStatusQuery, FetchApplicationManagementStatusQueryVariables>(
    FetchApplicationManagementStatusDocument,
    options,
  );
}
export function useFetchApplicationManagementStatusLazyQuery(
  baseOptions?: Apollo.LazyQueryHookOptions<
    FetchApplicationManagementStatusQuery,
    FetchApplicationManagementStatusQueryVariables
  >,
) {
  const options = { ...defaultOptions, ...baseOptions };
  return Apollo.useLazyQuery<FetchApplicationManagementStatusQuery, FetchApplicationManagementStatusQueryVariables>(
    FetchApplicationManagementStatusDocument,
    options,
  );
}
export type FetchApplicationManagementStatusQueryHookResult = ReturnType<
  typeof useFetchApplicationManagementStatusQuery
>;
export type FetchApplicationManagementStatusLazyQueryHookResult = ReturnType<
  typeof useFetchApplicationManagementStatusLazyQuery
>;
export type FetchApplicationManagementStatusQueryResult = Apollo.QueryResult<
  FetchApplicationManagementStatusQuery,
  FetchApplicationManagementStatusQueryVariables
>;
export const UpdateConstraintDocument = gql`
  mutation UpdateConstraint($payload: MdConstraintStatusPayload!) {
    updateConstraintStatus(payload: $payload)
  }
`;
export type UpdateConstraintMutationFn = Apollo.MutationFunction<
  UpdateConstraintMutation,
  UpdateConstraintMutationVariables
>;

/**
 * __useUpdateConstraintMutation__
 *
 * To run a mutation, you first call `useUpdateConstraintMutation` within a React component and pass it any options that fit your needs.
 * When your component renders, `useUpdateConstraintMutation` returns a tuple that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - An object with fields that represent the current status of the mutation's execution
 *
 * @param baseOptions options that will be passed into the mutation, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options-2;
 *
 * @example
 * const [updateConstraintMutation, { data, loading, error }] = useUpdateConstraintMutation({
 *   variables: {
 *      payload: // value for 'payload'
 *   },
 * });
 */
export function useUpdateConstraintMutation(
  baseOptions?: Apollo.MutationHookOptions<UpdateConstraintMutation, UpdateConstraintMutationVariables>,
) {
  const options = { ...defaultOptions, ...baseOptions };
  return Apollo.useMutation<UpdateConstraintMutation, UpdateConstraintMutationVariables>(
    UpdateConstraintDocument,
    options,
  );
}
export type UpdateConstraintMutationHookResult = ReturnType<typeof useUpdateConstraintMutation>;
export type UpdateConstraintMutationResult = Apollo.MutationResult<UpdateConstraintMutation>;
export type UpdateConstraintMutationOptions = Apollo.BaseMutationOptions<
  UpdateConstraintMutation,
  UpdateConstraintMutationVariables
>;
export const ToggleManagementDocument = gql`
  mutation ToggleManagement($application: String!, $isPaused: Boolean!) {
    toggleManagement(application: $application, isPaused: $isPaused)
  }
`;
export type ToggleManagementMutationFn = Apollo.MutationFunction<
  ToggleManagementMutation,
  ToggleManagementMutationVariables
>;

/**
 * __useToggleManagementMutation__
 *
 * To run a mutation, you first call `useToggleManagementMutation` within a React component and pass it any options that fit your needs.
 * When your component renders, `useToggleManagementMutation` returns a tuple that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - An object with fields that represent the current status of the mutation's execution
 *
 * @param baseOptions options that will be passed into the mutation, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options-2;
 *
 * @example
 * const [toggleManagementMutation, { data, loading, error }] = useToggleManagementMutation({
 *   variables: {
 *      application: // value for 'application'
 *      isPaused: // value for 'isPaused'
 *   },
 * });
 */
export function useToggleManagementMutation(
  baseOptions?: Apollo.MutationHookOptions<ToggleManagementMutation, ToggleManagementMutationVariables>,
) {
  const options = { ...defaultOptions, ...baseOptions };
  return Apollo.useMutation<ToggleManagementMutation, ToggleManagementMutationVariables>(
    ToggleManagementDocument,
    options,
  );
}
export type ToggleManagementMutationHookResult = ReturnType<typeof useToggleManagementMutation>;
export type ToggleManagementMutationResult = Apollo.MutationResult<ToggleManagementMutation>;
export type ToggleManagementMutationOptions = Apollo.BaseMutationOptions<
  ToggleManagementMutation,
  ToggleManagementMutationVariables
>;
