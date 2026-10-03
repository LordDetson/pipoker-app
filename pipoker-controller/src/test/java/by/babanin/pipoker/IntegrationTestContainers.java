package by.babanin.pipoker;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.lifecycle.Startables;

/**
 * MongoDB and RabbitMQ set up like on the server (see pipoker-docker-config/server/compose.yml),
 * started once for all integration tests.
 */
public final class IntegrationTestContainers {

    private static final String MONGODB_DATABASE = "pipoker";
    private static final String MONGODB_USERNAME = "pipoker";
    private static final String MONGODB_PASSWORD = "pipoker-test";
    private static final int MONGODB_PORT = 27017;

    private static final String BROKER_USERNAME = "pipoker";
    private static final String BROKER_PASSWORD = "pipoker-test";
    private static final int STOMP_PORT = 61613;

    @SuppressWarnings("resource")
    private static final GenericContainer<?> MONGODB = new GenericContainer<>("mongo:7.0")
            .withExposedPorts(MONGODB_PORT)
            .withEnv("MONGO_INITDB_ROOT_USERNAME", "root")
            .withEnv("MONGO_INITDB_ROOT_PASSWORD", "root-test")
            .withEnv("MONGO_INITDB_DATABASE", MONGODB_DATABASE)
            .withCopyToContainer(Transferable.of(String.format(
                    "db.getSiblingDB('%s').createUser({user: '%s', pwd: '%s', roles: [{role: 'readWrite', db: '%s'}]});",
                    MONGODB_DATABASE, MONGODB_USERNAME, MONGODB_PASSWORD, MONGODB_DATABASE)),
                    "/docker-entrypoint-initdb.d/create-app-user.js")
            // MongoDB restarts once after running the init scripts
            .waitingFor(Wait.forLogMessage("(?i).*waiting for connections.*\\n", 2));

    @SuppressWarnings("resource")
    private static final GenericContainer<?> RABBITMQ = new GenericContainer<>("rabbitmq:3.12-alpine")
            .withExposedPorts(STOMP_PORT)
            .withEnv("RABBITMQ_DEFAULT_USER", BROKER_USERNAME)
            .withEnv("RABBITMQ_DEFAULT_PASS", BROKER_PASSWORD)
            .withCopyToContainer(Transferable.of("[rabbitmq_stomp]."), "/etc/rabbitmq/enabled_plugins")
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*\\n", 1));

    private IntegrationTestContainers() {
    }

    public static void start(DynamicPropertyRegistry registry) {
        Startables.deepStart(MONGODB, RABBITMQ).join();

        registry.add("spring.data.mongodb.host", MONGODB::getHost);
        registry.add("spring.data.mongodb.port", () -> MONGODB.getMappedPort(MONGODB_PORT));
        registry.add("spring.data.mongodb.database", () -> MONGODB_DATABASE);
        registry.add("spring.data.mongodb.username", () -> MONGODB_USERNAME);
        registry.add("spring.data.mongodb.password", () -> MONGODB_PASSWORD);

        registry.add("broker.host", RABBITMQ::getHost);
        registry.add("broker.stomp.port", () -> RABBITMQ.getMappedPort(STOMP_PORT));
        registry.add("broker.stomp.system.login", () -> BROKER_USERNAME);
        registry.add("broker.stomp.system.pass", () -> BROKER_PASSWORD);
        registry.add("broker.stomp.user.login", () -> BROKER_USERNAME);
        registry.add("broker.stomp.user.pass", () -> BROKER_PASSWORD);
    }
}
