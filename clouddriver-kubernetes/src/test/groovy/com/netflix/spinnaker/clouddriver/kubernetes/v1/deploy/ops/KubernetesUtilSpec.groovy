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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.ops

import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesUtilSpec extends Specification {
  private static final String REGISTRY1 = 'gcr.io'
  private static final String REGISTRY2 = 'localhost:5000'
  private static final String REPOSITORY1 = 'ubuntu'
  private static final String REPOSITORY2 = 'library/nginx'
  private static final String TAG1 = '1.0'
  private static final String TAG2 = 'mytag'
  private static final String DIGEST1 = 'sha256:1b0a6c01c29ff911bf5c9857e29b8847a98f80b2b1b785622d78e317d25503dd'
  private static final String DIGEST2 = 'sha256:c98c24b677eff44860afea6f493bbaec5bb1c4cbb209c6fc2bbb47f66ff2ad31'

  @Unroll
  void "should correctly build an image description"() {
    when:
      def imageDescription = KubernetesUtil.buildImageDescription("$registry/$repository:$tag")

    then:
      imageDescription.registry == registry
      imageDescription.repository == repository
      imageDescription.tag == tag
      imageDescription.digest == null

    where:
      registry  | repository  | tag
      REGISTRY1 | REPOSITORY1 | TAG1
      REGISTRY1 | REPOSITORY1 | TAG2
      REGISTRY1 | REPOSITORY2 | TAG1
      REGISTRY1 | REPOSITORY2 | TAG2
      REGISTRY2 | REPOSITORY1 | TAG1
      REGISTRY2 | REPOSITORY1 | TAG2
      REGISTRY2 | REPOSITORY2 | TAG1
      REGISTRY2 | REPOSITORY2 | TAG2
  }

  @Unroll
  void "should correctly build an image description from a digest"() {
    when:
    def imageDescription = KubernetesUtil.buildImageDescription("$registry/$repository@$digest")

    then:
    imageDescription.registry == registry
    imageDescription.repository == repository
    imageDescription.tag == null
    imageDescription.digest == digest

    where:
    registry  | repository  | digest
    REGISTRY1 | REPOSITORY1 | DIGEST1
    REGISTRY1 | REPOSITORY1 | DIGEST2
    REGISTRY1 | REPOSITORY2 | DIGEST1
    REGISTRY1 | REPOSITORY2 | DIGEST2
    REGISTRY2 | REPOSITORY1 | DIGEST1
    REGISTRY2 | REPOSITORY1 | DIGEST2
    REGISTRY2 | REPOSITORY2 | DIGEST1
    REGISTRY2 | REPOSITORY2 | DIGEST2
  }

}
