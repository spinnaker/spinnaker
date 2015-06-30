/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.oort.gce.model

import spock.lang.Specification
import spock.lang.Subject

class GoogleLoadBalancerProviderSpec extends Specification {

  def networkLoadBalancerMap

  def setup() {
    networkLoadBalancerMap = [
      "my-account-name": [
        "us-central1": [
          [
            name: "roscoapp-dev-frontend1",
            type: "gce",
            region: "us-central1",
            account: "my-account-name",
            serverGroups: [
              [
                name: "roscoapp-dev-v001",
                isDisabled: false,
                instances: [
                  [
                    id: "roscoapp-dev-v001-vx09",
                    zone: "us-central1-a",
                    health: [
                      state: "InService",
                      description: null
                    ]
                  ]
                ]
              ],
              [
                name: "roscoapp-dev-v002",
                isDisabled: false,
                instances: [
                  [
                    id: "roscoapp-dev-v002-ab05",
                    zone: "us-central1-a",
                    health: [
                      state: "InService",
                      description: null
                    ]
                  ]
                ]
              ]
            ]
          ],
          [
            name: "roscoapp-dev-frontend2",
            type: "gce",
            region: "us-central1",
            account: "my-account-name"
          ],
          [
            name: "roscoapp-dev-frontend3",
            type: "gce",
            region: "us-central1",
            account: "my-account-name"
          ]
        ],
        "europe-west1": [
          [
            name: "roscoapp-dev-frontend4",
            type: "gce",
            region: "europe-west1",
            account: "my-account-name"
          ],
          [
            name: "roscoapp-dev-frontend5",
            type: "gce",
            region: "europe-west1",
            account: "my-account-name"
          ],
          [
            name: "roscoapp-dev-frontend6",
            type: "gce",
            region: "europe-west1",
            account: "my-account-name"
          ],
          [
            name: "testapp-dev-frontend1",
            type: "gce",
            region: "europe-west1",
            account: "my-account-name"
          ]
        ],
        "asia-east1": [
          [
            name: "testapp-dev-frontend2",
            type: "gce",
            region: "asia-east1",
            account: "my-account-name"
          ],
          [
            name: "testapp-dev-frontend3",
            type: "gce",
            region: "asia-east1",
            account: "my-account-name",
            serverGroups: [
              [
                name: "roscoapp-dev-v001",
                isDisabled: false,
                instances: [
                  [
                    id: "roscoapp-dev-v001-vx09",
                    zone: "asia-east1-a",
                    health: [
                      state: "InService",
                      description: null
                    ]
                  ]
                ]
              ],
              [
                name: "roscoapp3-dev-v002",
                isDisabled: false,
                instances: [
                  [
                    id: "roscoapp3-dev-v002-gh07",
                    zone: "asia-east1-a",
                    health: [
                      state: "InService",
                      description: null
                    ]
                  ]
                ]
              ]
            ]
          ],
          [
            name: "testapp-dev-frontend4",
            type: "gce",
            region: "asia-east1",
            account: "my-account-name"
          ]
        ]
      ],
      "other-account-name": [
        "us-central1": [
          [
            name: "roscoapp-dev-frontend1",
            type: "gce",
            region: "us-central1",
            account: "other-account-name"
          ],
          [
            name: "roscoapp-dev-frontend2",
            type: "gce",
            region: "us-central1",
            account: "other-account-name"
          ],
          [
            name: "roscoapp-dev-frontend3",
            type: "gce",
            region: "us-central1",
            account: "other-account-name"
          ]
        ],
        "europe-west1": [
          [
            name: "roscoapp-dev-frontend4",
            type: "gce",
            region: "europe-west1",
            account: "other-account-name"
          ],
          [
            name: "roscoapp-dev-frontend5",
            type: "gce",
            region: "europe-west1",
            account: "other-account-name"
          ],
          [
            name: "roscoapp-dev-frontend7",
            type: "gce",
            region: "europe-west1",
            account: "other-account-name"
          ],
          [
            name: "testapp-dev-frontend1",
            type: "gce",
            region: "europe-west1",
            account: "other-account-name"
          ]
        ],
        "asia-east1": [
          [
            name: "testapp-dev-frontend2",
            type: "gce",
            region: "asia-east1",
            account: "other-account-name"
          ],
          [
            name: "testapp-dev-frontend3",
            type: "gce",
            region: "asia-east1",
            account: "other-account-name"
          ],
          [
            name: "testapp-dev-frontend4",
            type: "gce",
            region: "asia-east1",
            account: "other-account-name"
          ]
        ]
      ]
    ]
  }

