package com.netflix.spinnaker.rosco.services;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.providers.aws.config.RoscoAWSConfiguration.AWSNamedImage;
import java.util.List;
import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.PUT;
import retrofit.http.Query;

public interface ClouddriverService {
  @PUT("/artifacts/fetch/")
  Response fetchArtifact(@Body Artifact artifact);

  @GET("/aws/images/find")
  List<AWSNamedImage> findAmazonImageByName(
      @Query("q") String name, @Query("account") String account, @Query("region") String region);
}
