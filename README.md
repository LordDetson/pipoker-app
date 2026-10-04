# PiPoker backend

The server of [PiPoker](https://pipoker.duckdns.org), free online Planning Poker without registration:
rooms with a card deck, voters and watchers, votes revealed to everyone at once and the history of the last
estimates. The web client is [pipoker-web](https://github.com/LordDetson/pipoker-web);
the server setup is in [pipoker-docker-config](https://github.com/LordDetson/pipoker-docker-config).

## How it works
Spring Boot application on Java 25, built with Maven:
- `pipoker-service`: rooms, participants and votes, kept in MongoDB; a room is changed by atomic updates (`AtomicRoomRepository`)
  so that people acting at the same moment do not overwrite each other.
- `pipoker-controller`: the runnable application. Clients talk to it over STOMP on the WebSocket endpoint `/ws`:
  they send commands to `/app/room/...` and receive the room's events from `/topic/room.{roomId}`
  through RabbitMQ, which serves as the STOMP broker. It also tracks who is still connected, closes rooms idle for 30 minutes
  and serves metrics for the activity dashboard on the internal port 8081.

## Configuration
The application reads these environment variables:

| Variable | Meaning |
|----------|---------|
| `APP_PORT` | HTTP port, 8080 by default |
| `BROKER_HOST`, `STOMP_BROKER_PORT` | RabbitMQ with the STOMP plugin; the port is 61613 by default |
| `STOMP_BROKER_SYSTEM_LOGIN`, `STOMP_BROKER_SYSTEM_PASS`, `STOMP_BROKER_USER_LOGIN`, `STOMP_BROKER_USER_PASS` | RabbitMQ credentials |
| `MONGODB_HOST`, `MONGODB_PORT`, `MONGODB_DBNAME`, `MONGODB_USR`, `MONGODB_PASS` | MongoDB; the port is 27017 by default |

`server/compose.yml` in pipoker-docker-config starts the application together with MongoDB and RabbitMQ.

## Tests
`./mvnw verify` runs all tests and writes coverage reports to `*/target/site/jacoco`.
- Unit tests (`*Test`) need nothing but Java 25: `./mvnw test`.
- Integration tests (`*IT`) start MongoDB and RabbitMQ in Docker with Testcontainers and drive the application over STOMP the way the web client does. They need a running Docker; skip them with `./mvnw verify -DskipITs`.

## Releases
GitHub Actions (`.github/workflows/ci.yml`) builds and tests every pull request. Every push to main also publishes
the image `ghcr.io/lorddetson/pipoker-controller`, which the QA environment picks up within a few minutes.
A commit checked on QA goes to PROD through the **Promote to PROD** workflow (`.github/workflows/promote.yml`),
which waits for approval.

## License
PiPoker is released under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt).
