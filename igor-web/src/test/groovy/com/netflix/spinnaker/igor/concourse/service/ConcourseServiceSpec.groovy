/*
 * Copyright 2020 Weld North Education.
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

package com.netflix.spinnaker.igor.concourse.service

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.spinnaker.igor.config.ConcourseProperties
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.igor.build.artifact.decorator.DebDetailsDecorator
import com.netflix.spinnaker.igor.build.artifact.decorator.RpmDetailsDecorator
import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.build.model.Result
import com.netflix.spinnaker.igor.service.ArtifactDecorator
import com.netflix.spinnaker.igor.concourse.ConcourseCache
import com.netflix.spinnaker.igor.concourse.client.BuildService
import com.netflix.spinnaker.igor.concourse.client.ConcourseClient
import com.netflix.spinnaker.igor.concourse.client.EventService
import com.netflix.spinnaker.igor.concourse.client.model.Build
import com.netflix.spinnaker.igor.concourse.client.model.Event
import com.netflix.spinnaker.igor.concourse.client.model.Plan
import com.netflix.spinnaker.igor.helpers.TestUtils
import retrofit2.mock.Calls

import java.util.List
import reactor.core.publisher.Flux;
import spock.lang.Shared
import spock.lang.Specification

import java.time.Instant

class ConcourseServiceSpec extends Specification {
    @Shared
    ConcourseClient client

    @Shared
    BuildService buildService

    @Shared
    EventService eventService

    @Shared
    ObjectMapper mapper

    ConcourseService service

    @Shared
    Optional<ArtifactDecorator> artifactDecorator

    void setup() {
        mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .registerModule(new JavaTimeModule());

        buildService = Mock()
        eventService = Mock()

        client = Mock()
        client.getBuildService() >> buildService
        client.getEventService() >> eventService

        artifactDecorator = Optional.of(new ArtifactDecorator([new DebDetailsDecorator(), new RpmDetailsDecorator()], null))

        ConcourseProperties.Host host = new ConcourseProperties.Host()
        host.name = 'concourse-ci'
        host.url = 'http://my.concourse.ci'
        host.username = 'user'
        host.password = 'pass'
        host.teams = ['myteam']

        service = new ConcourseService(client, host, artifactDecorator)
    }

    def "getGenericBuild(jobPath, buildNumber)"() {
        given:
        buildService.builds('myteam', 'mypipeline', 'myjob', _, _) >> Calls.response(builds('49', '48.1', '48', '47'))
        buildService.plan(_) >> Calls.response(planFixture())
        eventService.resourceEvents(_) >> eventFixture()

        when:
        GenericBuild genericBuild = service.getGenericBuild('myteam/mypipeline/myjob', 48)

        then:
        genericBuild.number == 48
        genericBuild.id == "48.1-id"
        genericBuild.timestamp == "1421717251402000"
        genericBuild.url == "http://my.concourse.ci/teams/myteam/pipelines/mypipeline/jobs/myjob/builds/48.1"
        genericBuild.genericGitRevisions[0].branch == "master"
    }

    def "getGenericBuildMissingBranch(jobPath, buildNumber)"() {
        given:
        buildService.builds('myteam', 'mypipeline', 'myjob', _, _) >> Calls.response(builds('49', '48.1', '48', '47'))
        buildService.plan(_) >> Calls.response(planFixtureNoBranch())
        eventService.resourceEvents(_) >> eventFixtureNoBranch()

        when:
        GenericBuild genericBuild = service.getGenericBuild('myteam/mypipeline/myjob', 48)

        then:
        genericBuild.number == 48
        genericBuild.id == "48.1-id"
        genericBuild.timestamp == "1421717251402000"
        genericBuild.url == "http://my.concourse.ci/teams/myteam/pipelines/mypipeline/jobs/myjob/builds/48.1"
        genericBuild.genericGitRevisions[0].branch == "b2a1834"
    }

    def "getBuilds(jobPath, since)"() {
        given:
        buildService.builds('myteam', 'mypipeline', 'myjob', _, 1421717251402) >> Calls.response(builds('49', '48.1', '48', '47'))
        buildService.plan(_) >> Calls.response(planFixture())
        eventService.resourceEvents(_) >> (eventFixture())

        when:
        List<Build> builds = service.getBuilds('myteam/mypipeline/myjob', 1421717251402)

        then:
        builds.size() == 3
        builds[0].id == '49-id'
        builds[1].id == '48.1-id'
        builds[2].id == '47-id'
    }

    private static List<Build> builds(String... nums) {
        nums.collect { it ->
            new Build().with { b ->
                b.id = "${it}-id"
                b.name = it
                b.startTime = 1421717251402
                b.status = 'succeeded'
                b
            }
        }
    }

    private Plan planFixture() {
        def planJson = """\
            {
                "plan": {
                    "id": "5ed14577",
                    "on_failure": {
                        "on_failure": {
                            "id": "5ed14576",
                            "on_success": {
                                "on_success": {
                                    "get": {
                                        "name": "notify",
                                        "resource": "notify",
                                        "type": "slack-notification"
                                    },
                                    "id": "5ed14575"
                                },
                                "step": {
                                    "id": "5ed14574",
                                    "put": {
                                        "name": "notify",
                                        "resource": "notify",
                                        "type": "slack-notification"
                                    }
                                }
                            }
                        },
                        "step": {
                            "do": [
                                {
                                    "id": "5ed14565",
                                    "in_parallel": {
                                        "steps": [
                                            {
                                                "get": {
                                                    "name": "common",
                                                    "resource": "common",
                                                    "type": "git",
                                                    "version": {
                                                        "ref": "f16f80615a204423824bc987b382e5ad0199b36c"
                                                    }
                                                },
                                                "id": "5ed14561"
                                            },
                                            {
                                                "get": {
                                                    "name": "version",
                                                    "resource": "version",
                                                    "type": "semver",
                                                    "version": {
                                                        "number": "1.0.178"
                                                    }
                                                },
                                                "id": "5ed14562"
                                            },
                                            {
                                                "get": {
                                                    "name": "repo",
                                                    "resource": "repo",
                                                    "type": "git",
                                                    "version": {
                                                        "ref": "65a7c129fe402634ee1d94b5ba2577fd46a039f2"
                                                    }
                                                },
                                                "id": "5ed14563"
                                            },
                                            {
                                                "get": {
                                                    "name": "build-slug",
                                                    "resource": "build-slug",
                                                    "type": "s3",
                                                    "version": {
                                                        "path": "build-slugs/gateway/gateway-slug-1.17.7.tgz"
                                                    }
                                                },
                                                "id": "5ed14564"
                                            }
                                        ]
                                    }
                                },
                                {
                                    "id": "5ed14566",
                                    "task": {
                                        "name": "create-deployable",
                                        "privileged": false
                                    }
                                },
                                {
                                    "id": "5ed14569",
                                    "on_success": {
                                        "on_success": {
                                            "get": {
                                                "name": "gateway-service-docker-image",
                                                "resource": "gateway-service-docker-image",
                                                "type": "docker-image"
                                            },
                                            "id": "5ed14568"
                                        },
                                        "step": {
                                            "id": "5ed14567",
                                            "put": {
                                                "name": "gateway-service-docker-image",
                                                "resource": "gateway-service-docker-image",
                                                "type": "docker-image"
                                            }
                                        }
                                    }
                                },
                                {
                                    "id": "5ed1456c",
                                    "on_success": {
                                        "on_success": {
                                            "get": {
                                                "name": "gateway-service-deploy-yaml",
                                                "resource": "gateway-service-deploy-yaml",
                                                "type": "s3"
                                            },
                                            "id": "5ed1456b"
                                        },
                                        "step": {
                                            "id": "5ed1456a",
                                            "put": {
                                                "name": "gateway-service-deploy-yaml",
                                                "resource": "gateway-service-deploy-yaml",
                                                "type": "s3"
                                            }
                                        }
                                    }
                                },
                                {
                                    "id": "5ed1456f",
                                    "on_success": {
                                        "on_success": {
                                            "get": {
                                                "name": "version",
                                                "resource": "version",
                                                "type": "semver"
                                            },
                                            "id": "5ed1456e"
                                        },
                                        "step": {
                                            "id": "5ed1456d",
                                            "put": {
                                                "name": "version",
                                                "resource": "version",
                                                "type": "semver"
                                            }
                                        }
                                    }
                                },
                                {
                                    "id": "5ed14572",
                                    "on_success": {
                                        "on_success": {
                                            "get": {
                                                "name": "repo",
                                                "resource": "repo",
                                                "type": "git"
                                            },
                                            "id": "5ed14571"
                                        },
                                        "step": {
                                            "id": "5ed14570",
                                            "put": {
                                                "name": "repo",
                                                "resource": "repo",
                                                "type": "git"
                                            }
                                        }
                                    }
                                }
                            ],
                            "id": "5ed14573"
                        }
                    }
                },
                "schema": "exec.v2"
            }""".stripIndent()

        return mapper.readValue(planJson, Plan.class)
    }

    private Flux<Event> eventFixture() throws Exception {
        def eventsJson = '''[\
            {"data":{"origin":{"id":"5ed14561"},"time":1591654781,"exit_status":0,"version":{"ref":"f16f80615a204423824bc987b382e5ad0199b36c"},"metadata":[{"name":"commit","value":"f16f80615a204423824bc987b382e5ad0199b36c"},{"name":"author","value":"Jared Stehler"},{"name":"author_date","value":"2020-06-06 11:20:28 -0400"},{"name":"committer","value":"Jared Stehler"},{"name":"committer_date","value":"2020-06-06 11:20:28 -0400"},{"name":"branch","value":"master"},{"name":"message","value":"temp fix\\n"},{"name":"url","value":"https://github.com/myteam/common/commit/f16f80615a204423824bc987b382e5ad0199b36c"}]},"event":"finish-get","version":"5.1"},
            {"data":{"origin":{"id":"5ed14562"},"time":1591654783,"exit_status":0,"version":{"number":"1.0.178"},"metadata":[{"name":"number","value":"1.0.178"}]},"event":"finish-get","version":"5.1"},
            {"data":{"origin":{"id":"5ed14564"},"time":1591654785,"exit_status":0,"version":{"path":"build-slugs/gateway/gateway-slug-1.17.7.tgz"},"metadata":[{"name":"filename","value":"gateway-slug-1.17.7.tgz"},{"name":"url","value":"https://s3-us-west-2.amazonaws.com/artifacts-bucket/build-slugs/gateway/gateway-slug-1.17.7.tgz"}]},"event":"finish-get","version":"5.1"},
            {"data":{"origin":{"id":"5ed14563"},"time":1591654786,"exit_status":0,"version":{"ref":"65a7c129fe402634ee1d94b5ba2577fd46a039f2"},"metadata":[{"name":"commit","value":"65a7c129fe402634ee1d94b5ba2577fd46a039f2"},{"name":"author","value":"John Smith"},{"name":"author_date","value":"2020-06-08 15:13:23 -0700"},{"name":"branch","value":"master"},{"name":"message","value":"DOOL-1990: reopening question sets completionPercent to zero\\n\\n"},{"name":"url","value":"https://github.com/myteam/gateway/commit/65a7c129fe402634ee1d94b5ba2577fd46a039f2"}]},"event":"finish-get","version":"5.1"},
            {"data":{"origin":{"id":"5ed14567"},"time":1591654846,"exit_status":0,"version":{"digest":"sha256:30bf1745fc5884469fb61f1652374e2f1befc9e937754a0b9914fe886111c96d"},"metadata":[{"name":"image","value":"sha256:81dcf"}]},"event":"finish-put","version":"5.1"},
            {"data":{"origin":{"id":"5ed14568"},"time":1591654866,"exit_status":0,"version":{"digest":"sha256:30bf1745fc5884469fb61f1652374e2f1befc9e937754a0b9914fe886111c96d"},"metadata":[{"name":"image","value":"sha256:81dcf"}]},"event":"finish-get","version":"5.1"},
            {"data":{"origin":{"id":"5ed1456a"},"time":1591654894,"exit_status":0,"version":{"path":"artifacts/gateway-service/deploy-1.0.178-65a7c12.yaml"},"metadata":[{"name":"filename","value":"deploy-1.0.178-65a7c12.yaml"},{"name":"url","value":"https://s3-us-west-2.amazonaws.com/artifacts-bucket/artifacts/gateway-service/deploy-1.0.178-65a7c12.yaml"}]},"event":"finish-put","version":"5.1"},
            {"data":{"origin":{"id":"5ed1456b"},"time":1591654896,"exit_status":0,"version":{"path":"artifacts/gateway-service/deploy-1.0.178-65a7c12.yaml"},"metadata":[{"name":"filename","value":"deploy-1.0.178-65a7c12.yaml"},{"name":"url","value":"https://s3-us-west-2.amazonaws.com/artifacts-bucket/artifacts/gateway-service/deploy-1.0.178-65a7c12.yaml"}]},"event":"finish-get","version":"5.1"},
            {"data":{"origin":{"id":"5ed1456d"},"time":1591654924,"exit_status":0,"version":{"number":"1.0.178"},"metadata":[{"name":"number","value":"1.0.178"}]},"event":"finish-put","version":"5.1"},
            {"data":{"origin":{"id":"5ed1456e"},"time":1591654926,"exit_status":0,"version":{"number":"1.0.178"},"metadata":[{"name":"number","value":"1.0.178"}]},"event":"finish-get","version":"5.1"},
            {"data":{"origin":{"id":"5ed14570"},"time":1591654959,"exit_status":0,"version":{"ref":"65a7c129fe402634ee1d94b5ba2577fd46a039f2"},"metadata":[{"name":"commit","value":"65a7c129fe402634ee1d94b5ba2577fd46a039f2"},{"name":"author","value":"John Smith"},{"name":"author_date","value":"2020-06-08 15:13:23 -0700"},{"name":"branch","value":"master"},{"name":"tags","value":"v1.0.178"},{"name":"message","value":"DOOL-1990: reopening question sets completionPercent to zero\\n\\n"},{"name":"url","value":"https://github.com/myteam/gateway/commit/65a7c129fe402634ee1d94b5ba2577fd46a039f2"}]},"event":"finish-put","version":"5.1"},
            {"data":{"origin":{"id":"5ed14571"},"time":1591654963,"exit_status":0,"version":{"ref":"65a7c129fe402634ee1d94b5ba2577fd46a039f2"},"metadata":[{"name":"commit","value":"65a7c129fe402634ee1d94b5ba2577fd46a039f2"},{"name":"author","value":"John Smith"},{"name":"author_date","value":"2020-06-08 15:13:23 -0700"},{"name":"branch","value":"master"},{"name":"tags","value":"v1.0.178"},{"name":"message","value":"DOOL-1990: reopening question sets completionPercent to zero\\n\\n"},{"name":"url","value":"https://github.com/myteam/gateway/commit/65a7c129fe402634ee1d94b5ba2577fd46a039f2"}]},"event":"finish-get","version":"5.1"}
        ]'''.stripIndent()

        def events = mapper.readValue(eventsJson, new TypeReference<List<Event>>(){})
        return Flux.fromIterable(events)
    }

    private Plan planFixtureNoBranch() {
        def planJson = """\
            {
                "plan": {
                    "do": [
                        {
                            "get": {
                                "name": "repo",
                                "resource": "repo",
                                "type": "git",
                                "version": {
                                    "ref": "b2a18347d4669c25ab23f626edea622ec8ffe7aa"
                                }
                            },
                            "id": "5f06bca6"
                        },
                        {
                            "get": {
                                "name": "version",
                                "resource": "version",
                                "type": "semver",
                                "version": {
                                    "number": "1.0.40"
                                }
                            },
                            "id": "5f06bca7"
                        },
                        {
                            "id": "5f06bca8",
                            "task": {
                                "name": "build-kops",
                                "privileged": false
                            }
                        },
                        {
                            "id": "5f06bcab",
                            "on_success": {
                                "on_success": {
                                    "get": {
                                        "name": "nonprod-cluster-spec",
                                        "resource": "nonprod-cluster-spec",
                                        "type": "s3"
                                    },
                                    "id": "5f06bcaa"
                                },
                                "step": {
                                    "id": "5f06bca9",
                                    "put": {
                                        "name": "nonprod-cluster-spec",
                                        "resource": "nonprod-cluster-spec",
                                        "type": "s3"
                                    }
                                }
                            }
                        },
                        {
                            "id": "5f06bcae",
                            "on_success": {
                                "on_success": {
                                    "get": {
                                        "name": "prod-cluster-spec",
                                        "resource": "prod-cluster-spec",
                                        "type": "s3"
                                    },
                                    "id": "5f06bcad"
                                },
                                "step": {
                                    "id": "5f06bcac",
                                    "put": {
                                        "name": "prod-cluster-spec",
                                        "resource": "prod-cluster-spec",
                                        "type": "s3"
                                    }
                                }
                            }
                        },
                        {
                            "id": "5f06bcb1",
                            "on_success": {
                                "on_success": {
                                    "get": {
                                        "name": "ops-cluster-spec",
                                        "resource": "ops-cluster-spec",
                                        "type": "s3"
                                    },
                                    "id": "5f06bcb0"
                                },
                                "step": {
                                    "id": "5f06bcaf",
                                    "put": {
                                        "name": "ops-cluster-spec",
                                        "resource": "ops-cluster-spec",
                                        "type": "s3"
                                    }
                                }
                            }
                        },
                        {
                            "id": "5f06bcb4",
                            "on_success": {
                                "on_success": {
                                    "get": {
                                        "name": "version",
                                        "resource": "version",
                                        "type": "semver"
                                    },
                                    "id": "5f06bcb3"
                                },
                                "step": {
                                    "id": "5f06bcb2",
                                    "put": {
                                        "name": "version",
                                        "resource": "version",
                                        "type": "semver"
                                    }
                                }
                            }
                        }
                    ],
                    "id": "5f06bcb5"
                },
                "schema": "exec.v2"
            }""".stripIndent()

        return mapper.readValue(planJson, Plan.class)
    }

    private Flux<Event> eventFixtureNoBranch() throws Exception {
        def eventsJson = '''[\
            {"data":{"origin":{"id":"5f06bca6"},"time":1595715621,"exit_status":0,"version":{"ref":"b2a18347d4669c25ab23f626edea622ec8ffe7aa"},"metadata":[{"name":"commit","value":"b2a18347d4669c25ab23f626edea622ec8ffe7aa"},{"name":"author","value":"Jared Stehler"},{"name":"author_date","value":"2020-07-25 17:58:28 -0400"},{"name":"committer","value":"Jared Stehler"},{"name":"committer_date","value":"2020-07-25 17:58:28 -0400"},{"name":"message","value":"[DOOL-2652] upgrade k8s to 1.17.9\\n"},{"name":"url","value":"https://github.com/myteam/cloud/commit/b2a18347d4669c25ab23f626edea622ec8ffe7aa"}]},"event":"finish-get","version":"5.1"},
            {"data":{"origin":{"id":"5f06bca7"},"time":1595715622,"exit_status":0,"version":{"number":"1.0.40"},"metadata":[{"name":"number","value":"1.0.40"}]},"event":"finish-get","version":"5.1"},
            {"data":{"origin":{"id":"5f06bca9"},"time":1595715631,"exit_status":0,"version":{"path":"artifacts/kops/nonprod/nonprod-cluster-spec-1.0.41-b2a1834.yaml"},"metadata":[{"name":"filename","value":"nonprod-cluster-spec-1.0.41-b2a1834.yaml"},{"name":"url","value":"https://s3-us-west-2.amazonaws.com/artifacts-bucket/artifacts/kops/nonprod/nonprod-cluster-spec-1.0.41-b2a1834.yaml"}]},"event":"finish-put","version":"5.1"},
            {"data":{"origin":{"id":"5f06bcaa"},"time":1595715632,"exit_status":0,"version":{"path":"artifacts/kops/nonprod/nonprod-cluster-spec-1.0.41-b2a1834.yaml"},"metadata":[{"name":"filename","value":"nonprod-cluster-spec-1.0.41-b2a1834.yaml"},{"name":"url","value":"https://s3-us-west-2.amazonaws.com/artifacts-bucket/artifacts/kops/nonprod/nonprod-cluster-spec-1.0.41-b2a1834.yaml"}]},"event":"finish-get","version":"5.1"},
            {"data":{"origin":{"id":"5f06bcac"},"time":1595715638,"exit_status":0,"version":{"path":"artifacts/kops/prod/prod-cluster-spec-1.0.41-b2a1834.yaml"},"metadata":[{"name":"filename","value":"prod-cluster-spec-1.0.41-b2a1834.yaml"},{"name":"url","value":"https://s3-us-west-2.amazonaws.com/artifacts-bucket/artifacts/kops/prod/prod-cluster-spec-1.0.41-b2a1834.yaml"}]},"event":"finish-put","version":"5.1"},
            {"data":{"origin":{"id":"5f06bcad"},"time":1595715640,"exit_status":0,"version":{"path":"artifacts/kops/prod/prod-cluster-spec-1.0.41-b2a1834.yaml"},"metadata":[{"name":"filename","value":"prod-cluster-spec-1.0.41-b2a1834.yaml"},{"name":"url","value":"https://s3-us-west-2.amazonaws.com/artifacts-bucket/artifacts/kops/prod/prod-cluster-spec-1.0.41-b2a1834.yaml"}]},"event":"finish-get","version":"5.1"},
            {"data":{"origin":{"id":"5f06bcaf"},"time":1595715646,"exit_status":0,"version":{"path":"artifacts/kops/ops/ops-cluster-spec-1.0.41-b2a1834.yaml"},"metadata":[{"name":"filename","value":"ops-cluster-spec-1.0.41-b2a1834.yaml"},{"name":"url","value":"https://s3-us-west-2.amazonaws.com/artifacts-bucket/artifacts/kops/ops/ops-cluster-spec-1.0.41-b2a1834.yaml"}]},"event":"finish-put","version":"5.1"},
            {"data":{"origin":{"id":"5f06bcb0"},"time":1595715648,"exit_status":0,"version":{"path":"artifacts/kops/ops/ops-cluster-spec-1.0.41-b2a1834.yaml"},"metadata":[{"name":"filename","value":"ops-cluster-spec-1.0.41-b2a1834.yaml"},{"name":"url","value":"https://s3-us-west-2.amazonaws.com/artifacts-bucket/artifacts/kops/ops/ops-cluster-spec-1.0.41-b2a1834.yaml"}]},"event":"finish-get","version":"5.1"},
            {"data":{"origin":{"id":"5f06bcb2"},"time":1595715660,"exit_status":0,"version":{"number":"1.0.41"},"metadata":[{"name":"number","value":"1.0.41"}]},"event":"finish-put","version":"5.1"},
            {"data":{"origin":{"id":"5f06bcb3"},"time":1595715662,"exit_status":0,"version":{"number":"1.0.41"},"metadata":[{"name":"number","value":"1.0.41"}]},"event":"finish-get","version":"5.1"}
        ]'''.stripIndent()

        def events = mapper.readValue(eventsJson, new TypeReference<List<Event>>(){})
        return Flux.fromIterable(events)
    }
}
