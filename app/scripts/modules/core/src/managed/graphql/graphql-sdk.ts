/* eslint-disable @typescript-eslint/array-type */
import { gql } from '@apollo/client';
import * as Apollo from '@apollo/client';
export type Maybe<T> = T | null;
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
  InstantTime: Date;
}

export interface DgsApplication {
  __typename?: 'DgsApplication';
  name: Scalars['String'];
  account: Scalars['String'];
  environments: Array<DgsEnvironment>;
}

export interface DgsArtifact {
  __typename?: 'DgsArtifact';
  environment: Scalars['String'];
  name: Scalars['String'];
  type: Scalars['String'];
  reference: Scalars['String'];
  versions?: Maybe<Array<DgsArtifactVersionInEnvironment>>;
  pinnedVersion?: Maybe<DgsPinnedVersion>;
}

export interface DgsArtifactVersionsArgs {
  statuses?: Maybe<Array<DgsArtifactStatusInEnvironment>>;
}

export type DgsArtifactStatusInEnvironment =
  | 'PENDING'
  | 'APPROVED'
  | 'DEPLOYING'
  | 'CURRENT'
  | 'PREVIOUS'
  | 'VETOED'
  | 'SKIPPED';

export interface DgsArtifactVersionInEnvironment {
  __typename?: 'DgsArtifactVersionInEnvironment';
  version: Scalars['String'];
  createdAt?: Maybe<Scalars['String']>;
  resources?: Maybe<Array<DgsResource>>;
  gitMetadata?: Maybe<DgsGitMetadata>;
  environment?: Maybe<Scalars['String']>;
  reference: Scalars['String'];
  status?: Maybe<DgsArtifactStatusInEnvironment>;
  lifecycleSteps?: Maybe<Array<DgsLifecycleStep>>;
  constraints?: Maybe<Array<DgsConstraint>>;
}

export interface DgsCommitInfo {
  __typename?: 'DgsCommitInfo';
  sha?: Maybe<Scalars['String']>;
  link?: Maybe<Scalars['String']>;
  message?: Maybe<Scalars['String']>;
}

export interface DgsConstraint {
  __typename?: 'DgsConstraint';
  type: Scalars['String'];
  status: DgsConstraintStatus;
  startedAt?: Maybe<Scalars['InstantTime']>;
  judgedAt?: Maybe<Scalars['InstantTime']>;
  judgedBy?: Maybe<Scalars['String']>;
  comment?: Maybe<Scalars['String']>;
  attributes?: Maybe<Scalars['String']>;
}

export type DgsConstraintStatus = 'NOT_EVALUATED' | 'PENDING' | 'PASS' | 'FAIL' | 'OVERRIDE_PASS' | 'OVERRIDE_FAIL';

export interface DgsEnvironment {
  __typename?: 'DgsEnvironment';
  name: Scalars['String'];
  state: DgsEnvironmentState;
}

export interface DgsEnvironmentState {
  __typename?: 'DgsEnvironmentState';
  version: Scalars['String'];
  resources?: Maybe<Array<DgsResource>>;
  artifacts?: Maybe<Array<DgsArtifact>>;
}

export interface DgsGitMetadata {
  __typename?: 'DgsGitMetadata';
  commit?: Maybe<Scalars['String']>;
  author?: Maybe<Scalars['String']>;
  project?: Maybe<Scalars['String']>;
  branch?: Maybe<Scalars['String']>;
  repoName?: Maybe<Scalars['String']>;
  pullRequest?: Maybe<DgsPullRequest>;
  commitInfo?: Maybe<DgsCommitInfo>;
}

export interface DgsLifecycleStep {
  __typename?: 'DgsLifecycleStep';
  scope: Scalars['String'];
  type: Scalars['String'];
  id?: Maybe<Scalars['String']>;
  status: Scalars['String'];
  text?: Maybe<Scalars['String']>;
  link?: Maybe<Scalars['String']>;
  startedAt?: Maybe<Scalars['String']>;
  completedAt?: Maybe<Scalars['String']>;
  artifactVersion?: Maybe<Scalars['String']>;
}

