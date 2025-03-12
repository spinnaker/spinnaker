/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.front50.utils

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.S3ObjectSummary

class S3TestHelper {
  static void setupBucket(AmazonS3 amazonS3, String bucketName) {
    try {
      def objectListing = amazonS3.listObjects(bucketName)

      while (true) {
        for (Iterator<?> iterator = objectListing.getObjectSummaries().iterator(); iterator.hasNext();) {
          def objectSummary = (S3ObjectSummary) iterator.next()
          amazonS3.deleteObject(bucketName, objectSummary.getKey())
        }

        if (objectListing.isTruncated()) {
          objectListing = amazonS3.listNextBatchOfObjects(objectListing)
        } else {
          break
        }
      }
      amazonS3.deleteBucket(bucketName)
    } catch (ignored) {}

    amazonS3.createBucket(bucketName)
  }

  /**
   * s3proxy: https://github.com/andrewgaul/s3proxy
   *
   * Configuration
   * -------------
   * s3proxy.authorization=none
   * s3proxy.endpoint=http://127.0.0.1:9999
   * jclouds.provider=filesystem
   * jclouds.identity=identity
   * jclouds.credential=credential
   * jclouds.filesystem.basedir=/tmp/s3proxy
   *
   * $ ./s3proxy --properties s3proxy.properties
   */
  static boolean s3ProxyUnavailable() {
    Socket s
    try {
      byte[] localhost = [127, 0, 0, 1]
      def resolvedAddress = new InetSocketAddress(InetAddress.getByAddress('localhost', localhost), 9999)
      s = new Socket()
      s.connect(resolvedAddress, 125)
      false
    } catch (Throwable t) {
      true
    } finally {
      try {
        s?.close()
      } catch (IOException ignored) {

      }
    }
  }
}
