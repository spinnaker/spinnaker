package com.netflix.kayenta.controllers;

import com.netflix.kayenta.canary.CanaryJudge;
import com.netflix.kayenta.canary.ExecutionMapper;
import com.netflix.kayenta.config.WebConfiguration;
import com.netflix.kayenta.metrics.MetricsServiceRepository;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.storage.StorageServiceRepository;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@SpringBootTest(
        classes = BaseControllerTest.TestControllersConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@RunWith(SpringRunner.class)
public abstract class BaseControllerTest {

    @MockBean
    AccountCredentialsRepository accountCredentialsRepository;
    @MockBean
    StorageServiceRepository storageServiceRepository;

    @MockBean
    ExecutionRepository executionRepository;
    @MockBean
    ExecutionMapper executionMapper;

    @MockBean
    MetricsServiceRepository metricsServiceRepository;

    @MockBean(answer = Answers.RETURNS_MOCKS)
    Registry registry;

    @MockBean
    CanaryJudge canaryJudge;

    @Autowired
    private WebApplicationContext webApplicationContext;

    protected MockMvc mockMvc;

    @Before
    public void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext)
                .alwaysDo(print())
                .build();
    }

    @EnableWebMvc
    @Import(WebConfiguration.class)
    @Configuration
    public static class TestControllersConfiguration {


    }

}
