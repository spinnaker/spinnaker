/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.igor.gitlabci.client

class GitlabApiCannedResponses {
    static final String PROJECT_LIST = """[  
               {  
                  "id":4631786,
                  "description":"Project 1",
                  "default_branch":"master",
                  "tag_list":[  
            
                  ],
                  "ssh_url_to_repo":"git@gitlab.com:user1/project1.git",
                  "http_url_to_repo":"https://gitlab.com/user1/project1.git",
                  "web_url":"https://gitlab.com/user1/project1",
                  "name":"project1",
                  "name_with_namespace":"User 1 / project1",
                  "path":"project1",
                  "path_with_namespace":"user1/project1",
                  "avatar_url":"https://gitlab.com/uploads/-/system/project/avatar/4631786/ic_launcher.png",
                  "star_count":0,
                  "forks_count":0,
                  "created_at":"2017-11-12T16:58:02.097Z",
                  "last_activity_at":"2017-12-02T11:31:39.316Z",
                  "_links":{  
                     "self":"http://gitlab.com/api/v4/projects/4631786",
                     "issues":"http://gitlab.com/api/v4/projects/4631786/issues",
                     "merge_requests":"http://gitlab.com/api/v4/projects/4631786/merge_requests",
                     "repo_branches":"http://gitlab.com/api/v4/projects/4631786/repository/branches",
                     "labels":"http://gitlab.com/api/v4/projects/4631786/labels",
                     "events":"http://gitlab.com/api/v4/projects/4631786/events",
                     "members":"http://gitlab.com/api/v4/projects/4631786/members"
                  },
                  "archived":false,
                  "visibility":"private",
                  "owner":{  
                     "id":885762,
                     "name":"User 1",
                     "username":"user1",
                     "state":"active",
                     "avatar_url":"https://secure.gravatar.com/avatar/c21ab8c2c16c9e9b79d88f632df0c069?s=80\\u0026d=identicon",
                     "web_url":"https://gitlab.com/user1"
                  },
                  "resolve_outdated_diff_discussions":false,
                  "container_registry_enabled":true,
                  "issues_enabled":true,
                  "merge_requests_enabled":true,
                  "wiki_enabled":true,
                  "jobs_enabled":true,
                  "snippets_enabled":true,
                  "shared_runners_enabled":true,
                  "lfs_enabled":true,
                  "creator_id":885762,
                  "namespace":{  
                     "id":1061604,
                     "name":"user1",
                     "path":"user1",
                     "kind":"user",
                     "full_path":"user1",
                     "parent_id":null,
                     "plan":"early_adopter"
                  },
                  "import_status":"none",
                  "open_issues_count":0,
                  "public_jobs":true,
                  "ci_config_path":null,
                  "shared_with_groups":[  
            
                  ],
                  "only_allow_merge_if_pipeline_succeeds":false,
                  "request_access_enabled":false,
                  "only_allow_merge_if_all_discussions_are_resolved":false,
                  "printing_merge_request_link_enabled":true,
                  "approvals_before_merge":0,
                  "permissions":{  
                     "project_access":{  
                        "access_level":40,
                        "notification_level":3
                     },
                     "group_access":null
                  }
               },
               {  
                  "id":3057147,
                  "description":"",
                  "default_branch":"master",
                  "tag_list":[  
            
                  ],
                  "ssh_url_to_repo":"git@gitlab.com:user1/project2.git",
                  "http_url_to_repo":"https://gitlab.com/user1/project2.git",
                  "web_url":"https://gitlab.com/user1/project2",
                  "name":"project2",
                  "name_with_namespace":"User 1 / project2",
                  "path":"project2",
                  "path_with_namespace":"user1/project2",
                  "avatar_url":"https://assets.gitlab-static.net/uploads/-/system/project/avatar/3057147/Viktor_Dragunskij__The_adventures_of_Dennis.jpeg",
                  "star_count":1,
                  "forks_count":2,
                  "created_at":"2017-04-05T20:39:10.797Z",
                  "last_activity_at":"2017-11-23T12:56:09.588Z",
                  "_links":{  
                     "self":"http://gitlab.com/api/v4/projects/3057147",
                     "issues":"http://gitlab.com/api/v4/projects/3057147/issues",
                     "merge_requests":"http://gitlab.com/api/v4/projects/3057147/merge_requests",
                     "repo_branches":"http://gitlab.com/api/v4/projects/3057147/repository/branches",
                     "labels":"http://gitlab.com/api/v4/projects/3057147/labels",
                     "events":"http://gitlab.com/api/v4/projects/3057147/events",
                     "members":"http://gitlab.com/api/v4/projects/3057147/members"
                  },
                  "archived":false,
                  "visibility":"public",
                  "owner":{  
                     "id":885762,
                     "name":"User 1",
                     "username":"user1",
                     "state":"active",
                     "avatar_url":"https://secure.gravatar.com/avatar/c21ab8c2c16c9e9b79d88f632df0c069?s=80\\u0026d=identicon",
                     "web_url":"https://gitlab.com/user1"
                  },
                  "resolve_outdated_diff_discussions":null,
                  "container_registry_enabled":true,
                  "issues_enabled":true,
                  "merge_requests_enabled":true,
                  "wiki_enabled":true,
                  "jobs_enabled":true,
                  "snippets_enabled":false,
                  "shared_runners_enabled":true,
                  "lfs_enabled":true,
                  "creator_id":885762,
                  "namespace":{  
                     "id":1061604,
                     "name":"user1",
                     "path":"user1",
                     "kind":"user",
                     "full_path":"user1",
                     "parent_id":null,
                     "plan":"early_adopter"
                  },
                  "import_status":"none",
                  "open_issues_count":0,
                  "public_jobs":true,
                  "ci_config_path":null,
                  "shared_with_groups":[  
            
                  ],
                  "only_allow_merge_if_pipeline_succeeds":false,
                  "request_access_enabled":true,
                  "only_allow_merge_if_all_discussions_are_resolved":false,
                  "printing_merge_request_link_enabled":true,
                  "approvals_before_merge":0,
                  "permissions":{  
                     "project_access":{  
                        "access_level":40,
                        "notification_level":3
                     },
                     "group_access":null
                  }
               }]"""