  void "application load balancers are returned based on naming-convention and server group associations, and do include server groups from other applications"() {
    setup:
      def resourceRetrieverMock = Mock(GoogleResourceRetriever)
      @Subject
      def googleLoadBalancerProvider = new GoogleLoadBalancerProvider()
      googleLoadBalancerProvider.googleResourceRetriever = resourceRetrieverMock

    when:
      def applicationLoadBalancers = googleLoadBalancerProvider.getApplicationLoadBalancers("roscoapp")

    then:
      1 * resourceRetrieverMock.getNetworkLoadBalancerMap() >> networkLoadBalancerMap
      applicationLoadBalancers == [
        new GoogleLoadBalancer(
          name: "roscoapp-dev-frontend1",
          region: "us-central1",
          account: "my-account-name",
          serverGroups: [
            [
              name: "roscoapp-dev-v001",
              isDisabled: false,
              instances: [
                [
                  id: "roscoapp-dev-v001-vx09",
                  zone: "us-central1-a",
                  health: [
                    state: "InService",
                    description: null
                  ]
                ]
              ]
            ],
            [
              name: "roscoapp-dev-v002",
              isDisabled: false,
              instances: [
                [
                  id: "roscoapp-dev-v002-ab05",
                  zone: "us-central1-a",
                  health: [
                    state: "InService",
                    description: null
                  ]
                ]
              ]
            ]
          ]
        ),
        new GoogleLoadBalancer(
          name: "roscoapp-dev-frontend2",
          region: "us-central1",
          account: "my-account-name",
          serverGroups: [] as Set
        ),
        new GoogleLoadBalancer(
          name: "roscoapp-dev-frontend3",
          region: "us-central1",
          account: "my-account-name",
          serverGroups: [] as Set
        ),
        new GoogleLoadBalancer(
          name: "roscoapp-dev-frontend4",
          region: "europe-west1",
          account: "my-account-name",
          serverGroups: [] as Set
        ),
        new GoogleLoadBalancer(
          name: "roscoapp-dev-frontend5",
          region: "europe-west1",
          account: "my-account-name",
          serverGroups: [] as Set
        ),
        new GoogleLoadBalancer(
          name: "roscoapp-dev-frontend6",
          region: "europe-west1",
          account: "my-account-name",
          serverGroups: [] as Set
        ),
        new GoogleLoadBalancer(
          name: "roscoapp-dev-frontend1",
          region: "us-central1",
          account: "other-account-name",
          serverGroups: [] as Set
        ),
        new GoogleLoadBalancer(
          name: "roscoapp-dev-frontend2",
          region: "us-central1",
          account: "other-account-name",
          serverGroups: [] as Set
        ),
        new GoogleLoadBalancer(
          name: "roscoapp-dev-frontend3",
          region: "us-central1",
          account: "other-account-name",
          serverGroups: [] as Set
        ),
        new GoogleLoadBalancer(
          name: "roscoapp-dev-frontend4",
          region: "europe-west1",
          account: "other-account-name",
          serverGroups: [] as Set
        ),
        new GoogleLoadBalancer(
          name: "roscoapp-dev-frontend5",
          region: "europe-west1",
          account: "other-account-name",
          serverGroups: [] as Set
        ),
        new GoogleLoadBalancer(
          name: "roscoapp-dev-frontend7",
          region: "europe-west1",
          account: "other-account-name",
          serverGroups: [] as Set
        ),
        new GoogleLoadBalancer(
          name: "testapp-dev-frontend3",
          region: "asia-east1",
          account: "my-account-name",
          serverGroups: [
            [
              name: "roscoapp-dev-v001",
              isDisabled: false,
              instances: [
                [
                  id: "roscoapp-dev-v001-vx09",
                  zone: "asia-east1-a",
                  health: [
                    state: "InService",
                    description: null
                  ]
                ]
              ]
            ],
            [
              name: "roscoapp3-dev-v002",
              isDisabled: false,
              instances: [
                [
                  id: "roscoapp3-dev-v002-gh07",
                  zone: "asia-east1-a",
                  health: [
                    state: "InService",
                    description: null
                  ]
                ]
              ]
            ]
          ]
        )
      ] as Set

    when:
      applicationLoadBalancers = googleLoadBalancerProvider.getApplicationLoadBalancers("testapp")

    then:
      1 * resourceRetrieverMock.getNetworkLoadBalancerMap() >> networkLoadBalancerMap
      applicationLoadBalancers == [
        new GoogleLoadBalancer(
          name: "testapp-dev-frontend1",
          region: "europe-west1",
          account: "my-account-name",
          serverGroups: [] as Set
        ),
        new GoogleLoadBalancer(
          name: "testapp-dev-frontend2",
          region: "asia-east1",
          account: "my-account-name",
          serverGroups: [] as Set
        ),
        new GoogleLoadBalancer(
          name: "testapp-dev-frontend3",
          region: "asia-east1",
          account: "my-account-name",
          serverGroups: [
            [
              name: "roscoapp-dev-v001",
              isDisabled: false,
              instances: [
                [
                  id: "roscoapp-dev-v001-vx09",
                  zone: "asia-east1-a",
                  health: [
                    state: "InService",
                    description: null
                  ]
                ]
              ]
            ],
            [
              name: "roscoapp3-dev-v002",
              isDisabled: false,
              instances: [
                [
                  id: "roscoapp3-dev-v002-gh07",
                  zone: "asia-east1-a",
                  health: [
                    state: "InService",
                    description: null
                  ]
                ]
              ]
            ]
          ]
        ),
        new GoogleLoadBalancer(
          name: "testapp-dev-frontend4",
          region: "asia-east1",
          account: "my-account-name",
          serverGroups: [] as Set
        ),
        new GoogleLoadBalancer(
          name: "testapp-dev-frontend1",
          region: "europe-west1",
          account: "other-account-name",
          serverGroups: [] as Set
        ),
        new GoogleLoadBalancer(
          name: "testapp-dev-frontend2",
          region: "asia-east1",
          account: "other-account-name",
          serverGroups: [] as Set
        ),
        new GoogleLoadBalancer(
          name: "testapp-dev-frontend3",
          region: "asia-east1",
          account: "other-account-name",
          serverGroups: [] as Set
        ),
        new GoogleLoadBalancer(
          name: "testapp-dev-frontend4",
          region: "asia-east1",
          account: "other-account-name",
          serverGroups: [] as Set
        )
      ] as Set

    when:
      applicationLoadBalancers = googleLoadBalancerProvider.getApplicationLoadBalancers("somethingelse")

    then:
      1 * resourceRetrieverMock.getNetworkLoadBalancerMap() >> networkLoadBalancerMap
      !applicationLoadBalancers
  }

