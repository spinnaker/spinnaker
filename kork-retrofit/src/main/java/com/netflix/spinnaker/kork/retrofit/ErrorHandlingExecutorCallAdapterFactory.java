/*
 * Copyright 2023 OpsMx, Inc.
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

package com.netflix.spinnaker.kork.retrofit;

import com.netflix.spinnaker.kork.retrofit.exceptions.RetrofitException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.concurrent.Executor;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import okhttp3.Request;
import okio.Timeout;
import org.springframework.http.HttpStatus;
import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

/**
 * {@link retrofit.RetrofitError} and {@link retrofit.ErrorHandler} are no longer present in
 * retrofit2. So this class helps to achieve similar logic as retrofit and handle exceptions
 * globally in retrofit2. This can be achieved by setting this class as CallAdapterFactory at the
 * time of {@link Retrofit} client creation.
 */
public class ErrorHandlingExecutorCallAdapterFactory extends CallAdapter.Factory {

  /**
   * Which need to be set only if clients uses async call i.e enqueue method. Clients which make use
   * of sync call i.e execute method only, no need to set this.
   */
  private final @Nullable Executor callbackExecutor;

  ErrorHandlingExecutorCallAdapterFactory() {
    this.callbackExecutor = null;
  }

  ErrorHandlingExecutorCallAdapterFactory(Executor callbackExecutor) {
    this.callbackExecutor = callbackExecutor;
  }

  public static ErrorHandlingExecutorCallAdapterFactory getInstance(Executor callbackExecutor) {
    return new ErrorHandlingExecutorCallAdapterFactory(callbackExecutor);
  }

  public static ErrorHandlingExecutorCallAdapterFactory getInstance() {
    return new ErrorHandlingExecutorCallAdapterFactory();
  }

  /**
   * Returns a call adapter for interface methods that return {@code returnType}, or null if
   * returnType is not instance of {@link Call} and {@link ParameterizedType}.
   */
  @Nullable
  @Override
  public CallAdapter<?, ?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {

    /**
     * Expected the raw class type from returnType to be {@link Call} class otherwise return null as
     * it cannot be handled by this factory
     */
    if (getRawType(returnType) != Call.class) {
      return null;
    }

    if (!(returnType instanceof ParameterizedType)) {
      return null;
    }

    /**
     * The value type that this adapter uses when converting the HTTP response body to a Java
     * object. For example, the response type for {@code Call<Repo>} is {@code Repo}. This type is
     * used to prepare the {@code call} passed to {@code #adapt}.
     */
    final Type responseType = getParameterUpperBound(0, (ParameterizedType) returnType);

    return new CallAdapter<Object, Call<?>>() {
      @Override
      public Type responseType() {
        return responseType;
      }

      @Override
      public Call<Object> adapt(Call<Object> call) {
        return new ExecutorCallbackCall<>(callbackExecutor, call, retrofit);
      }
    };
  }

  /**
   * An invocation of a Retrofit method that sends a request to a webserver and returns a response.
   * Each call yields its own HTTP request and response pair.
   *
   * <p>Calls may be executed synchronously with {@link #execute}, or asynchronously with {@link
   * #enqueue}. In either case Spinnaker(Http|Network|Server)Exception will be thrown if the
   * response is not successful or an unexpected error occurs creating the request or decoding the
   * response.
   *
   * @param <T> Successful response body type.
   */
  static final class ExecutorCallbackCall<T> implements Call<T> {

    /** The executor used for Callback methods on a Call. */
    private final Executor callbackExecutor;

    /** Original delegate which has request to execute */
    private final Call<T> delegate;

    /**
     * Client used while the service creation, which has convertor logic to be used to parse the
     * response
     */
    private final Retrofit retrofit;

    ExecutorCallbackCall(Executor callbackExecutor, Call<T> delegate, Retrofit retrofit) {
      this.callbackExecutor = callbackExecutor;
      this.delegate = delegate;
      this.retrofit = retrofit;
    }

