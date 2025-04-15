/*
 * Copyright 2016 Schibsted ASA.
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

package com.netflix.spinnaker.igor.travis.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.igor.travis.client.model.AccessToken
import com.netflix.spinnaker.igor.travis.client.model.Build
import com.netflix.spinnaker.igor.travis.client.model.Builds
import com.netflix.spinnaker.igor.travis.client.model.GithubAuth
import com.netflix.spinnaker.igor.travis.client.model.v3.RepoRequest
import com.netflix.spinnaker.igor.travis.client.model.v3.TriggerResponse
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Build
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Builds
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Log
import com.netflix.spinnaker.igor.travis.config.TravisConfig
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneOffset

import static com.netflix.spinnaker.igor.travis.helpers.TestUtils.createObjectMapper

class TravisClientSpec extends Specification {

    @Shared
    TravisClient client

    @Shared
    MockWebServer server

    @Shared
    ObjectMapper mapper

    void setup() {
        server = new MockWebServer()
        mapper = createObjectMapper()
    }

    void cleanup() {
        server.shutdown()
    }

    def "AccessToken"() {
        given:
        setResponse '''{"access_token":"aCCeSSToKeN"}'''

        when:
        AccessToken accessToken = Retrofit2SyncCall.execute(client.accessToken(new GithubAuth("foo")))

        then:
        accessToken.accessToken == "aCCeSSToKeN"
    }

    def "triggerBuild()" () {
        given:
        setResponse '''{
            "@type": "pending",
            "remaining_requests": 10,
            "repository": {
                "@type": "repository",
                "@href": "/repo/8059977",
                "@representation": "minimal",
                "id": 8059977,
                "name": "travistest",
                "slug": "gardalize/travistest"
            },
            "request": {
                "repository": {
                    "id": 54667513,
                    "owner_name": "gardalize",
                    "name": "travistest"
                },
                "user": {
                    "id": 337980
                },
                "message": "Triggered from spinnaker",
                "branch": "master",
                "config": {
                }
            },
            "resource_type": "request"
        }'''

        when:
        TriggerResponse triggerResponse = Retrofit2SyncCall.execute(client.triggerBuild("someToken", "some/build", new RepoRequest("master")))

        then:
        triggerResponse.remainingRequests == 10
    }

    def "Log"() {
        given:
        setResponse '''
        {
            "@type": "log",
            "@href": "/job/123/log",
            "@representation": "standard",
            "@permissions": {
                "read": true,
                "debug": false,
                "cancel": false,
                "restart": false,
                "delete_log": false
            },
            "id": 1337,
            "content": "ERROR: An error occured while trying to parse your .travis.yml file.\\n\\nPlease make sure that the file is valid YAML.\\n\\nhttp://lint.travis-ci.org can check your .travis.yml.\\n\\nThe error was \\"undefined method `merge' for false:FalseClass\\".\\n\\nDone. Your build exited with 1.",
            "log_parts": [
                {
                    "content": "ERROR: An error occured while trying to parse your .travis.yml file.\\n\\nPlease make sure that the file is valid YAML.\\n\\n",
                    "final": false,
                    "number": 0
                },
                {
                    "content": "http://lint.travis-ci.org can check your .travis.yml.\\n\\nThe error was \\"undefined method `merge' for false:FalseClass\\".\\n\\nDone. Your build exited with 1.",
                    "final": true,
                    "number": 1
                }
            ],
            "@raw_log_href": "/v3/job/123/log.txt?log.token=hfeus7seyfhfe8"
        }'''.stripIndent()

        when:
        V3Log v3Log = Retrofit2SyncCall.execute(client.jobLog("someToken", 123))

        then:
        v3Log.content.contains "false:FalseClass"
        v3Log.ready
    }

    def "detect incomplete log"() {
        given:
        setResponse '''
        {
            "@type": "log",
            "@href": "/job/123/log",
            "@representation": "standard",
            "@permissions": {
                "read": true,
                "debug": false,
                "cancel": false,
                "restart": false,
                "delete_log": false
            },
            "id": 1337,
            "content": "ERROR: An error occured while trying to parse your .travis.yml file.\\n\\nPlease make sure that the file is valid YAML.\\n\\nhttp://lint.travis-ci.org can check your .travis.yml.\\n\\nThe error was \\"undefined method `merge' for false:FalseClass\\".",
            "log_parts": [
                {
                    "content": "ERROR: An error occured while trying to parse your .travis.yml file.\\n\\nPlease make sure that the file is valid YAML.\\n\\n",
                    "final": false,
                    "number": 0
                },
                {
                    "content": "http://lint.travis-ci.org can check your .travis.yml.\\n\\nThe error was \\"undefined method `merge' for false:FalseClass\\".",
                    "final": false,
                    "number": 1
                }
            ],
            "@raw_log_href": "/v3/job/123/log.txt?log.token=hfeus7seyfhfe8"
        }'''.stripIndent()

        when:
        V3Log v3Log = Retrofit2SyncCall.execute(client.jobLog("someToken", 123))

        then:
        v3Log.content.contains "false:FalseClass"
        !v3Log.ready
    }

  def "detect incomplete log due to missing part"() {
    given:
    setResponse '''
        {
            "@type": "log",
            "@href": "/job/123/log",
            "@representation": "standard",
            "@permissions": {
                "read": true,
                "debug": false,
                "cancel": false,
                "restart": false,
                "delete_log": false
            },
            "id": 1337,
            "content": "ERROR: An error occured while trying to parse your .travis.yml file.\\n\\nPlease make sure that the file is valid YAML.\\n\\nhttp://lint.travis-ci.org can check your .travis.yml.\\n\\nThe error was \\"undefined method `merge' for false:FalseClass\\".",
            "log_parts": [
                {
                    "content": "http://lint.travis-ci.org can check your .travis.yml.\\n\\nThe error was \\"undefined method `merge' for false:FalseClass\\".",
                    "final": true,
                    "number": 1
                }
            ],
            "@raw_log_href": "/v3/job/123/log.txt?log.token=hfeus7seyfhfe8"
        }'''.stripIndent()

    when:
    V3Log v3Log = Retrofit2SyncCall.execute(client.jobLog("someToken", 123))

    then:
    v3Log.content.contains "false:FalseClass"
    !v3Log.ready
  }

    def "getBuilds(accessToken, repoSlug, buildNumber)"() {
        given:
        setResponse '''
            {
            "builds":[
                {
                "id":241991,
                "repository_id":2838,
                "commit_id":133851,
                "number":"31",
                "pull_request":false,
                "pull_request_title":null,
                "pull_request_number":null,
                "config":{
                    "install":"script/bootstrap",
                    "script":"script/build",
                    "deploy":{
                        "skip_cleanup":true,
                        "provider":"script",
                        "script":"script/deploy",
                        "true":{"tags":true}
                    },
                    ".result":"configured",
                    "global_env":[
                        "SOME_PARAM=some_value",
                        {"secure":"xyz"}
                    ],
                    "language":"ruby",
                    "os":"linux"
                },
                "state":"passed",
                "started_at":"2016-03-15T14:11:16Z",
                "finished_at":"2016-03-15T14:11:24Z",
                "duration":8,"job_ids":[241992]}],
                "commits":[
                    {
                        "id":1337,
                        "sha":"def",
                        "branch":"master","message":"Merge pull request #4 from my pull-request",
                        "committed_at":"2016-03-15T13:46:11Z",
                        "author_name":"Some User",
                        "author_email":"some.user@some.domain",
                        "committer_name":"Some User",
                        "committer_email":"some.user@some.domain",
                        "compare_url":"https://github.some.domain/some-org/some-repo/compare/abc...def",
                        "pull_request_number":null
                    }
                ]
            }'''

        when:
        Builds builds = Retrofit2SyncCall.execute(client.builds("someToken", "some/build", 31))

        then:
        Build build = builds.builds.first()
        build.number == 31
        build.duration == 8
        build.finishedAt.toEpochMilli() == 1458051084000
    }

    def "extract config from getBuild(accessToken, repoSlug)"() {
        given:
        setResponse '''{
            "builds": [{
                "id": 134545105,
                "repository_id": 8906939,
                "commit_id": 38108155,
                "number": "3",
                "event_type": "push",
                "pull_request": false,
                "pull_request_title": null,
                "pull_request_number": null,
                "config": {
                    "language": "python",
                    "global_env": ["TARGET_ENV=test.environment"],
                    "script": "./travis.sh",
                    ".result": "configured",
                    "group": "stable",
                    "dist": "precise"
                },
                "state": "passed",
                "started_at": "2016-06-01T18:58:08Z",
                "finished_at": "2016-06-01T18:58:29Z",
                "duration": 21,
                "job_ids": [134545109]
            }],
            "commits": [{
                "id": 38108155,
                "sha": "f290f2af03826999c6004404378a5bc750e834b0",
                "branch": "master",
                "message": "Update README.md",
                "committed_at": "2016-06-01T18:57:48Z",
                "author_name": "Gard Rimestad",
                "author_email": "gardalize@gurters.com",
                "committer_name": "Gard Rimestad",
                "committer_email": "gardalize@gurters.com",
                "compare_url": "https://github.com/gardalize/travis-trigger-test/compare/bd005f51cb1e...f290f2af0382",
                "pull_request_number": null
            }]
            }'''

        when:
        Builds builds = Retrofit2SyncCall.execute(client.builds("someToken", "some/build", 31))

        then:
        Build build = builds.builds.first()
        build.number == 3
        build.config.globalEnv.size() == 1
        build.config.globalEnv.first() == "TARGET_ENV=test.environment"
    }

    def "getBuilds(accessToken, repoSlug, buildNumber) with no build found"() {
        given:
        setResponse '''{"builds":[],"commits":[]}'''

        when:
        Builds builds = Retrofit2SyncCall.execute(client.builds("someToken", "some/build", 31))

        then:
        builds.builds.size() == 0
    }

    def "commits, identify tag"() {
        given:
        setResponse '''{
            "builds": [{
                           "id": 281721,
                           "repository_id": 1993,
                           "commit_id": 156529,
                           "number": "39",
                           "pull_request": false,
                           "pull_request_title": null,
                           "pull_request_number": null,
                           "state": "passed",
                           "started_at": "2016-04-19T09:19:25Z",
                           "finished_at": "2016-04-19T09:23:08Z",
                           "duration": 223,
                           "job_ids": [281722]
                       }],
            "commits": [{
                            "id": 156529,
                            "sha": "1537853a99a3b90abe53d74ff7d908eec7641138",
                            "branch": "v1.17.4",
                            "message": "Some message",
                            "committed_at": "2016-04-12T15:46:54Z",
                            "author_name": "Some user",
                            "author_email": "some.user@domain.com",
                            "committer_name": "Some user",
                            "committer_email": "some.user@domain.com",
                            "compare_url": "https://github.domain/org/repo/compare/v1.17.4",
                            "pull_request_number": null
                        }]
        }'''

        when:
        Builds builds = Retrofit2SyncCall.execute(client.builds("someToken", "org/repo", 39))

        then:
        builds.commits.first().isTag()
    }

    def "commits, parse committed_at"() {
        given:
        setResponse '''{
            "builds": [{
                "id": 281721,
                "repository_id": 1993,
                "commit_id": 156529,
                "number": "39",
                "pull_request": false,
                "pull_request_title": null,
                "pull_request_number": null,
                "state": "passed",
                "started_at": "2016-04-19T09:19:25Z",
                "finished_at": "2016-04-19T09:23:08Z",
                "duration": 223,
                "job_ids": [281722]
           }],
            "commits": [{
                "id": 38108155,
                "sha": "f290f2af03826999c6004404378a5bc750e834b0",
                "branch": "master",
                "message": "Update README.md",
                "committed_at": "2016-06-01T18:57:48Z",
                "author_name": "Gard Rimestad",
                "author_email": "gardalize@gurters.com",
                "committer_name": "Gard Rimestad",
                "committer_email": "gardalize@gurters.com",
                "compare_url": "https://github.com/gardalize/travis-trigger-test/compare/bd005f51cb1e...f290f2af0382",
                "pull_request_number": null
            }]
        }'''

        when:
        Builds builds = Retrofit2SyncCall.execute(client.builds("someToken", "org/repo", 31))

        then:
        builds.commits.first().timestamp == LocalDateTime.of(2016, Month.JUNE, 1, 18, 57, 48).toInstant(ZoneOffset.UTC)

    }

    def "Parse builds from the v3 api"() {
        given:
        setResponse '''
          {
              "@type": "builds",
              "@href": "/api/repo/spt-infrastructure%2Forca/builds?branch.name=sch_master&limit=2",
              "@representation": "standard",
              "@pagination": {
                  "limit": 2,
                  "offset": 0,
                  "count": 160,
                  "is_first": true,
                  "is_last": false,
                  "next": {
                      "@href": "/api/repo/spt-infrastructure%2Forca/builds?branch.name=sch_master&limit=2&offset=2",
                      "offset": 2,
                      "limit": 2
                  },
                  "prev": null,
                  "first": {
                      "@href": "/api/repo/spt-infrastructure%2Forca/builds?branch.name=sch_master&limit=2",
                      "offset": 0,
                      "limit": 2
                  },
                  "last": {
                      "@href": "/api/repo/spt-infrastructure%2Forca/builds?branch.name=sch_master&limit=2&offset=158",
                      "offset": 158,
                      "limit": 2
                  }
              },
              "builds": [
                  {
                      "@type": "build",
                      "@href": "/api/build/1386282",
                      "@representation": "standard",
                      "@permissions": {
                          "read": true,
                          "cancel": true,
                          "restart": true
                      },
                      "id": 1386282,
                      "number": "389",
                      "state": "passed",
                      "duration": 413,
                      "event_type": "push",
                      "previous_state": "passed",
                      "pull_request_title": null,
                      "pull_request_number": null,
                      "started_at": "2017-06-06T18:06:56Z",
                      "finished_at": "2017-06-06T18:13:49Z",
                      "repository": {
                          "@type": "repository",
                          "@href": "/api/repo/1996",
                          "@representation": "minimal",
                          "id": 1996,
                          "name": "orca",
                          "slug": "spt-infrastructure/orca"
                      },
                      "branch": {
                          "@type": "branch",
                          "@href": "/api/repo/1996/branch/sch_master",
                          "@representation": "minimal",
                          "name": "sch_master"
                      },
                      "commit": {
                          "@type": "commit",
                          "@representation": "minimal",
                          "id": 802307,
                          "sha": "3d38456a3656a65032c4db9c08d3648abb696b58",
                          "ref": "refs/heads/sch_master",
                          "message": "Merge pull request #54 from spt-infrastructure/DOCD-1025\\n\\nDocd 1025",
                          "compare_url": "https://github.schibsted.io/spt-infrastructure/orca/compare/0b373922a733...3d38456a3656",
                          "committed_at": "2017-06-06T18:06:51Z"
                      },
                      "jobs": [
                          {
                              "@type": "job",
                              "@href": "/api/job/1386283",
                              "@representation": "minimal",
                              "id": 1386283
                          }
                      ]
                  },
                  {
                      "@type": "build",
                      "@href": "/api/build/1384443",
                      "@representation": "standard",
                      "@permissions": {
                          "read": true,
                          "cancel": true,
                          "restart": true
                      },
                      "id": 1384443,
                      "number": "388",
                      "state": "passed",
                      "duration": 717,
                      "event_type": "pull_request",
                      "previous_state": "passed",
                      "pull_request_title": "Docd 1025",
                      "pull_request_number": 54,
                      "started_at": "2017-06-06T13:46:06Z",
                      "finished_at": "2017-06-06T13:58:03Z",
                      "repository": {
                          "@type": "repository",
                          "@href": "/api/repo/1996",
                          "@representation": "minimal",
                          "id": 1996,
                          "name": "orca",
                          "slug": "spt-infrastructure/orca"
                      },
                      "branch": {
                          "@type": "branch",
                          "@href": "/api/repo/1996/branch/sch_master",
                          "@representation": "minimal",
                          "name": "sch_master"
                      },
                      "commit": {
                          "@type": "commit",
                          "@representation": "minimal",
                          "id": 801226,
                          "sha": "40e8099ba9cab03febe17b38a650b8868434a068",
                          "ref": "refs/pull/54/merge",
                          "message": "DOCD-1025 enable pipelineTemplates and add proxy",
                          "compare_url": "https://github.schibsted.io/spt-infrastructure/orca/pull/54",
                          "committed_at": "2017-06-06T13:34:13Z"
                      },
                      "jobs": [
                          {
                              "@type": "job",
                              "@href": "/api/job/1384444",
                              "@representation": "minimal",
                              "id": 1384444
                          }
                      ]
                  }
              ]
          }
          '''

        when:
        V3Builds builds = Retrofit2SyncCall.execute(client.v3builds("someToken", "org/repo","bah", 2, null))

        then:
        builds.builds.size() == 2
    }

  def "Fetch a single V3 build"() {
    given:
    setResponse '''
      {
          "@type": "build",
          "@href": "/api/build/7128433",
          "@representation": "standard",
          "@permissions": {
              "read": true,
              "cancel": true,
              "restart": true
          },
          "id": 7128433,
          "number": "265",
          "state": "passed",
          "duration": 19,
          "event_type": "api",
          "previous_state": "passed",
          "pull_request_title": null,
          "pull_request_number": null,
          "started_at": "2019-05-31T10:27:47Z",
          "finished_at": "2019-05-31T10:28:06Z",
          "repository": {
              "@type": "repository",
              "@href": "/api/repo/8881",
              "@representation": "minimal",
              "id": 8881,
              "name": "jervi-is-testing",
              "slug": "arrested-developers/jervi-is-testing"
          },
          "branch": {
              "@type": "branch",
              "@href": "/api/repo/8881/branch/master",
              "@representation": "minimal",
              "name": "master"
          },
          "tag": null,
          "commit": {
              "@type": "commit",
              "@representation": "standard",
              "id": 4113219,
              "sha": "fc726afaaf5d3892dae017e2dbe6fa8534f4423a",
              "ref": null,
              "message": "Triggered from Spinnaker: Hello world! 🐥 awesomeapp:Testing trigger",
              "compare_url": "https://github.acme.io/arrested-developers/jervi-is-testing/compare/5e8a99bbc0e189dfbe1cec4b8da2a48aaa2f672e...fc726afaaf5d3892dae017e2dbe6fa8534f4423a",
              "committed_at": "2019-05-31T10:26:30Z",
              "committer": {
                  "name": "Alice",
                  "avatar_url": "https://0.gravatar.com/avatar/xxxxxxxxxxxxxxxxxx"
              },
              "author": {
                  "name": "Bob",
                  "avatar_url": "https://0.gravatar.com/avatar/xxxxxxxxxxxxxxxxxx"
              }
          },
          "jobs": [
              {
                  "@type": "job",
                  "@href": "/api/job/7128434",
                  "@representation": "minimal",
                  "id": 7128434
              }
          ],
          "stages": [],
          "created_by": {
              "@type": "user",
              "@href": "/api/user/574",
              "@representation": "minimal",
              "id": 574,
              "login": "spinnaker"
          },
          "updated_at": "2019-05-31T10:28:06.312Z",
          "log_complete": true
      }
          '''

    when:
    V3Build build = Retrofit2SyncCall.execute(client.v3build("someToken", 7128433, "build.log_complete"))

    then:
    build.number == 265
    build.commit.committer.name == "Alice"
    build.commit.author.name == "Bob"
    build.jobs*.id == [7128434]
    build.getLogComplete()
  }

    def "commits, dont mark regular branches as tags"() {
        given:
        setResponse '''
            {
                "builds": [{
                               "id": 273844,
                               "repository_id": 1993,
                               "commit_id": 152035,
                               "number": "38",
                               "pull_request": false,
                               "pull_request_title": null,
                               "pull_request_number": null,
                               "state": "passed",
                               "started_at": "2016-04-12T15:47:18Z",
                               "finished_at": "2016-04-12T15:49:57Z",
                               "duration": 159,
                               "job_ids": [273845]
                           }],
                "commits": [{
                            "id": 152035,
                            "sha": "153abe7853a99a3b3553d74ff7d908eec7641138",
                            "branch": "sch_master",
                            "message": "some commit",
                            "committed_at": "2016-04-12T15:46:54Z",
                            "author_name": "Some user",
                            "author_email": "some.user@domain.com",
                            "committer_name": "Some user",
                            "committer_email": "some.user@domain.com",
                                "compare_url": "https://github.domain/org/repo/compare/845cddf3543f...153abe7853a9",
                                "pull_request_number": null
                            }]
            }
        '''

        when:
        Builds builds = Retrofit2SyncCall.execute(client.builds("someToken", "org/repo", 38))

        then:
        !builds.commits.first().isTag()

    }

    def "should parse v3 config section correctly"() {
        given:
        setResponse '''{
          "@type": "builds",
          "@href": "/api/repo/my-org%2Fmy-project/builds?limit=1&include=job.config",
          "@representation": "standard",
          "@pagination": {
            "limit": 1,
            "offset": 0,
            "count": 1213,
            "is_first": true,
            "is_last": false,
            "next": {
              "@href": "/api/repo/my-org%2Fmy-project/builds?include=job.config&limit=1&offset=1",
              "offset": 1,
              "limit": 1
            },
            "prev": null,
            "first": {
              "@href": "/api/repo/my-org%2Fmy-project/builds?limit=1&include=job.config",
              "offset": 0,
              "limit": 1
            },
            "last": {
              "@href": "/api/repo/my-org%2Fmy-project/builds?include=job.config&limit=1&offset=1212",
              "offset": 1212,
              "limit": 1
            }
          },
          "builds": [
            {
              "@type": "build",
              "@href": "/api/build/8078881",
              "@representation": "standard",
              "@permissions": {
                "read": true,
                "cancel": true,
                "restart": true
              },
              "id": 8078881,
              "number": "1213",
              "state": "errored",
              "duration": 19,
              "event_type": "cron",
              "previous_state": "failed",
              "pull_request_title": null,
              "pull_request_number": null,
              "started_at": "2019-09-04T10:47:53Z",
              "finished_at": "2019-09-04T10:48:12Z",
              "repository": {
                "@type": "repository",
                "@href": "/api/repo/10094",
                "@representation": "minimal",
                "id": 10094,
                "name": "my-project",
                "slug": "my-org/my-project"
              },
              "branch": {
                "@type": "branch",
                "@href": "/api/repo/10094/branch/master",
                "@representation": "minimal",
                "name": "master"
              },
              "tag": null,
              "commit": {
                "@type": "commit",
                "@representation": "minimal",
                "id": 4571457,
                "sha": "8af6ccfec7ea3cac6c3e01751186bc9ca5b6062e",
                "ref": null,
                "message": "Release 0.1.28",
                "compare_url": "https://github.acme.com/my-org/my-project/compare/7a7214f554e2b8e313354af08c1010ad90f1e612...8af6ccfec7ea3cac6c3e01751186bc9ca5b6062e",
                "committed_at": "2019-04-24T21:54:13Z"
              },
              "jobs": [
                {
                  "@type": "job",
                  "@href": "/api/job/8078882",
                  "@representation": "minimal",
                  "id": 8078882,
                  "config": {
                    "language": "python",
                    "cache": "pip",
                    "install": "script/bootstrap",
                    "script": "script/cibuild",
                    "after_script": "script/cleanup",
                    ".result": "configured",
                    "global_env": "REGION=eu-west-1 STACK_NAME=testing A_USER=user@schibsted.com A_PWD=[secure] KEY_ID=\\"MY_KEY\\" A_SECRET=[secure] ROLE='arn:aws:iam::0123456789:role/myRole'",
                    "os": "linux",
                    "group": "stable",
                    "dist": "trusty",
                    "addons": {}
                  }
                }
              ],
              "stages": [],
              "created_by": {
                "@type": "user",
                "@href": "/api/user/574",
                "@representation": "minimal",
                "id": 574,
                "login": "spinnaker"
              },
              "updated_at": "2019-09-04T10:48:12.782Z"
            }
          ]
        }'''

        when:
        V3Builds builds = Retrofit2SyncCall.execute(client.v3builds("someToken", "org/repo", 1, "job.config"))

        then:
        builds.builds.size() == 1
        builds.builds[0].jobs[0].config.globalEnv == [
            "REGION=eu-west-1",
            "STACK_NAME=testing",
            "A_USER=user@schibsted.com",
            "A_PWD=[secure]",
            "KEY_ID=MY_KEY",
            "A_SECRET=[secure]",
            "ROLE=arn:aws:iam::0123456789:role/myRole"
        ]
    }

    private void setResponse(String body) {
        server.enqueue(
            new MockResponse()
                .setBody(body)
                .setHeader('Content-Type', 'application/json;charset=utf-8')
        )
        server.start()
        OkHttp3ClientConfiguration okHttpClientConfig = new OkHttp3ClientConfiguration(
            new OkHttpClientConfigurationProperties(), null, HttpLoggingInterceptor.Level.BASIC, null, null, null);
        client = new TravisConfig().travisClient(server.url('/').toString(), 3000, mapper, okHttpClientConfig)
    }
}