  void "can locate load balancers by account"() {
    setup:
      def resourceRetrieverMock = Mock(GoogleResourceRetriever)
      @Subject
      def googleLoadBalancerProvider = new GoogleLoadBalancerProvider()
      googleLoadBalancerProvider.googleResourceRetriever = resourceRetrieverMock

    when:
      def loadBalancers = googleLoadBalancerProvider.getLoadBalancers("my-account-name")

    then:
      1 * resourceRetrieverMock.getNetworkLoadBalancerMap() >> networkLoadBalancerMap
      loadBalancers == [
        [
          name: "roscoapp-dev-frontend1",
          type: "gce",
          region: "us-central1",
          account: "my-account-name",
          serverGroups: [
            [
              name: "roscoapp-dev-v001",
              isDisabled: false,
              instances: [
                [
                  id: "roscoapp-dev-v001-vx09",
                  zone: "us-central1-a",
                  health: [
                    state: "InService",
                    description: null
                  ]
                ]
              ]
            ],
            [
              name: "roscoapp-dev-v002",
              isDisabled: false,
              instances: [
                [
                  id: "roscoapp-dev-v002-ab05",
                  zone: "us-central1-a",
                  health: [
                    state: "InService",
                    description: null
                  ]
                ]
              ]
            ]
          ]
        ],
        [
          name: "roscoapp-dev-frontend2",
          type: "gce",
          region: "us-central1",
          account: "my-account-name"
        ],
        [
          name: "roscoapp-dev-frontend3",
          type: "gce",
          region: "us-central1",
          account: "my-account-name"
        ],
        [
          name: "roscoapp-dev-frontend4",
          type: "gce",
          region: "europe-west1",
          account: "my-account-name"
        ],
        [
          name: "roscoapp-dev-frontend5",
          type: "gce",
          region: "europe-west1",
          account: "my-account-name"
        ],
        [
          name: "roscoapp-dev-frontend6",
          type: "gce",
          region: "europe-west1",
          account: "my-account-name"
        ],
        [
          name: "testapp-dev-frontend1",
          type: "gce",
          region: "europe-west1",
          account: "my-account-name"
        ],
        [
          name: "testapp-dev-frontend2",
          type: "gce",
          region: "asia-east1",
          account: "my-account-name"
        ],
        [
          name: "testapp-dev-frontend3",
          type: "gce",
          region: "asia-east1",
          account: "my-account-name",
          serverGroups: [
            [
              name: "roscoapp-dev-v001",
              isDisabled: false,
              instances: [
                [
                  id: "roscoapp-dev-v001-vx09",
                  zone: "asia-east1-a",
                  health: [
                    state: "InService",
                    description: null
                  ]
                ]
              ]
            ],
            [
              name: "roscoapp3-dev-v002",
              isDisabled: false,
              instances: [
                [
                  id: "roscoapp3-dev-v002-gh07",
                  zone: "asia-east1-a",
                  health: [
                    state: "InService",
                    description: null
                  ]
                ]
              ]
            ]
          ]
        ],
        [
          name: "testapp-dev-frontend4",
          type: "gce",
          region: "asia-east1",
          account: "my-account-name"
        ]
      ] as Set

    when:
      loadBalancers = googleLoadBalancerProvider.getLoadBalancers("other-account-name")

    then:
      1 * resourceRetrieverMock.getNetworkLoadBalancerMap() >> networkLoadBalancerMap
      loadBalancers == [
        [
          name: "roscoapp-dev-frontend1",
          type: "gce",
          region: "us-central1",
          account: "other-account-name"
        ],
        [
          name: "roscoapp-dev-frontend2",
          type: "gce",
          region: "us-central1",
          account: "other-account-name"
        ],
        [
          name: "roscoapp-dev-frontend3",
          type: "gce",
          region: "us-central1",
          account: "other-account-name"
        ],
        [
          name: "roscoapp-dev-frontend4",
          type: "gce",
          region: "europe-west1",
          account: "other-account-name"
        ],
        [
          name: "roscoapp-dev-frontend5",
          type: "gce",
          region: "europe-west1",
          account: "other-account-name"
        ],
        [
          name: "roscoapp-dev-frontend7",
          type: "gce",
          region: "europe-west1",
          account: "other-account-name"
        ],
        [
          name: "testapp-dev-frontend1",
          type: "gce",
          region: "europe-west1",
          account: "other-account-name"
        ],
        [
          name: "testapp-dev-frontend2",
          type: "gce",
          region: "asia-east1",
          account: "other-account-name"
        ],
        [
          name: "testapp-dev-frontend3",
          type: "gce",
          region: "asia-east1",
          account: "other-account-name"
        ],
        [
          name: "testapp-dev-frontend4",
          type: "gce",
          region: "asia-east1",
          account: "other-account-name"
        ]
      ] as Set
  }