    /**
     * Synchronously send the request and return its response.
     *
     * @throws SpinnakerServerException (and subclasses) if an error occurs while creating the
     *     request or decoding the response
     */
    @Override
    public Response<T> execute() {
      Response<T> syncResp;
      try {
        syncResp = delegate.execute();
        if (syncResp.isSuccessful()) {
          return syncResp;
        }
      } catch (IOException e) {
        throw new SpinnakerNetworkException(e);
      } catch (Exception e) {
        throw new SpinnakerServerException(e);
      }
      throw createSpinnakerHttpException(syncResp);
    }

    @Nonnull
    private SpinnakerHttpException createSpinnakerHttpException(Response<T> response) {
      SpinnakerHttpException retval =
          new SpinnakerHttpException(RetrofitException.httpError(response, retrofit));
      if ((response.code() == HttpStatus.NOT_FOUND.value())
          || (response.code() == HttpStatus.BAD_REQUEST.value())) {
        retval.setRetryable(false);
      }
      return retval;
    }

    /**
     * Asynchronously send the request and notify {@code callback} of its response or
     * Spinnaker(Http|Network|Server)Exception if an error occurred talking to the server, creating
     * the request, or processing the response.
     */
    @Override
    public void enqueue(Callback<T> callback) {
      Objects.requireNonNull(callback, "Callback can't be null");
      delegate.enqueue(new SpinnakerCustomExecutorCallback<>(callbackExecutor, callback, this));
    }

    @Override
    public boolean isExecuted() {
      return delegate.isExecuted();
    }

    @Override
    public void cancel() {
      delegate.cancel();
    }

    @Override
    public boolean isCanceled() {
      return delegate.isCanceled();
    }

    @Override
    public Call<T> clone() {
      return new ExecutorCallbackCall<>(callbackExecutor, delegate.clone(), retrofit);
    }

    @Override
    public Request request() {
      return delegate.request();
    }

    @Override
    public Timeout timeout() {
      return delegate.timeout();
    }
  }

  /**
   * Handles exceptions globally for async calls and notify {@code callback} with response or
   * Spinnaker(Http|Network|Server)Exception if an error occurred talking to the server, creating
   * the request, or processing the response.
   */
  static class SpinnakerCustomExecutorCallback<T> implements Callback<T> {
    private final Executor callbackExecutor;
    private final Callback<T> callback;
    private final ExecutorCallbackCall<T> executorCallbackCall;

    public SpinnakerCustomExecutorCallback(
        Executor callbackExecutor,
        Callback<T> callback,
        ExecutorCallbackCall<T> executorCallbackCall) {
      this.callbackExecutor = callbackExecutor;
      this.callback = callback;
      this.executorCallbackCall = executorCallbackCall;
    }

    @Override
    public void onResponse(final Call<T> call, final Response<T> response) {
      if (response.isSuccessful()) {
        callbackExecutor.execute(
            new Runnable() {
              @Override
              public void run() {
                callback.onResponse(executorCallbackCall, response);
              }
            });
      } else {
        callbackExecutor.execute(
            new Runnable() {
              @Override
              public void run() {
                callback.onFailure(
                    executorCallbackCall,
                    executorCallbackCall.createSpinnakerHttpException(response));
              }
            });
      }
    }

    @Override
    public void onFailure(Call<T> call, final Throwable t) {

      SpinnakerServerException exception;
      if (t instanceof IOException) {
        exception = new SpinnakerNetworkException(t);
      } else if (t instanceof SpinnakerHttpException) {
        exception = (SpinnakerHttpException) t;
      } else {
        exception = new SpinnakerServerException(t);
      }
      final SpinnakerServerException finalException = exception;
      callbackExecutor.execute(
          new Runnable() {
            @Override
            public void run() {
              callback.onFailure(executorCallbackCall, finalException);
            }
          });
    }
  }
}
