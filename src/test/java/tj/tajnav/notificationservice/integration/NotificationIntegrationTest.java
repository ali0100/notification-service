package tj.tajnav.notificationservice.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tj.tajnav.notificationservice.domain.NotificationStatus;
import tj.tajnav.notificationservice.persistence.NotificationEntity;
import tj.tajnav.notificationservice.persistence.NotificationRepository;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@DirtiesContext
class NotificationIntegrationTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "secret";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));


    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @Container
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    NotificationRepository notificationRepository;

    @Test
    void submitNotification_returnsAcceptedAndProcessesAsync() throws Exception {
        String body = """
            {
              "recipientId": "%s",
              "templateCode": "WELCOME",
              "channelType": "EMAIL",
              "parameters": {"name": "Alice"},
              "priority": "HIGH"
            }
            """.formatted(UUID.randomUUID());

        String response = mockMvc.perform(post("/api/v1/notifications")
                .with(httpBasic(USERNAME, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.notificationId").isNotEmpty())
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        UUID notificationId = UUID.fromString(
            response.replaceAll(".*\"notificationId\":\"([^\"]+)\".*", "$1"));

        await().atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                NotificationEntity entity = notificationRepository.findById(notificationId).orElseThrow();
                assertThat(entity.getStatus()).isIn(NotificationStatus.SENT, NotificationStatus.FAILED);
            });
    }

    @Test
    void submitNotification_returnsUnauthorized_withoutCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void submitNotification_returnsBadRequest_whenMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                .with(httpBasic(USERNAME, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void submitNotification_returnsTooManyRequests_whenRateLimitExceeded() throws Exception {
        UUID recipientId = UUID.randomUUID();
        String body = """
            {
              "recipientId": "%s",
              "templateCode": "WELCOME",
              "channelType": "EMAIL",
              "parameters": {},
              "priority": "LOW"
            }
            """.formatted(recipientId);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/notifications")
                    .with(httpBasic(USERNAME, PASSWORD))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isAccepted());
        }

        mockMvc.perform(post("/api/v1/notifications")
                .with(httpBasic(USERNAME, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isTooManyRequests());
    }
}