package by.babanin.pipoker;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

/**
 * MongoDB set up like on the server: authentication is on and the application has its own user in its database.
 */
public class MongoDbContainer extends GenericContainer<MongoDbContainer> {

    public static final String DATABASE = "pipoker";
    public static final String USERNAME = "pipoker";
    public static final String PASSWORD = "pipoker-test";

    private static final int PORT = 27017;
    private static final String CREATE_APP_USER = String.format(
            "db.getSiblingDB('%s').createUser({user: '%s', pwd: '%s', roles: [{role: 'readWrite', db: '%s'}]});",
            DATABASE, USERNAME, PASSWORD, DATABASE);

    public MongoDbContainer() {
        super("mongo:7.0");
        withExposedPorts(PORT);
        withEnv("MONGO_INITDB_ROOT_USERNAME", "root");
        withEnv("MONGO_INITDB_ROOT_PASSWORD", "root-test");
        withEnv("MONGO_INITDB_DATABASE", DATABASE);
        withCopyToContainer(Transferable.of(CREATE_APP_USER), "/docker-entrypoint-initdb.d/create-app-user.js");
        // MongoDB restarts once after running the init scripts
        waitingFor(Wait.forLogMessage("(?i).*waiting for connections.*\\n", 2));
    }

    public void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.host", this::getHost);
        registry.add("spring.mongodb.port", () -> getMappedPort(PORT));
        registry.add("spring.mongodb.database", () -> DATABASE);
        registry.add("spring.mongodb.username", () -> USERNAME);
        registry.add("spring.mongodb.password", () -> PASSWORD);
    }
}
