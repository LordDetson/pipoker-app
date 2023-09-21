pipeline {
  agent any
  tools {
    jdk 'openjdk17'
    maven 'maven-3.6.3'
  }
  stages {
    stage ('Build pipoker-api') {
      steps {
        sh 'mvn clean install'
      }
    }
    stage ("Build and push docker image") {
      environment {
        DOCKER_HUB_LOGIN = credentials("DockerHub")
      }
      steps {
        sh 'mvn spring-boot:build-image -DpublishRegistry.username=$DOCKER_HUB_LOGIN_USR -DpublishRegistry.password=$DOCKER_HUB_LOGIN_PSW'
      }
    }
    stage ("Run docker image") {
      environment {
        CONTAINER_NAME = "pipoker-api"
        IMAGE_NAME = "lorddetson/pipoker-api"
        APP_PORT = "8080"
        APP_BROKER_LOGIN = credentials("BrokerLogin")
      }
      steps {
        sh "docker rm -f $CONTAINER_NAME"
        sh "docker rmi $IMAGE_NAME"
        sh "docker run -d --name $CONTAINER_NAME -p 81:$APP_PORT --network todo-manager-api -e APP_PORT=$APP_PORT -e APP_BROKER_LOGIN=$APP_BROKER_LOGIN_USR -e APP_BROKER_PASS=$APP_BROKER_LOGIN_PSW $IMAGE_NAME --spring.profiles.active=prod"
      }
    }
  }
}