/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.v3client;

import io.grpc.*;
import io.grpc.internal.SharedResourceHolder;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static io.grpc.internal.GrpcUtil.TIMER_SERVICE;

public class GrpcRetryInterceptor implements ClientInterceptor {

  private static long deadline;

  public GrpcRetryInterceptor(long deadline) {
    this.deadline = deadline;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
    callOptions = callOptions.withDeadlineAfter(deadline, TimeUnit.MILLISECONDS);

    if (method.getType() != MethodDescriptor.MethodType.UNARY) {
      return next.newCall(method, callOptions);
    }

    return new RetryingCall<ReqT, RespT>(method, callOptions, next, Context.current());
  }

  // retry interceptor modified from https://github.com/grpc/grpc-java/pull/1570
  private class RetryingCall<ReqT, RespT> extends ClientCall<ReqT, RespT> {
    private final MethodDescriptor<ReqT, RespT> method;
    private final CallOptions callOptions;
    private final Channel channel;
    private final Context context;
    private final ScheduledExecutorService scheduledExecutor;
    private Listener<RespT> responseListener;
    private Metadata requestHeaders;
    private ReqT requestMessage;
    private boolean compressionEnabled;
    private final Queue<AttemptListener> attemptListeners = new ConcurrentLinkedQueue<AttemptListener>();
    private volatile AttemptListener latestResponse;
    private volatile ScheduledFuture<?> retryTask;
    private int retries = 0;
    private long backOff = 150;
    private int maxRetries = 12;
    private final Set<Status.Code> retryOnStatuses = new HashSet<>(Arrays.asList(Status.Code.UNAVAILABLE, Status.Code.RESOURCE_EXHAUSTED, Status.Code.DEADLINE_EXCEEDED));

    RetryingCall(MethodDescriptor<ReqT, RespT> method,
                 CallOptions callOptions, Channel channel, Context context) {
      this.method = method;
      this.callOptions = callOptions;
      this.channel = channel;
      this.context = context;
      this.scheduledExecutor = SharedResourceHolder.get(TIMER_SERVICE);
    }

    @Override
    public void start(Listener<RespT> listener, Metadata headers) {
      responseListener = listener;
      requestHeaders = headers;
      ClientCall<ReqT, RespT> firstCall = channel.newCall(method, callOptions);
      AttemptListener attemptListener = new AttemptListener(firstCall);
      attemptListeners.add(attemptListener);
      firstCall.start(attemptListener, headers);
    }

    @Override
    public void request(int numMessages) {
      lastCall().request(numMessages);
    }

    @Override
    public void cancel(@Nullable String message, @Nullable Throwable cause) {
      for (AttemptListener attempt : attemptListeners) {
        attempt.call.cancel(message, cause);
      }
      if (retryTask != null) {
        retryTask.cancel(true);
      }
    }

    @Override
    public void halfClose() {
      lastCall().halfClose();
    }

    @Override
    public void sendMessage(ReqT message) {
      requestMessage = message;
      lastCall().sendMessage(message);
    }

    @Override
    public boolean isReady() {
      return lastCall().isReady();
    }

    @Override
    public void setMessageCompression(boolean enabled) {
      compressionEnabled = enabled;
      lastCall().setMessageCompression(enabled);
    }

    private void maybeRetry(AttemptListener attempt) {
      Status status = attempt.responseStatus;
      if (status.isOk() || retries >= maxRetries) {
        useResponse(attempt);
        return;
      }

      Status.Code code = status.getCode();
      if (!retryOnStatuses.contains(code)) {
        AttemptListener latest = latestResponse;
        if (latest != null) {
          useResponse(latest);
        } else {
          useResponse(attempt);
        }
        return;
      }
      latestResponse = attempt;

      retries++;
      long timeout = (long) Math.pow(2, retries) * backOff;

      retryTask = scheduledExecutor.schedule(context.wrap(new Runnable() {
        @Override
        public void run() {
          // need to reset the deadline here
          ClientCall<ReqT, RespT> nextCall = channel.newCall(method, callOptions.withDeadlineAfter(deadline, TimeUnit.MILLISECONDS));
          AttemptListener nextAttemptListener = new AttemptListener(nextCall);
          attemptListeners.add(nextAttemptListener);
          nextCall.start(nextAttemptListener, requestHeaders);
          nextCall.setMessageCompression(compressionEnabled);
          nextCall.sendMessage(requestMessage);
          nextCall.request(1);
          nextCall.halfClose();
        }
      }), timeout, TimeUnit.MILLISECONDS);
    }

    private void useResponse(AttemptListener attempt) {
      responseListener.onHeaders(attempt.responseHeaders);
      if (attempt.responseMessage != null) {
        responseListener.onMessage(attempt.responseMessage);
      }
      responseListener.onClose(attempt.responseStatus, attempt.responseTrailers);
    }

    private ClientCall<ReqT, RespT> lastCall() {
      return attemptListeners.peek().call;
    }

    private class AttemptListener extends ClientCall.Listener<RespT> {
      final ClientCall<ReqT, RespT> call;
      Metadata responseHeaders;
      RespT responseMessage;
      Status responseStatus;
      Metadata responseTrailers;

      AttemptListener(ClientCall<ReqT, RespT> call) {
        this.call = call;
      }

      @Override
      public void onHeaders(Metadata headers) {
        responseHeaders = headers;
      }

      @Override
      public void onMessage(RespT message) {
        responseMessage = message;
      }

      @Override
      public void onClose(Status status, Metadata trailers) {
        responseStatus = status;
        responseTrailers = trailers;
        maybeRetry(this);
      }

      @Override
      public void onReady() {
        responseListener.onReady();
      }
    }
  }
}
