pipeline {
  agent any
  tools {
    jdk 'openjdk17'
    maven 'maven-3.6.3'
  }
  stages {
    stage ('Build pipoker-app') {
      steps {
        sh 'mvn clean install'
      }
    }
    stage ("Build and push docker image") {
      environment {
        DOCKER_HUB_LOGIN = credentials("DockerHub")
      }
      steps {
        // Only pipoker-controller is a runnable Spring Boot application, pipoker-service is a library
        sh 'mvn -pl pipoker-controller spring-boot:build-image -DpublishRegistry.username=$DOCKER_HUB_LOGIN_USR -DpublishRegistry.password=$DOCKER_HUB_LOGIN_PSW'
      }
    }
    stage ("Run docker image") {
      environment {
        CONTAINER_NAME = "pipoker-api"
        IMAGE_NAME = "lorddetson/pipoker-controller"
        APP_PORT = "8080"
        BROKER_HOST = "rebbitmq"
        STOMP_BROKER_PORT = "61613"
        BROKER_LOGIN = credentials("BrokerLogin")
        MONGODB_HOST = "mongodb"
        MONGODB_PORT = "27017"
        MONGODB_DBNAME = "pipoker"
        MONGODB_LOGIN = credentials("MongoDbLogin")
      }
      steps {
        sh 'docker rm -f $CONTAINER_NAME || true'
        sh 'docker rmi $IMAGE_NAME || true'
        sh '''
          docker run -d --name $CONTAINER_NAME -p 81:$APP_PORT --network pipokernet \
            -e APP_PORT=$APP_PORT \
            -e BROKER_HOST=$BROKER_HOST \
            -e STOMP_BROKER_PORT=$STOMP_BROKER_PORT \
            -e STOMP_BROKER_SYSTEM_LOGIN=$BROKER_LOGIN_USR \
            -e STOMP_BROKER_SYSTEM_PASS=$BROKER_LOGIN_PSW \
            -e STOMP_BROKER_USER_LOGIN=$BROKER_LOGIN_USR \
            -e STOMP_BROKER_USER_PASS=$BROKER_LOGIN_PSW \
            -e MONGODB_HOST=$MONGODB_HOST \
            -e MONGODB_PORT=$MONGODB_PORT \
            -e MONGODB_DBNAME=$MONGODB_DBNAME \
            -e MONGODB_USR=$MONGODB_LOGIN_USR \
            -e MONGODB_PASS=$MONGODB_LOGIN_PSW \
            $IMAGE_NAME --spring.profiles.active=prod
        '''
      }
    }
  }
}
