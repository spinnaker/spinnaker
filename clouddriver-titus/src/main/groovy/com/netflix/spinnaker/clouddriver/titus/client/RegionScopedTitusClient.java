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
import com.netflix.spinnaker.clouddriver.titus.client.model.*;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.Call;
import retrofit2.Retrofit;

import java.io.IOException;
import java.util.*;
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

    public RegionScopedTitusClient(TitusRegion titusRegion) {
        this(titusRegion, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT, TitusClientObjectMapper.configure());
    }

    public RegionScopedTitusClient(TitusRegion titusRegion,
                                   long connectTimeoutMillis,
                                   long readTimeoutMillis,
                                   ObjectMapper objectMapper) {

        this.titusRegion = titusRegion;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.objectMapper = objectMapper;
        this.titusRestAdapter = createTitusRestAdapter(titusRegion);
    }

    private TitusRestAdapter createTitusRestAdapter(TitusRegion titusRegion) {
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor((msg) -> LOGGER.info("[{}] {}", titusRegion.getName(), msg));
        interceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
                .addNetworkInterceptor(interceptor)
                .build();
        return new Retrofit.Builder()
                .baseUrl(titusRegion.getEndpoint())
                .client(okHttpClient)
                .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                .validateEagerly(true)
                .build()
                .create(TitusRestAdapter.class);
    }


    // APIs
    // ------------------------------------------------------------------------------------------

    @Override
    public Job getJob(String jobId) {
        return execute(titusRestAdapter.getJob(jobId));
    }

    private <T> T execute(Call<T> call) {
        try {
            retrofit2.Response<T> response = call.execute();
            if (response.isSuccess()) {
                return response.body();
            }
            throw new RuntimeException("response failed " + response.code() + " " + response.message());
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }

    }

    @Override
    public Job findJobByName(String jobName) {
        List<Job> jobs = execute(titusRestAdapter.getJobsByLabel("name=" + jobName));
        if (jobs.isEmpty()) {
            return null;
        }
        Collections.sort(jobs, (j1, j2) -> -1 * j1.getSubmittedAt().compareTo(j2.getSubmittedAt()));
        return jobs.get(0);
    }

    @Override
    public List<Job> findJobsByApplication(String application) {
        return execute(titusRestAdapter.getJobsByApplication(application));
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
        if (jobDescription.getJobGroupSequence() == null) {
           try {
              int sequence = Names.parseName(jobDescription.getName()).getSequence();
              jobDescription.setJobGroupSequence(String.format("v%03d", sequence));
           } catch (Exception e) {
             // fail silently if we can't get a job group sequence
           }
        }
        jobDescription.getLabels().put("name", jobDescription.getName());
        jobDescription.getLabels().put("source", "spinnaker");
        SubmitJobResponse response = execute(titusRestAdapter.submitJob(jobDescription));
        if (response == null) throw new RuntimeException(String.format("Failed to submit a titus job request for %s", jobDescription));
        String jobUri = response.getJobUri();
        return jobUri.substring(jobUri.lastIndexOf("/") + 1);
    }

    @Override
    public void updateJob(String jobId, Map<String, Object> jobAttributes) {
        execute(titusRestAdapter.updateJob(jobId, jobAttributes));
    }

    @Override
    public Task getTask(String taskId) {
        return execute(titusRestAdapter.getTask(taskId));
    }

    @Override
    public void resizeJob(ResizeJobRequest resizeJobRequest){
        if(resizeJobRequest.getUser() == null){
           resizeJobRequest.withUser("spinnaker");
        }
        execute(titusRestAdapter.resizeJob(resizeJobRequest));
    }

    @Override
    public void terminateJob(String jobId) {
        execute(titusRestAdapter.killJob(RequestBody.create(MediaType.parse("text/plain"), jobId)));
    }

    @Override
    public void terminateTask(String taskId) {
        execute(titusRestAdapter.terminateTask(taskId));
    }

    @Override
    public Logs getLogs(String taskId) {
        return execute(titusRestAdapter.getLogs(taskId));
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
        return execute(titusRestAdapter.getJobsByType("service")).stream();
    }

}