  void "can locate load balancers by account and cluster"() {
    setup:
      def resourceRetrieverMock = Mock(GoogleResourceRetriever)
      @Subject
      def googleLoadBalancerProvider = new GoogleLoadBalancerProvider()
      googleLoadBalancerProvider.googleResourceRetriever = resourceRetrieverMock

    when:
      def loadBalancers = googleLoadBalancerProvider.getLoadBalancers("my-account-name", "roscoapp-dev-frontend1")

    then:
      1 * resourceRetrieverMock.getNetworkLoadBalancerMap() >> networkLoadBalancerMap
      loadBalancers == [
        [
          name: "roscoapp-dev-frontend1",
          type: "gce",
          region: "us-central1",
          account: "my-account-name",
          serverGroups: [
            [
              name: "roscoapp-dev-v001",
              isDisabled: false,
              instances: [
                [
                  id: "roscoapp-dev-v001-vx09",
                  zone: "us-central1-a",
                  health: [
                    state: "InService",
                    description: null
                  ]
                ]
              ]
            ],
            [
              name: "roscoapp-dev-v002",
              isDisabled: false,
              instances: [
                [
                  id: "roscoapp-dev-v002-ab05",
                  zone: "us-central1-a",
                  health: [
                    state: "InService",
                    description: null
                  ]
                ]
              ]
            ]
          ]
        ]
      ] as Set

    when:
      loadBalancers = googleLoadBalancerProvider.getLoadBalancers("other-account-name", "roscoapp-dev-frontend1")

    then:
      1 * resourceRetrieverMock.getNetworkLoadBalancerMap() >> networkLoadBalancerMap
      loadBalancers == [
        [
          name: "roscoapp-dev-frontend1",
          type: "gce",
          region: "us-central1",
          account: "other-account-name"
        ]
      ] as Set
  }

