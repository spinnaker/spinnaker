/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.security

trait TestDefaults {
  static final REGION_TO_ZONES = [
    'us-east1': ['us-east1-b', 'us-east1-c', 'us-east1-d'],
    'us-central1': ['us-central1-a', 'us-central1-b', 'us-central1-c', 'us-central1-f'],
    'us-west1': ['us-west1-a', 'us-west1-b', 'us-west1-c'],
    'europe-west1': ['europe-west1-b', 'europe-west1-c', 'europe-west1-d'],
    'asia-east1': ['asia-east1-a', 'asia-east1-b', 'asia-east1-c'],
    'asia-northeast1': ['asia-northeast1-a', 'asia-northeast1-b', 'asia-northeast1-c'],
    'asia-southeast1': ['asia-southeast1-a', 'asia-southeast1-b'],
    'us-east4': ['us-east4-a', 'us-east4-b', 'us-east4-c']
  ]

  static final LOCATION_TO_INSTANCE_TYPES = [
    'asia-east1': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-32',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-32',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-32',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 32
    ],
    'asia-east1-a': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-32',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-32',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-32',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 32
    ],
    'asia-east1-b': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-32',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-32',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-32',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 32
    ],
    'asia-east1-c': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-32',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-32',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-32',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 32
    ],
    'europe-west1': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 16
    ],
    'europe-west1-b': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 16
    ],
    'europe-west1-c': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-32',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-32',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-32',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 32
    ],
    'europe-west1-d': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-32',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-32',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-32',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 32
    ],
    'us-central1': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-32',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-32',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-32',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 32
    ],
    'us-central1-a': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 16
    ],
    'us-central1-b': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-32',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-32',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-32',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 32
    ],
    'us-central1-c': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-32',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-32',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-32',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 32
    ],
    'us-central1-f': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-32',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-32',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-32',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 32
    ],
    'us-east1': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-32',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-32',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-32',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 32
    ],
    'us-east1-a': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-32',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-32',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-32',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 32
    ],
    'us-east1-b': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-32',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-32',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-32',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 32
    ],
    'us-east1-c': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-32',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-32',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-32',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 32
    ],
    'us-east1-d': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-32',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-32',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-32',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 32
    ],
    'us-west1': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-32',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-32',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-32',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 32
    ],
    'us-west1-a': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-32',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-32',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-32',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 32
    ],
    'us-west1-b': [
      'instanceTypes': [
        'f1-micro',
        'g1-small',
        'n1-highcpu-16',
        'n1-highcpu-2',
        'n1-highcpu-32',
        'n1-highcpu-4',
        'n1-highcpu-8',
        'n1-highmem-16',
        'n1-highmem-2',
        'n1-highmem-32',
        'n1-highmem-4',
        'n1-highmem-8',
        'n1-standard-1',
        'n1-standard-16',
        'n1-standard-2',
        'n1-standard-32',
        'n1-standard-4',
        'n1-standard-8'
      ],
      'vCpuMax': 32
    ]
  ]

  static final INSTANCE_TYPES_WITH_16 = [
    [ guestCpus: 1, name: 'f1-micro' ],
    [ guestCpus: 1, name: 'g1-small' ],
    [ guestCpus: 16, name: 'n1-highcpu-16' ],
    [ guestCpus: 2, name: 'n1-highcpu-2' ],
    [ guestCpus: 4, name: 'n1-highcpu-4' ],
    [ guestCpus: 8, name: 'n1-highcpu-8' ],
    [ guestCpus: 16, name: 'n1-highmem-16' ],
    [ guestCpus: 2, name: 'n1-highmem-2' ],
    [ guestCpus: 4, name: 'n1-highmem-4' ],
    [ guestCpus: 8, name: 'n1-highmem-8' ],
    [ guestCpus: 1, name: 'n1-standard-1' ],
    [ guestCpus: 16, name: 'n1-standard-16' ],
    [ guestCpus: 2, name: 'n1-standard-2' ],
    [ guestCpus: 4, name: 'n1-standard-4' ],
    [ guestCpus: 8, name: 'n1-standard-8' ]
  ]

  static final INSTANCE_TYPES_WITH_32 = [
    [ guestCpus: 1, name: 'f1-micro' ],
    [ guestCpus: 1, name: 'g1-small' ],
    [ guestCpus: 16, name: 'n1-highcpu-16' ],
    [ guestCpus: 2, name: 'n1-highcpu-2' ],
    [ guestCpus: 32, name: 'n1-highcpu-32' ],
    [ guestCpus: 4, name: 'n1-highcpu-4' ],
    [ guestCpus: 8, name: 'n1-highcpu-8' ],
    [ guestCpus: 16, name: 'n1-highmem-16' ],
    [ guestCpus: 2, name: 'n1-highmem-2' ],
    [ guestCpus: 32, name: 'n1-highmem-32' ],
    [ guestCpus: 4, name: 'n1-highmem-4' ],
    [ guestCpus: 8, name: 'n1-highmem-8' ],
    [ guestCpus: 1, name: 'n1-standard-1' ],
    [ guestCpus: 16, name: 'n1-standard-16' ],
    [ guestCpus: 2, name: 'n1-standard-2' ],
    [ guestCpus: 32, name: 'n1-standard-32' ],
    [ guestCpus: 4, name: 'n1-standard-4' ],
    [ guestCpus: 8, name: 'n1-standard-8' ]
  ]

  static final INSTANCE_TYPES_WITH_64 = [
    [ guestCpus: 1, name: 'f1-micro' ],
    [ guestCpus: 1, name: 'g1-small' ],
    [ guestCpus: 16, name: 'n1-highcpu-16' ],
    [ guestCpus: 2, name: 'n1-highcpu-2' ],
    [ guestCpus: 32, name: 'n1-highcpu-32' ],
    [ guestCpus: 64, name: 'n1-highcpu-64' ],
    [ guestCpus: 4, name: 'n1-highcpu-4' ],
    [ guestCpus: 8, name: 'n1-highcpu-8' ],
    [ guestCpus: 16, name: 'n1-highmem-16' ],
    [ guestCpus: 2, name: 'n1-highmem-2' ],
    [ guestCpus: 32, name: 'n1-highmem-32' ],
    [ guestCpus: 64, name: 'n1-highmem-64' ],
    [ guestCpus: 4, name: 'n1-highmem-4' ],
    [ guestCpus: 8, name: 'n1-highmem-8' ],
    [ guestCpus: 1, name: 'n1-standard-1' ],
    [ guestCpus: 16, name: 'n1-standard-16' ],
    [ guestCpus: 2, name: 'n1-standard-2' ],
    [ guestCpus: 32, name: 'n1-standard-32' ],
    [ guestCpus: 64, name: 'n1-standard-64' ],
    [ guestCpus: 4, name: 'n1-standard-4' ],
    [ guestCpus: 8, name: 'n1-standard-8' ]
  ]

  static final INSTANCE_TYPE_LIST = [
    items: [
      'zones/us-central1-a': [
        machineTypes: INSTANCE_TYPES_WITH_16
      ],
      'zones/us-central1-b': [
        machineTypes: INSTANCE_TYPES_WITH_32
      ],
      'zones/us-central1-c': [
        machineTypes: INSTANCE_TYPES_WITH_32
      ],
      'zones/us-central1-f': [
        machineTypes: INSTANCE_TYPES_WITH_32
      ],
      'zones/europe-west1-b': [
        machineTypes: INSTANCE_TYPES_WITH_16
      ],
      'zones/europe-west1-c': [
        machineTypes: INSTANCE_TYPES_WITH_32
      ],
      'zones/europe-west1-d': [
        machineTypes: INSTANCE_TYPES_WITH_32
      ],
      'zones/us-west1-a': [
        machineTypes: INSTANCE_TYPES_WITH_32
      ],
      'zones/us-west1-b': [
        machineTypes: INSTANCE_TYPES_WITH_32
      ],
      'zones/asia-east1-a': [
        machineTypes: INSTANCE_TYPES_WITH_32
      ],
      'zones/asia-east1-b': [
        machineTypes: INSTANCE_TYPES_WITH_32
      ],
      'zones/asia-east1-c': [
        machineTypes: INSTANCE_TYPES_WITH_32
      ],
      'zones/us-east1-a': [
        machineTypes: INSTANCE_TYPES_WITH_32
      ],
      'zones/us-east1-b': [
        machineTypes: INSTANCE_TYPES_WITH_32
      ],
      'zones/us-east1-c': [
        machineTypes: INSTANCE_TYPES_WITH_32
      ],
      'zones/us-east1-d': [
        machineTypes: INSTANCE_TYPES_WITH_32
      ]
    ]
  ]

  static final ZONE_ITEMS_LIST = [
    [
      name: "asia-east1-b",
      availableCpuPlatforms: ["Intel Ivy Bridge",
                              "Intel Skylake"]
    ],
    [
      name: "asia-east1-c",
      availableCpuPlatforms: ["Intel Ivy Bridge"]
    ],
    [
      name: "asia-east1-a",
      availableCpuPlatforms: ["Intel Broadwell",
                              "Intel Ivy Bridge",
                              "Intel Skylake"]
    ],
    [
      name: "asia-northeast1-b",
      availableCpuPlatforms: ["Intel Broadwell"]
    ],
    [
      name: "asia-northeast1-c",
      availableCpuPlatforms: ["Intel Broadwell"]
    ],
    [
      name: "asia-northeast1-a",
      availableCpuPlatforms: ["Intel Broadwell"]
    ],
    [
      name: "asia-southeast1-a",
      availableCpuPlatforms: ["Intel Broadwell"]
    ],
    [
      name: "asia-southeast1-b",
      availableCpuPlatforms: ["Intel Broadwell"]
    ],
    [
      name: "europe-west1-d",
      availableCpuPlatforms: ["Intel Haswell",
                              "Intel Skylake"]
    ],
    [
      name: "europe-west1-b",
      availableCpuPlatforms: ["Intel Broadwell",
                              "Intel Sandy Bridge",
                              "Intel Skylake"]
    ],
    [
      name: "europe-west1-c",
      availableCpuPlatforms: ["Intel Broadwell",
                              "Intel Ivy Bridge"]
    ],
    [
      name: "europe-west2-b"
    ],
    [
      name: "europe-west2-a"
    ],
    [
      name: "europe-west2-c"
    ],
    [
      name: "us-central1-a",
      availableCpuPlatforms: ["Intel Broadwell",
                              "Intel Sandy Bridge"]
    ],
    [
      name: "us-central1-b",
      availableCpuPlatforms: ["Intel Broadwell",
                              "Intel Haswell",
                              "Intel Skylake"]
    ],
    [
      name: "us-central1-c",
      availableCpuPlatforms: ["Intel Broadwell",
                              "Intel Haswell",
                              "Intel Skylake"]
    ],
    [
      name: "us-central1-f",
      availableCpuPlatforms: ["Intel Broadwell",
                              "Intel Ivy Bridge"]
    ],
    [
      name: "us-east1-b",
      availableCpuPlatforms: ["Intel Broadwell",
                              "Intel Haswell"]
    ],
    [
      name: "us-east1-a"
    ],
    [
      name: "us-east1-c",
      availableCpuPlatforms: ["Intel Broadwell",
                              "Intel Haswell"]
    ],
    [
      name: "us-east1-d",
      availableCpuPlatforms: ["Intel Broadwell",
                              "Intel Haswell"]
    ],
    [
      name: "us-east4-b",
      availableCpuPlatforms: ["Intel Broadwell"]
    ],
    [
      name: "us-east4-c",
      availableCpuPlatforms: ["Intel Broadwell"]
    ],
    [
      name: "us-east4-a",
      availableCpuPlatforms: ["Intel Broadwell"]
    ],
    [
      name: "us-west1-b",
      availableCpuPlatforms: ["Intel Broadwell",
                              "Intel Skylake"]
    ],
    [
      name: "us-west1-c",
      availableCpuPlatforms: ["Intel Broadwell"]
    ],
    [
      name: "us-west1-a",
      availableCpuPlatforms: ["Intel Broadwell",
                              "Intel Skylake"]
    ]
  ]
}