    static final String PIPELINE_SUMMARIES = """
            [  
               {  
                  "id":14843843,
                  "sha":"ab0e9eb3a105082a97d5774cceb8c1b6c4d46136",
                  "ref":"master",
                  "status":"success"
               },
               {  
                  "id":14843833,
                  "sha":"ab0e9eb3a105082a97d5774cceb8c1b6c4d46136",
                  "ref":"master",
                  "status":"success"
               },
               {  
                  "id":14081120,
                  "sha":"ab0e9eb3a105082a97d5774cceb8c1b6c4d46136",
                  "ref":"master",
                  "status":"success"
               }
            ]
            """

    static final String PIPELINE = """{  
               "id":14081120,
               "sha":"ab0e9eb3a105082a97d5774cceb8c1b6c4d46136",
               "ref":"master",
               "status":"success",
               "before_sha":"ab0e9eb3a105082a97d5774cceb8c1b6c4d46136",
               "tag":false,
               "yaml_errors":null,
               "user":{  
                  "id":885762,
                  "name":"User 1",
                  "username":"user1",
                  "state":"active",
                  "avatar_url":"https://secure.gravatar.com/avatar/c21ab8c2c16c9e9b79d88f632df0c069?s=80&d=identicon",
                  "web_url":"https://gitlab.com/user1"
               },
               "created_at":"2017-11-17T21:24:14.264Z",
               "updated_at":"2017-11-17T21:24:54.886Z",
               "started_at":"2017-11-17T21:24:21.507Z",
               "finished_at":"2017-11-17T21:24:54.880Z",
               "committed_at":null,
               "duration":18,
               "coverage":null
            }
            """
}