export interface DgsLocation {
  __typename?: 'DgsLocation';
  regions?: Maybe<Array<Scalars['String']>>;
}

export interface DgsPinnedVersion {
  __typename?: 'DgsPinnedVersion';
  name: Scalars['String'];
  reference: Scalars['String'];
  version: Scalars['String'];
  pinnedAt?: Maybe<Scalars['String']>;
  pinnedBy?: Maybe<Scalars['String']>;
  comment?: Maybe<Scalars['String']>;
}

export interface DgsPullRequest {
  __typename?: 'DgsPullRequest';
  number?: Maybe<Scalars['String']>;
  link?: Maybe<Scalars['String']>;
}

export interface DgsResource {
  __typename?: 'DgsResource';
  id: Scalars['String'];
  kind: Scalars['String'];
  status?: Maybe<Scalars['String']>;
  artifact?: Maybe<DgsArtifact>;
  displayName?: Maybe<Scalars['String']>;
  location?: Maybe<DgsLocation>;
}

export interface DgsVerification {
  __typename?: 'DgsVerification';
  id: Scalars['String'];
  type: Scalars['String'];
  status?: Maybe<DgsConstraintStatus>;
  startedAt?: Maybe<Scalars['String']>;
  completedAt?: Maybe<Scalars['String']>;
  link?: Maybe<Scalars['String']>;
}

export interface Query {
  __typename?: 'Query';
  application?: Maybe<DgsApplication>;
}

export interface QueryApplicationArgs {
  appName: Scalars['String'];
}

export type FetchApplicationQueryVariables = Exact<{
  appName: Scalars['String'];
}>;

export type FetchApplicationQuery = { __typename?: 'Query' } & {
  application?: Maybe<
    { __typename?: 'DgsApplication' } & Pick<DgsApplication, 'name' | 'account'> & {
        environments: Array<
          { __typename?: 'DgsEnvironment' } & Pick<DgsEnvironment, 'name'> & {
              state: { __typename?: 'DgsEnvironmentState' } & {
                artifacts?: Maybe<
                  Array<
                    { __typename?: 'DgsArtifact' } & Pick<DgsArtifact, 'name' | 'type' | 'reference'> & {
                        versions?: Maybe<
                          Array<
                            { __typename?: 'DgsArtifactVersionInEnvironment' } & Pick<
                              DgsArtifactVersionInEnvironment,
                              'version' | 'createdAt' | 'status'
                            > & {
                                gitMetadata?: Maybe<
                                  { __typename?: 'DgsGitMetadata' } & Pick<
                                    DgsGitMetadata,
                                    'commit' | 'author' | 'branch'
                                  > & {
                                      commitInfo?: Maybe<
                                        { __typename?: 'DgsCommitInfo' } & Pick<
                                          DgsCommitInfo,
                                          'sha' | 'link' | 'message'
                                        >
                                      >;
                                      pullRequest?: Maybe<
                                        { __typename?: 'DgsPullRequest' } & Pick<DgsPullRequest, 'number' | 'link'>
                                      >;
                                    }
                                >;
                              }
                          >
                        >;
                        pinnedVersion?: Maybe<
                          { __typename?: 'DgsPinnedVersion' } & Pick<DgsPinnedVersion, 'name' | 'reference'>
                        >;
                      }
                  >
                >;
                resources?: Maybe<
                  Array<
                    { __typename?: 'DgsResource' } & Pick<DgsResource, 'id' | 'kind' | 'status' | 'displayName'> & {
                        artifact?: Maybe<
                          { __typename?: 'DgsArtifact' } & Pick<DgsArtifact, 'name' | 'type' | 'reference'>
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

export const FetchApplicationDocument = gql`
  query fetchApplication($appName: String!) {
    application(appName: $appName) {
      name
      account
      environments {
        name
        state {
          artifacts {
            name
            type
            reference
            versions(statuses: [PENDING, APPROVED, DEPLOYING, CURRENT]) {
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
            }
            pinnedVersion {
              name
              reference
            }
          }
          resources {
            id
            kind
            status
            displayName
            artifact {
              name
              type
              reference
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
