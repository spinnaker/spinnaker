package com.netflix.spinnaker.amos.gce;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.Region;
import com.google.api.services.compute.model.RegionList;
import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.amos.AccountCredentials;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

public class GoogleNamedAccountCredentials implements AccountCredentials<GoogleCredentials> {
    public GoogleNamedAccountCredentials(String kmsServer, String accountName, String environment, String accountType, String projectName, String applicationName) {
        this(kmsServer, accountName, environment, accountType, projectName, null, applicationName);
    }

    public GoogleNamedAccountCredentials(String kmsServer, String accountName, String environment, String accountType, String projectName, List<String> requiredGroupMembership, String applicationName) {
        this.kmsServer = kmsServer;
        this.accountName = accountName;
        this.environment = environment;
        this.accountType = accountType;
        this.projectName = projectName;
        this.requiredGroupMembership = requiredGroupMembership == null ? Collections.emptyList() : Collections.unmodifiableList(requiredGroupMembership);
        this.credentials = (kmsServer != null) ? buildCredentials() : null;
        this.regionToZonesMap = (credentials != null && credentials.getCompute() != null) ? queryRegions(credentials.getCompute(), projectName) : Collections.emptyMap();
        this.applicationName = applicationName;
    }

    @Override
    public String getName() {
        return accountName;
    }

    @Override
    public String getEnvironment() {
        return environment;
    }

    @Override
    public String getAccountType() {
        return accountType;
    }

    @Override
    public String getCloudProvider() {
        return CLOUD_PROVIDER;
    }

    public Map<String, List<String>> getRegions() {
        return regionToZonesMap;
    }

    public GoogleCredentials getCredentials() {
        return credentials;
    }

    private GoogleCredentials buildCredentials() {
        JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
        HttpTransport httpTransport = buildHttpTransport();
        String jsonKey = getJsonKey(kmsServer, accountName, httpTransport, JSON_FACTORY);

        try {
            if (jsonKey != null) {
                try (InputStream credentialStream = new ByteArrayInputStream(jsonKey.getBytes())) {

                    // JSON key was specified in matching config on key server.
                    GoogleCredential credential = GoogleCredential.fromStream(credentialStream, httpTransport, JSON_FACTORY).createScoped(Collections.singleton(ComputeScopes.COMPUTE));
                    Compute compute = new Compute.Builder(httpTransport, JSON_FACTORY, null).setApplicationName(applicationName).setHttpRequestInitializer(credential).build();

                    return new GoogleCredentials(projectName, compute, httpTransport, JSON_FACTORY, jsonKey);
                }
            } else {
                // No JSON key was specified in matching config on key server, so use application default credentials.
                GoogleCredential credential = GoogleCredential.getApplicationDefault();
                Compute compute = new Compute.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(applicationName).build();

                return new GoogleCredentials(projectName, compute, httpTransport, JSON_FACTORY, null);
            }
        } catch (IOException ioe) {
            throw new RuntimeException("failed to create credentials", ioe);
        }
    }

    private HttpTransport buildHttpTransport() {
        try {
            return GoogleNetHttpTransport.newTrustedTransport();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create trusted transport", e);
        }
    }

    @VisibleForTesting
    @SuppressWarnings("unchecked")
    static String getJsonKey(String kmsServer, String accountName, HttpTransport transport, JsonFactory jsonFactory) {
        try {
            URI credentialsUri = URI.create(kmsServer + "/credentials/" + accountName).normalize();
            HttpResponse keyResponse = transport.createRequestFactory().buildGetRequest(new GenericUrl(credentialsUri)).execute();
            Map<String, Object> json = (Map<String, Object>) jsonFactory.createJsonParser(keyResponse.getContent()).parseAndClose(Map.class);
            Object jsonKey = json.get("jsonKey");

            if (jsonKey instanceof String) {
                return (String) jsonKey;
            }
            return null;
        } catch (Exception ex) {
            throw new RuntimeException("Unable to load kms configuration", ex);
        }
    }

    private static Map<String, List<String>> queryRegions(Compute compute, String projectName) {
        RegionList regionList = fetchRegions(compute, projectName);
        return convertToMap(regionList);
    }

    @VisibleForTesting
    static Map<String, List<String>> convertToMap(RegionList regionList) {
        return regionList.getItems().stream()
                .collect(Collectors.toMap(
                        Region::getName,
                        r -> r.getZones().stream()
                                .map(GoogleNamedAccountCredentials::getLocalName)
                                .collect(Collectors.toList())));
    }

    private static RegionList fetchRegions(Compute compute, String projectName) {
        try {
            return compute.regions().list(projectName).execute();
        } catch (IOException ioe) {
            throw new RuntimeException("Failed loading regions for " + projectName, ioe);
        }
    }

    private static String getLocalName(String fullUrl) {
        return fullUrl.substring(fullUrl.lastIndexOf('/') + 1);
    }

    @Override
    public String getProvider() {
        return getCloudProvider();
    }

    public String getAccountName() {
        return accountName;
    }

    public String getProjectName() {
        return projectName;
    }

    public List<String> getRequiredGroupMembership() {
        return requiredGroupMembership;
    }

    public String getApplicationName() {
        return applicationName;
    }

    private static final String CLOUD_PROVIDER = "gce";
    private final String kmsServer;
    private final Map<String, List<String>> regionToZonesMap;
    private final String accountName;
    private final String environment;
    private final String accountType;
    private final String projectName;
    private final GoogleCredentials credentials;
    private final List<String> requiredGroupMembership;
    private final String applicationName;
}