  void "can locate load balancers by account, cluster and type"() {
    setup:
      def resourceRetrieverMock = Mock(GoogleResourceRetriever)
      @Subject
      def googleLoadBalancerProvider = new GoogleLoadBalancerProvider()
      googleLoadBalancerProvider.googleResourceRetriever = resourceRetrieverMock

    when:
      def loadBalancers = googleLoadBalancerProvider.getLoadBalancers("my-account-name", "roscoapp-dev-frontend2", "gce")

    then:
      1 * resourceRetrieverMock.getNetworkLoadBalancerMap() >> networkLoadBalancerMap
      loadBalancers == [
        [
          name: "roscoapp-dev-frontend2",
          type: "gce",
          region: "us-central1",
          account: "my-account-name"
        ]
      ] as Set

    when:
      loadBalancers = googleLoadBalancerProvider.getLoadBalancers("other-account-name", "roscoapp-dev-frontend2", "gce")

    then:
      1 * resourceRetrieverMock.getNetworkLoadBalancerMap() >> networkLoadBalancerMap
      loadBalancers == [
        [
          name: "roscoapp-dev-frontend2",
          type: "gce",
          region: "us-central1",
          account: "other-account-name"
        ]
      ] as Set
  }

  void "can locate load balancers by account, cluster, type and name"() {
    setup:
      def resourceRetrieverMock = Mock(GoogleResourceRetriever)
      @Subject
      def googleLoadBalancerProvider = new GoogleLoadBalancerProvider()
      googleLoadBalancerProvider.googleResourceRetriever = resourceRetrieverMock

    when:
      def loadBalancers = googleLoadBalancerProvider.getLoadBalancer("my-account-name", "roscoapp-dev-frontend2", "gce", "roscoapp-dev-frontend2")

    then:
      1 * resourceRetrieverMock.getNetworkLoadBalancerMap() >> networkLoadBalancerMap
      loadBalancers == [
        [
          name: "roscoapp-dev-frontend2",
          type: "gce",
          region: "us-central1",
          account: "my-account-name"
        ]
      ] as Set

    when:
      loadBalancers = googleLoadBalancerProvider.getLoadBalancer("other-account-name", "roscoapp-dev-frontend2", "gce", "roscoapp-dev-frontend2")

    then:
      1 * resourceRetrieverMock.getNetworkLoadBalancerMap() >> networkLoadBalancerMap
      loadBalancers == [
        [
          name: "roscoapp-dev-frontend2",
          type: "gce",
          region: "us-central1",
          account: "other-account-name"
        ]
      ] as Set
  }

  void "can locate load balancer by account, cluster, type, name and region"() {
    setup:
      def resourceRetrieverMock = Mock(GoogleResourceRetriever)
      @Subject
      def googleLoadBalancerProvider = new GoogleLoadBalancerProvider()
      googleLoadBalancerProvider.googleResourceRetriever = resourceRetrieverMock

    when:
      def loadBalancer = googleLoadBalancerProvider.getLoadBalancer("my-account-name", "roscoapp-dev-frontend3", "gce", "roscoapp-dev-frontend3", "us-central1")

    then:
      1 * resourceRetrieverMock.getNetworkLoadBalancerMap() >> networkLoadBalancerMap
      loadBalancer == new GoogleLoadBalancer(name: "roscoapp-dev-frontend3", account: "my-account-name", region: "us-central1")

    when:
      loadBalancer = googleLoadBalancerProvider.getLoadBalancer("other-account-name", "roscoapp-dev-frontend3", "gce", "roscoapp-dev-frontend3", "us-central1")

    then:
      1 * resourceRetrieverMock.getNetworkLoadBalancerMap() >> networkLoadBalancerMap
      loadBalancer == new GoogleLoadBalancer(name: "roscoapp-dev-frontend3", account: "other-account-name", region: "us-central1")

    when:
      loadBalancer = googleLoadBalancerProvider.getLoadBalancer("wrong-account-name", "roscoapp-dev-frontend3", "gce", "roscoapp-dev-frontend3", "us-central1")

    then:
      1 * resourceRetrieverMock.getNetworkLoadBalancerMap() >> networkLoadBalancerMap
      !loadBalancer
  }

}
