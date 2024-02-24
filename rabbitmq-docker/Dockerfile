FROM rabbitmq:3.13.0-management-alpine
RUN rabbitmq-plugins enable --offline rabbitmq_mqtt rabbitmq_federation_management rabbitmq_stomp
EXPOSE 15671 15672 61613