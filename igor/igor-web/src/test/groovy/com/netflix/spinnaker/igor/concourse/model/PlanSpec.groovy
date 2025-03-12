/*
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
import com.netflix.spinnaker.igor.concourse.client.model.Plan
import com.netflix.spinnaker.igor.concourse.client.model.Resource
import java.util.List
import spock.lang.Shared
import spock.lang.Specification

import java.time.Instant

class PlanSpec extends Specification {

    @Shared
    ObjectMapper mapper

    void setup() {
        mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .registerModule(new JavaTimeModule());

    }

    def "it finds nested resources"() {
        given:
        Plan p = planFixture()

        when:
        List<Resource> res = p.getResources()

        then:
        res.size() == 14
        res*.getId() == ['5ed14575', '5ed14574', '5ed14561', '5ed14562', '5ed14563', '5ed14564', '5ed14568', '5ed14567', '5ed1456b', '5ed1456a', '5ed1456e', '5ed1456d', '5ed14571', '5ed14570']
    }

    def "it finds nested resources 2"() {
        given:
        Plan p = planFixture2()

        when:
        List<Resource> res = p.getResources()

        then:
        res.size() == 14
        res*.getId() == ['5f067771', '5f067772', '5f067773', '5f067774', '5f067777', '5f067778', '5f06777a', '5f06777b', '5f06777d', '5f06777e', '5f067780', '5f067781', '5f067784', '5f067785']
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

    private Plan planFixture2() {
        def planJson = """\
            {
              "schema": "exec.v2",
              "plan": {
                "id": "5f067788",
                "do": [
                  {
                    "id": "5f067787",
                    "on_failure": {
                      "step": {
                        "id": "5f067783",
                        "do": [
                          {
                            "id": "5f067775",
                            "in_parallel": {
                              "steps": [
                                {
                                  "id": "5f067771",
                                  "get": {
                                    "type": "git",
                                    "name": "common",
                                    "resource": "common",
                                    "version": {
                                      "ref": "c88a26072f813c698026542289ee77155535e83f"
                                    }
                                  }
                                },
                                {
                                  "id": "5f067772",
                                  "get": {
                                    "type": "semver",
                                    "name": "version",
                                    "resource": "version",
                                    "version": {
                                      "number": "1.0.47"
                                    }
                                  }
                                },
                                {
                                  "id": "5f067773",
                                  "get": {
                                    "type": "git",
                                    "name": "repo",
                                    "resource": "repo",
                                    "version": {
                                      "ref": "13e81c198f9e083b935cc7f2e6f5839423c544e0"
                                    }
                                  }
                                },
                                {
                                  "id": "5f067774",
                                  "get": {
                                    "type": "s3",
                                    "name": "build-slug",
                                    "resource": "build-slug",
                                    "version": {
                                      "path": "build-slugs/cosmos-nextgen-service-product/cosmos-nextgen-service-product-slug-1.6.2.tgz"
                                    }
                                  }
                                }
                              ]
                            }
                          },
                          {
                            "id": "5f067776",
                            "task": {
                              "name": "create-deployable",
                              "privileged": false
                            }
                          },
                          {
                            "id": "5f067779",
                            "on_success": {
                              "step": {
                                "id": "5f067777",
                                "put": {
                                  "type": "docker-image",
                                  "name": "product-api-docker-image",
                                  "resource": "product-api-docker-image"
                                }
                              },
                              "on_success": {
                                "id": "5f067778",
                                "get": {
                                  "type": "docker-image",
                                  "name": "product-api-docker-image",
                                  "resource": "product-api-docker-image"
                                }
                              }
                            }
                          },
                          {
                            "id": "5f06777c",
                            "on_success": {
                              "step": {
                                "id": "5f06777a",
                                "put": {
                                  "type": "s3",
                                  "name": "product-api-deploy-yaml",
                                  "resource": "product-api-deploy-yaml"
                                }
                              },
                              "on_success": {
                                "id": "5f06777b",
                                "get": {
                                  "type": "s3",
                                  "name": "product-api-deploy-yaml",
                                  "resource": "product-api-deploy-yaml"
                                }
                              }
                            }
                          },
                          {
                            "id": "5f06777f",
                            "on_success": {
                              "step": {
                                "id": "5f06777d",
                                "put": {
                                  "type": "semver",
                                  "name": "version",
                                  "resource": "version"
                                }
                              },
                              "on_success": {
                                "id": "5f06777e",
                                "get": {
                                  "type": "semver",
                                  "name": "version",
                                  "resource": "version"
                                }
                              }
                            }
                          },
                          {
                            "id": "5f067782",
                            "on_success": {
                              "step": {
                                "id": "5f067780",
                                "put": {
                                  "type": "git",
                                  "name": "repo",
                                  "resource": "repo"
                                }
                              },
                              "on_success": {
                                "id": "5f067781",
                                "get": {
                                  "type": "git",
                                  "name": "repo",
                                  "resource": "repo"
                                }
                              }
                            }
                          }
                        ]
                      },
                      "on_failure": {
                        "id": "5f067786",
                        "on_success": {
                          "step": {
                            "id": "5f067784",
                            "put": {
                              "type": "slack-notification",
                              "name": "notify",
                              "resource": "notify"
                            }
                          },
                          "on_success": {
                            "id": "5f067785",
                            "get": {
                              "type": "slack-notification",
                              "name": "notify",
                              "resource": "notify"
                            }
                          }
                        }
                      }
                    }
                  }
                ]
              }
            }""".stripIndent()

        return mapper.readValue(planJson, Plan.class)
    }

}
