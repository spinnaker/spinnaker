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

import java.io.EOFException;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

/**
 * One archive's transfers over a borrowed, policy-configured sync client.
 *
 * <p>The 16 MiB strict threshold, 5 MiB minimum part size, 10,000-part calculation and 10 workers
 * retain SDK v1 TransferManager defaults (1.12.261 and 1.12.781). Only this invocation's executor,
 * streams and multipart IDs are owned here.
 *
 * <p>Workers never wait for jobs submitted to their own pool. The calling thread processes
 * completions and schedules the next protocol step. On failure it cancels work and drains the
 * executor before serially aborting its unfinished uploads. Those aborts cannot overlap transfer
 * calls, so all S3 calls stay within the same 10-call budget. The shared client is NEVER closed.
 *
 * <p>Drain/abort before returning (and thus before temporary-file deletion) improves ownership over
 * legacy shutdownNow(false). Interruption is not a force-kill: an already-running SDK call must
 * unwind under the existing transport/timeout policy. No new deadline, retry, transport or
 * credential policy is installed here.
 */
class S3ArchiveUpload implements AutoCloseable {
  static final int WORKERS = 10;
  static final long MULTIPART_THRESHOLD = 16L * 1024 * 1024;
  static final long MINIMUM_PART_SIZE = 5L * 1024 * 1024;
  static final int MAXIMUM_PARTS = 10_000;

  @FunctionalInterface
  interface MetadataProvider {
    void provideObjectMetadata(File file, PutObjectRequest.Builder metadata);
  }

  @FunctionalInterface
  interface TaggingProvider {
    String provideObjectTags(File file);
  }

  @FunctionalInterface
  interface Upload {
    void waitForCompletion() throws IOException, InterruptedException;
  }

  private final S3Client client;
  private final ExecutorService workers = Executors.newFixedThreadPool(WORKERS);
  private final ExecutorCompletionService<Runnable> completions =
      new ExecutorCompletionService<>(workers);
  private final AtomicReference<Throwable> failure = new AtomicReference<>();
  private final Set<Future<Runnable>> inFlight = new HashSet<>();
  private final List<Multipart> multiparts = new ArrayList<>();
  private boolean used;
  private boolean closed;

  S3ArchiveUpload(S3Client client) {
    this.client = client;
  }

  Upload uploadFileList(
      String bucket,
      String prefix,
      File directory,
      List<File> files,
      MetadataProvider metadataProvider,
      TaggingProvider taggingProvider)
      throws IOException {
    if (used || closed) {
      throw new IllegalStateException("Archive uploader is single-use");
    }
    used = true;
    List<FileUpload> uploads = new ArrayList<>();
    for (File file : files) {
      Path path = file.toPath();
      String relative = directory.toPath().relativize(path).toString().replace('\\', '/');
      String key = prefix.isEmpty() ? relative : prefix + "/" + relative;
      long length = Files.size(path);
      PutObjectRequest.Builder request =
          PutObjectRequest.builder().bucket(bucket).key(key).contentLength(length);
      metadataProvider.provideObjectMetadata(file, request);
      if (taggingProvider != null) {
        request.tagging(taggingProvider.provideObjectTags(file));
      }
      uploads.add(new FileUpload(path, length, request.build()));
    }
    return () -> transfer(uploads);
  }

  static long partSize(long length) {
    // Ceiling division without overflow, including for very large file lengths.
    long minimumForPartLimit = length / MAXIMUM_PARTS + (length % MAXIMUM_PARTS == 0 ? 0 : 1);
    return Math.max(MINIMUM_PART_SIZE, minimumForPartLimit);
  }

