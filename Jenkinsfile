// Declarative CI/CD pipeline for the Student Attendance Management Portal.
//
// Pipeline-as-code lives in the same repository as the application it
// builds, so a change to the build and the change it builds are reviewed
// together and move through branches together.
//
// Stages: checkout -> build -> unit test -> package -> deploy to Tomcat.
// The Selenium quality gate (Stage 10) and the Docker image and registry
// push (Stage 12) extend this file in their own stages.

pipeline {
    agent any

    parameters {
        choice(
            name: 'DEPLOY_ENVIRONMENT',
            choices: ['staging', 'production', 'local'],
            description: '''Environment label the deployed application reports.
                Rendered in the UI banner and returned by /actuator/info, so a
                deploy that landed with the wrong configuration is visible at a
                glance instead of being silent.''')
        string(
            name: 'APP_PORT',
            defaultValue: '8090',
            description: 'Host port the deployed Tomcat listens on.')
        booleanParam(
            name: 'RUN_SELENIUM',
            defaultValue: true,
            description: 'Run the Selenium quality gate. Clearing it skips the gate, and therefore the deploy.')
    }

    options {
        timestamps()
        timeout(time: 40, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '25', artifactNumToKeepStr: '10'))
        disableConcurrentBuilds()
    }

    environment {
        // Pinned Maven, bind-mounted into the controller: the lab cannot
        // reach deb.debian.org to install one.
        MVN = '/opt/maven/bin/mvn'

        // The JVM does not read the HTTPS_PROXY environment variable, so
        // the proxy is passed as system properties, together with a
        // truststore that holds the proxy's CA. Without both, dependency
        // resolution fails with a PKIX error that reads like a certificate
        // problem and is really a proxy one.
        MAVEN_OPTS = '-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=40387 -Dhttp.nonProxyHosts=localhost|127.0.0.1 -Djavax.net.ssl.trustStore=/var/jenkins_conf/truststore.jks -Djavax.net.ssl.trustStorePassword=changeit'

        TOMCAT_IMAGE     = 'tomcat:10.1-jdk21-temurin'
        APP_CONTEXT      = '/attendance'
        WAR_PATH         = 'app/target/attendance.war'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_SHA = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.GIT_BRANCH_NAME = sh(script: 'git rev-parse --abbrev-ref HEAD', returnStdout: true).trim()

                    // Semantic version for the artefact and, from Stage 12,
                    // the image tag: the build number makes it unique, the
                    // commit makes it traceable.
                    env.APP_VERSION = "1.0.${env.BUILD_NUMBER}-${env.GIT_SHA}"

                    // One deployment target per environment, so deploying
                    // staging does not silently replace production. A single
                    // fixed container name lets the two environments
                    // overwrite each other, which would make the parameter
                    // look effective while actually being destructive.
                    env.TOMCAT_CONTAINER = "attendance-tomcat-${params.DEPLOY_ENVIRONMENT}"

                    echo "Building ${env.APP_VERSION} from ${env.GIT_BRANCH_NAME} (${env.GIT_SHA})"
                    echo "Deployment target container: ${env.TOMCAT_CONTAINER}"
                }
            }
        }

        stage('Build') {
            steps {
                sh '"$MVN" -B -DskipTests clean compile'
            }
        }

        stage('Unit test') {
            steps {
                // Surefire only, as its own stage. Failing fast on a unit
                // regression costs seconds; discovering the same regression
                // after the integration run costs minutes.
                sh '"$MVN" -B test'
            }
            post {
                always {
                    junit allowEmptyResults: false,
                          testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Package') {
            steps {
                sh '"$MVN" -B -DskipTests package'
                sh 'ls -la app/target/attendance.war'
            }
            post {
                success {
                    archiveArtifacts artifacts: "${WAR_PATH}",
                                     fingerprint: true,
                                     onlyIfSuccessful: true
                }
            }
        }

        stage('Integration test') {
            steps {
                // Failsafe only - the unit tests already ran in their own
                // stage, and running them twice would just make the
                // pipeline slower without telling anyone anything new.
                sh '"$MVN" -B failsafe:integration-test failsafe:verify'
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: '**/target/failsafe-reports/*.xml'
                }
            }
        }

        stage('Deploy to Tomcat') {
            steps {
                script {
                    sh """
                        set -e

                        echo "Deploying ${env.APP_VERSION} to Tomcat as '${params.DEPLOY_ENVIRONMENT}' on port ${params.APP_PORT}"

                        # Replace the container rather than only swapping the
                        # WAR: the environment label is supplied as an
                        # environment variable, and changing it has to take
                        # effect for the parameter to mean anything.
                        docker rm -f ${TOMCAT_CONTAINER} >/dev/null 2>&1 || true

                        docker run -d --name ${TOMCAT_CONTAINER} \\
                            -p ${params.APP_PORT}:8080 \\
                            -e ATTENDANCE_ENVIRONMENT=${params.DEPLOY_ENVIRONMENT} \\
                            -e ATTENDANCE_ELIGIBILITY_THRESHOLD=75 \\
                            -e SPRING_DATASOURCE_URL='jdbc:h2:file:/usr/local/tomcat/data/attendance;DB_CLOSE_ON_EXIT=FALSE' \\
                            ${TOMCAT_IMAGE}

                        # Give Catalina a moment to create webapps/ before copying in.
                        sleep 5
                        docker cp ${WAR_PATH} ${TOMCAT_CONTAINER}:/usr/local/tomcat/webapps/attendance.war
                    """
                }
            }
        }

        stage('Verify deployment') {
            steps {
                script {
                    def base = "http://localhost:${params.APP_PORT}${env.APP_CONTEXT}"

                    // Health first: a deploy is not done when the container
                    // is running, it is done when the application answers.
                    sh """
                        set -e
                        for i in \$(seq 1 60); do
                          if curl -sf ${base}/actuator/health > /dev/null; then
                            echo "Health check passed after \${i} attempt(s)"
                            curl -s ${base}/actuator/health
                            echo
                            exit 0
                          fi
                          sleep 3
                        done
                        echo "Application did not become healthy; last 40 lines of the container log:"
                        docker logs --tail 40 ${TOMCAT_CONTAINER}
                        exit 1
                    """

                    // Then assert the parameter actually took effect. A
                    // deploy that is healthy but configured for the wrong
                    // environment is the failure this check exists to catch.
                    def reported = sh(
                        script: "curl -s ${base}/actuator/info | sed -n 's/.*\"environment\":\"\\([^\"]*\\)\".*/\\1/p'",
                        returnStdout: true).trim()

                    echo "Deployed application reports environment: '${reported}'"
                    if (reported != params.DEPLOY_ENVIRONMENT) {
                        error("Deployed environment is '${reported}' but '${params.DEPLOY_ENVIRONMENT}' was requested. " +
                              "The parameter did not reach the application.")
                    }
                    echo "Parameter verified: requested '${params.DEPLOY_ENVIRONMENT}', deployed '${reported}'"
                    echo "Deployed application URL: ${base}"
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline succeeded. ${env.APP_VERSION} is deployed at http://localhost:${params.APP_PORT}${env.APP_CONTEXT}"
        }
        failure {
            echo "Pipeline failed at stage '${env.STAGE_NAME}'. The deploy stages after it did not run."
        }
        always {
            // Keep the workspace from accumulating 60 MB WARs per build.
            cleanWs(deleteDirs: true, notFailBuild: true,
                    patterns: [[pattern: '**/target/**', type: 'INCLUDE']])
        }
    }
}
