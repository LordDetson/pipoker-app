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
  }
}