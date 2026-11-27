/*
 * Copyright 2026 Wise, PLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.clouddriver.aws.deploy.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

class S3ArchiveUploadTest {
  @TempDir Path directory;

  @Test
  void uploadsLargeFilesInOrderedPartsWithoutClosingSharedClient() throws Exception {
    Path file = directory.resolve("large.bin");
    try (RandomAccessFile content = new RandomAccessFile(file.toFile(), "rw")) {
      content.setLength(S3ArchiveUpload.MULTIPART_THRESHOLD + 1);
    }
    S3Client client = mock(S3Client.class);
    when(client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
        .thenReturn(CreateMultipartUploadResponse.builder().uploadId("upload").build());
    when(client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
        .thenAnswer(
            invocation -> {
              UploadPartRequest request = invocation.getArgument(0);
              return UploadPartResponse.builder().eTag("etag-" + request.partNumber()).build();
            });
    when(client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
        .thenReturn(CompleteMultipartUploadResponse.builder().build());

    try (S3ArchiveUpload uploader = new S3ArchiveUpload(client)) {
      uploader
          .uploadFileList(
              "bucket", "prefix", directory.toFile(), List.of(file.toFile()), (f, b) -> {}, null)
          .waitForCompletion();
    }

    ArgumentCaptor<CompleteMultipartUploadRequest> request =
        ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
    verify(client).completeMultipartUpload(request.capture());
    assertEquals("prefix/large.bin", request.getValue().key());
    assertEquals(
        List.of(1, 2, 3, 4),
        request.getValue().multipartUpload().parts().stream()
            .map(CompletedPart::partNumber)
            .toList());
    verify(client, never()).close();
  }
}
