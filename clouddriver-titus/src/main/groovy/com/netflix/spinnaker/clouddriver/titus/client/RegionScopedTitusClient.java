/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.Names;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.titus.client.model.*;
import com.netflix.spinnaker.clouddriver.titus.model.TitusError;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.logging.HttpLoggingInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class RegionScopedTitusClient implements TitusClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(TitusRestAdapter.class);

    /** Default connect timeout in milliseconds */
    private static final long DEFAULT_CONNECT_TIMEOUT = 10000;

    /** Default read timeout in milliseconds */
    private static final long DEFAULT_READ_TIMEOUT = 20000;

    /** An instance of {@link TitusRegion} that this RegionScopedTitusClient will use */
    private final TitusRegion titusRegion;

    /** Retrofit rest adapter for the titus environment */
    private final TitusRestAdapter titusRestAdapter;

    /** Actual connect timeout that will be used by the retrofit client */
    private final long connectTimeoutMillis;

    /** Actual read timeout that will be used by the retrofit client */
    private final long readTimeoutMillis;

    /** Titus client uses Jackson converter for retrofit. Titus client users can override this */
    private final ObjectMapper objectMapper;

    private final Registry registry;

    private final Retrofit retrofit;

    public RegionScopedTitusClient(TitusRegion titusRegion, Registry registry) {
        this(titusRegion, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT, TitusClientObjectMapper.configure(), registry);
    }

    public RegionScopedTitusClient(TitusRegion titusRegion,
                                   long connectTimeoutMillis,
                                   long readTimeoutMillis,
                                   ObjectMapper objectMapper,
                                   Registry registry) {
        this.titusRegion = titusRegion;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.objectMapper = objectMapper;
        this.retrofit = createRetrofit(titusRegion);
        this.titusRestAdapter = createTitusRestAdapter(this.retrofit);
        this.registry = registry;
    }

    private Retrofit createRetrofit(TitusRegion titusRegion){
      HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor((msg) -> LOGGER.info("[{}] {}", titusRegion.getName(), msg));
      interceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
      OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
        .addNetworkInterceptor(interceptor)
        .build();
      return new Retrofit.Builder()
        .baseUrl(titusRegion.getEndpoint())
        .addConverterFactory(JacksonConverterFactory.create(objectMapper))
        .client(okHttpClient)
        .validateEagerly(true)
        .build();
    }

    private TitusRestAdapter createTitusRestAdapter(Retrofit retrofit) {
        return retrofit.create(TitusRestAdapter.class);
    }


    // APIs
    // ------------------------------------------------------------------------------------------

    @Override
    public Job getJob(String jobId) {
        return execute("getJob", titusRestAdapter.getJob(jobId));
    }

    private <T> T execute(String requestName, Call<T> call) {
        long startTime = System.nanoTime();
        boolean success = false;
        Integer responseCode = null;
        try {
            retrofit2.Response<T> response = call.execute();
            responseCode = response.code();
            success = response.isSuccess();
            if (success) {
                return response.body();
            }
            String errorMessage = response.message();
            if (response != null && response.errorBody() != null) {
                try {
                  Converter<okhttp3.ResponseBody, TitusError> converter = retrofit.responseBodyConverter(TitusError.class, new Annotation[0]);
                  TitusError error = converter.convert(response.errorBody());
                  errorMessage = error.getMessage();
                } catch( Exception e ){
                }
            }
            throw new RuntimeException("response failed " + response.code() + " " + errorMessage);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        } finally {
            Id timerId = registry.createId("titus.request")
              .withTag("titusAccount", titusRegion.getAccount())
              .withTag("titusRegion", titusRegion.getName())
              .withTag("request", requestName)
              .withTag("success", Boolean.toString(success))
              .withTag("responseCode", Optional.ofNullable(responseCode).map(Object::toString).orElse("UNKNOWN"));
            registry.timer(timerId).record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        }

    }

    @Override
    public Job findJobByName(String jobName) {
        List<Job> jobs = execute("getJobsByLabel", titusRestAdapter.getJobsByLabel("name=" + jobName));
        if (jobs.isEmpty()) {
            return null;
        }
        Collections.sort(jobs, (j1, j2) -> -1 * j1.getSubmittedAt().compareTo(j2.getSubmittedAt()));
        return jobs.get(0);
    }

    @Override
    public List<Job> findJobsByApplication(String application) {
        return execute("getJobsByApplication", titusRestAdapter.getJobsByApplication(application));
    }

    @Override
    public String submitJob(SubmitJobRequest submitJobRequest) {
        JobDescription jobDescription = submitJobRequest.getJobDescription();
        if (jobDescription.getType() == null) {
            jobDescription.setType("service");
        }
        if (jobDescription.getUser() == null) {
            jobDescription.setUser("spinnaker");
        }
        if (jobDescription.getJobGroupSequence() == null && jobDescription.getType()!="batch") {
           try {
              int sequence = Names.parseName(jobDescription.getName()).getSequence();
              jobDescription.setJobGroupSequence(String.format("v%03d", sequence));
           } catch (Exception e) {
             // fail silently if we can't get a job group sequence
           }
        }
        jobDescription.getLabels().put("name", jobDescription.getName());
        jobDescription.getLabels().put("source", "spinnaker");
        jobDescription.getLabels().put("spinnakerAccount", submitJobRequest.getCredentials());
        SubmitJobResponse response = execute("submitJob", titusRestAdapter.submitJob(jobDescription));
        if (response == null) throw new RuntimeException(String.format("Failed to submit a titus job request for %s", jobDescription));
        String jobUri = response.getJobUri();
        return jobUri.substring(jobUri.lastIndexOf("/") + 1);
    }

    @Override
    public void updateJob(String jobId, Map<String, Object> jobAttributes) {
        execute("updateJob", titusRestAdapter.updateJob(jobId, jobAttributes));
    }

    @Override
    public Task getTask(String taskId) {
        return execute("getTask", titusRestAdapter.getTask(taskId));
    }

    @Override
    public void resizeJob(ResizeJobRequest resizeJobRequest){
        if(resizeJobRequest.getUser() == null){
           resizeJobRequest.withUser("spinnaker");
        }
        execute("resizeJob", titusRestAdapter.resizeJob(resizeJobRequest));
    }

    @Override
    public void activateJob(ActivateJobRequest activateJobRequest){
        if(activateJobRequest.getUser() == null){
           activateJobRequest.withUser("spinnaker");
        }
        execute("activateJob", titusRestAdapter.activateJob(activateJobRequest));
    }

    @Override
    public void terminateJob(String jobId) {
        execute("killJob", titusRestAdapter.killJob(RequestBody.create(MediaType.parse("text/plain"), jobId)));
    }

    @Override
    public void terminateTask(String taskId) {
        execute("terminateTask", titusRestAdapter.terminateTask(taskId));
    }

    @Override
    public void terminateTasksAndShrink(TerminateTasksAndShrinkJobRequest terminateTasksAndShrinkJob) {
      if(terminateTasksAndShrinkJob.getUser() == null){
        terminateTasksAndShrinkJob.withUser("spinnaker");
      }
      execute("terminateTask", titusRestAdapter.terminateTasksAndShrink(terminateTasksAndShrinkJob));
    }

    @Override
    public Logs getLogs(String taskId) {
        return execute("getLogs", titusRestAdapter.getLogs(taskId));
    }

    @Override
    public TitusHealth getHealth() {
        return new TitusHealth(HealthStatus.HEALTHY);
    }

    /**
     * Bulk read API for Jobs (/jobs) is just a list of job ids, so it makes more sense to use the
     * /tasks API for a list of Jobs. Task payload has pretty much everything that is in a Job so we can
     * construct a list of Jobs with a list of Tasks
     * @return
     */
    @Override
    public List<Job> getAllJobs() {
        return getAllJobsStream().collect(Collectors.toList());
    }

    @Override
    public List<Job.TaskSummary> getAllTasks() {

        return getAllJobsStream()
                .flatMap(j -> j.getTasks().stream())
                .collect(Collectors.toList());
    }

    private Stream<Job> getAllJobsStream() {
        return execute("getJobsByType", titusRestAdapter.getJobsByType("service")).stream();
    }

}