  private void transfer(List<FileUpload> uploads) throws IOException, InterruptedException {
    if (closed) {
      throw new IllegalStateException("Archive uploader is closed");
    }
    Deque<Callable<Runnable>> pending = new ArrayDeque<>();
    try {
      for (FileUpload upload : uploads) {
        if (upload.length() > MULTIPART_THRESHOLD) {
          Multipart state = new Multipart(upload);
          multiparts.add(state);
          pending.add(() -> initiate(state, pending));
        } else {
          pending.add(
              () -> {
                try (RangeBody body = new RangeBody(upload.path(), 0, upload.length())) {
                  client.putObject(upload.request(), body.requestBody());
                }
                return () -> {};
              });
        }
      }
      while ((!pending.isEmpty() || !inFlight.isEmpty()) && failure.get() == null) {
        if (Thread.interrupted()) {
          throw new InterruptedException("Archive upload interrupted");
        }
        while (!pending.isEmpty() && inFlight.size() < WORKERS && failure.get() == null) {
          Callable<Runnable> job = pending.removeFirst();
          inFlight.add(
              completions.submit(
                  () -> {
                    if (failure.get() != null) {
                      throw new CancellationException("Archive upload stopped");
                    }
                    try {
                      return job.call();
                    } catch (Throwable original) {
                      fail(original);
                      throw new ExecutionException(original);
                    }
                  }));
        }
        if (failure.get() == null && !inFlight.isEmpty()) {
          Future<Runnable> done = completions.take();
          inFlight.remove(done);
          try {
            Runnable next = done.get();
            if (failure.get() == null) {
              next.run();
            }
          } catch (ExecutionException original) {
            fail(original.getCause());
          }
        }
      }
    } catch (Throwable original) {
      if (original instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      fail(original);
    } finally {
      finish();
    }
    throwFailure();
  }

  private Runnable initiate(Multipart state, Deque<Callable<Runnable>> pending) {
    PutObjectRequest request = state.file.request();
    String id =
        client
            .createMultipartUpload(
                CreateMultipartUploadRequest.builder()
                    .bucket(request.bucket())
                    .key(request.key())
                    .contentType(request.contentType())
                    .cacheControl(request.cacheControl())
                    .expires(request.expires())
                    .tagging(request.tagging())
                    .build())
            .uploadId();
    if (id == null || id.isBlank()) {
      throw SdkClientException.create("S3 did not return a multipart upload ID");
    }
    // Publish even when cancellation raced with create returning. Abort waits for this worker.
    state.id = id;
    return () -> {
      long size = partSize(state.file.length());
      int count = Math.toIntExact(1 + (state.file.length() - 1) / size);
      state.parts = new CompletedPart[count];
      state.remaining = count;
      for (int index = 0; index < count; index++) {
        int partIndex = index;
        long offset = size * index;
        long length = Math.min(size, state.file.length() - offset);
        pending.add(
            () -> {
              String etag;
              try (RangeBody body = new RangeBody(state.file.path(), offset, length)) {
                etag =
                    client
                        .uploadPart(
                            UploadPartRequest.builder()
                                .bucket(request.bucket())
                                .key(request.key())
                                .uploadId(id)
                                .partNumber(partIndex + 1)
                                .contentLength(length)
                                .build(),
                            body.requestBody())
                        .eTag();
              }
              if (etag == null || etag.isBlank()) {
                throw SdkClientException.create("S3 did not return a part ETag");
              }
              String completedEtag = etag;
              return () -> {
                state.parts[partIndex] =
                    CompletedPart.builder().partNumber(partIndex + 1).eTag(completedEtag).build();
                if (--state.remaining == 0) {
                  pending.add(
                      () -> {
                        client.completeMultipartUpload(
                            CompleteMultipartUploadRequest.builder()
                                .bucket(request.bucket())
                                .key(request.key())
                                .uploadId(id)
                                .multipartUpload(
                                    CompletedMultipartUpload.builder().parts(state.parts).build())
                                .build());
                        // Set before this job returns, even if cancellation raced with completion.
                        state.completed = true;
                        return () -> {};
                      });
                }
              };
            });
      }
    };
  }

  private void fail(Throwable original) {
    if (original instanceof ExecutionException && original.getCause() != null) {
      original = original.getCause();
    }
    if (original instanceof UncheckedIOException) {
      original = original.getCause();
    }
    if (!failure.compareAndSet(null, original)) {
      Throwable first = failure.get();
      if (first != original) {
        first.addSuppressed(original);
      }
    }
  }

  private void finish() {
    if (closed) {
      return;
    }
    closed = true;
    boolean interrupted = Thread.interrupted();
    if (interrupted) {
      fail(new InterruptedException("Archive upload interrupted"));
    }
    for (Future<Runnable> job : inFlight) {
      job.cancel(true);
    }
    workers.shutdownNow();
    // Future.cancel/isDone does NOT mean that the underlying SDK call has unwound.
    while (!workers.isTerminated()) {
      try {
        workers.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
      } catch (InterruptedException original) {
        interrupted = true;
        fail(original);
      }
    }
    // No transfer call can still create/complete an ID or hold a file stream at this point.
    for (Multipart state : multiparts) {
      if (state.id != null && !state.completed) {
        try {
          client.abortMultipartUpload(
              AbortMultipartUploadRequest.builder()
                  .bucket(state.file.request().bucket())
                  .key(state.file.request().key())
                  .uploadId(state.id)
                  .build());
        } catch (Throwable cleanup) {
          fail(cleanup);
        } finally {
          if (Thread.interrupted()) {
            interrupted = true;
            fail(new InterruptedException("Multipart cleanup interrupted"));
          }
        }
      }
    }
    if (interrupted || failure.get() instanceof InterruptedException) {
      Thread.currentThread().interrupt();
    }
  }

  private void throwFailure() throws IOException, InterruptedException {
    Throwable original = failure.get();
    if (original instanceof IOException) {
      throw (IOException) original;
    }
    if (original instanceof InterruptedException) {
      throw (InterruptedException) original;
    }
    if (original instanceof RuntimeException) {
      throw (RuntimeException) original;
    }
    if (original instanceof Error) {
      throw (Error) original;
    }
    if (original != null) {
      throw new IllegalStateException("Archive upload failed", original);
    }
  }

  @Override
  public void close() throws IOException, InterruptedException {
    if (!closed) {
      finish();
      throwFailure();
    }
  }

  private record FileUpload(Path path, long length, PutObjectRequest request) {}

  private static final class Multipart {
    final FileUpload file;
    volatile String id;
    volatile boolean completed;
    CompletedPart[] parts;
    int remaining;

    Multipart(FileUpload file) {
      this.file = file;
    }
  }

  /** One job's retryable bounded file range; closes even if an SDK/mock neglects its stream. */
  private static final class RangeBody implements ContentStreamProvider, AutoCloseable {
    private final Path path;
    private final long offset;
    private final long length;
    private InputStream current;
    private boolean closed;

    RangeBody(Path path, long offset, long length) {
      this.path = path;
      this.offset = offset;
      this.length = length;
    }

    RequestBody requestBody() {
      return RequestBody.fromContentProvider(this, length, "application/octet-stream");
    }

    @Override
    public synchronized InputStream newStream() {
      if (closed) {
        throw new IllegalStateException("Upload body is closed");
      }
      try {
        if (current != null) {
          current.close();
        }
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
        try {
          channel.position(offset);
          current =
              new FilterInputStream(Channels.newInputStream(channel)) {
                private long remaining = length;

                @Override
                public int read() throws IOException {
                  if (remaining == 0) {
                    return -1;
                  }
                  int value = in.read();
                  if (value < 0) {
                    throw new EOFException("Upload file ended before its declared length");
                  }
                  remaining--;
                  return value;
                }

                @Override
                public int read(byte[] bytes, int start, int count) throws IOException {
                  Objects.checkFromIndexSize(start, count, bytes.length);
                  if (count == 0) {
                    return 0;
                  }
                  if (remaining == 0) {
                    return -1;
                  }
                  int read = in.read(bytes, start, (int) Math.min(count, remaining));
                  if (read < 0) {
                    throw new EOFException("Upload file ended before its declared length");
                  }
                  remaining -= read;
                  return read;
                }

                @Override
                public long skip(long count) throws IOException {
                  long skipped = in.skip(Math.min(Math.max(0, count), remaining));
                  remaining -= skipped;
                  return skipped;
                }

                @Override
                public int available() throws IOException {
                  return (int) Math.min(in.available(), remaining);
                }
              };
        } catch (Throwable original) {
          try {
            channel.close();
          } catch (Throwable cleanup) {
            if (original != cleanup) {
              original.addSuppressed(cleanup);
            }
          }
          throw original;
        }
        return current;
      } catch (IOException original) {
        throw new UncheckedIOException(original);
      }
    }

    @Override
    public synchronized void close() throws IOException {
      closed = true;
      if (current != null) {
        current.close();
      }
    }
  }
}
