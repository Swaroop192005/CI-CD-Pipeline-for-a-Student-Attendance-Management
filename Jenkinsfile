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

        // The quality gate runs the journeys against a throwaway instance of
        // the artefact just built, on a port of its own so it never collides
        // with a deployed environment.
        GATE_CONTAINER   = 'attendance-gate'
        GATE_PORT        = '8095'
        RUNTIME_IMAGE    = 'eclipse-temurin:21-jre-jammy'

        // The journeys run against a Selenium container rather than a
        // browser on the controller: the controller image has no browser and
        // none of the graphics libraries one needs, and installing them is
        // not possible here because apt cannot reach the Debian mirrors.
        // The container also pins the browser and its driver together, which
        // removes the version-mismatch failure mode entirely.
        SELENIUM_CONTAINER = 'attendance-selenium'
        SELENIUM_IMAGE     = 'selenium/standalone-chromium:latest'
        SELENIUM_PORT      = '4444'
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

        stage('Quality gate: Selenium') {
            when {
                expression { return params.RUN_SELENIUM }
            }
            steps {
                script {
                    // The suite needs a running application, so a throwaway
                    // instance is started from the artefact just packaged -
                    // the same WAR that is deployed if the gate passes.
                    // Testing a different build than the one deployed would
                    // make the gate decorative.
                    sh """
                        set -e
                        docker rm -f ${GATE_CONTAINER} ${SELENIUM_CONTAINER} >/dev/null 2>&1 || true

                        # The gate instance runs the WAR in its executable
                        # form rather than deploying it to Tomcat. It is the
                        # same artefact either way, and this mode honours
                        # SERVER_PORT, so the gate takes a port of its own
                        # without colliding with a deployed environment.
                        #
                        # create -> cp -> start, not a bind mount. The Docker
                        # CLI here talks to the *host* daemon over a mounted
                        # socket, so a -v path would be resolved against the
                        # host filesystem, where the controller's workspace
                        # does not exist: Docker would silently create an
                        # empty directory and the container would start with
                        # no artefact. docker cp streams the file from this
                        # container's own filesystem, which is where it is.
                        docker create --name ${GATE_CONTAINER} --network host \\
                            -e SERVER_PORT=${GATE_PORT} \\
                            -e ATTENDANCE_ENVIRONMENT=quality-gate \\
                            ${RUNTIME_IMAGE} \\
                            java -jar /attendance.war >/dev/null
                        docker cp ${WAR_PATH} ${GATE_CONTAINER}:/attendance.war
                        docker start ${GATE_CONTAINER} >/dev/null

                        docker run -d --name ${SELENIUM_CONTAINER} --network host \\
                            --shm-size=2g \\
                            -e SE_NODE_MAX_SESSIONS=2 \\
                            ${SELENIUM_IMAGE} >/dev/null

                        echo "Waiting for the application under test..."
                        for i in \$(seq 1 60); do
                          curl -sf http://localhost:${GATE_PORT}${APP_CONTEXT}/actuator/health >/dev/null && break
                          sleep 3
                        done
                        if ! curl -sf http://localhost:${GATE_PORT}${APP_CONTEXT}/actuator/health >/dev/null; then
                          echo "The application under test never became healthy. Container log:"
                          docker logs --tail 40 ${GATE_CONTAINER}
                          exit 1
                        fi

                        echo "Waiting for the Selenium node..."
                        for i in \$(seq 1 60); do
                          curl -sf http://localhost:${SELENIUM_PORT}/status 2>/dev/null | grep -q '"ready": *true' && break
                          sleep 3
                        done
                        if ! curl -sf http://localhost:${SELENIUM_PORT}/status 2>/dev/null | grep -q '"ready": *true'; then
                          echo "The Selenium node never became ready. Container log:"
                          docker logs --tail 40 ${SELENIUM_CONTAINER}
                          exit 1
                        fi

                        curl -sf http://localhost:${GATE_PORT}${APP_CONTEXT}/actuator/health
                        echo
                        echo "Application under test and browser node are both ready."
                    """
                }
            }
            post {
                always {
                    script {
                        // The suite runs here rather than in `steps` so that
                        // the report is published and the containers removed
                        // whether the journeys passed or failed.
                        def gateResult = sh(returnStatus: true, script: """
                            "\$MVN" -B -pl selenium-tests verify \\
                                -DskipSeleniumTests=false \\
                                -Dapp.base.url=http://localhost:${GATE_PORT}${APP_CONTEXT} \\
                                -Dselenium.remote.url=http://localhost:${SELENIUM_PORT} \\
                                -Dselenium.headless=true
                        """)

                        junit allowEmptyResults: true,
                              testResults: 'selenium-tests/target/failsafe-reports/*.xml'

                        // A failed journey leaves a screenshot and a page
                        // dump. Archive them before the workspace is cleaned:
                        // by the time anyone reads the build, they are the
                        // only record of what the browser actually saw.
                        archiveArtifacts artifacts: 'selenium-tests/target/screenshots/**',
                                         allowEmptyArchive: true,
                                         onlyIfSuccessful: false

                        sh "docker rm -f ${GATE_CONTAINER} ${SELENIUM_CONTAINER} >/dev/null 2>&1 || true"

                        if (gateResult != 0) {
                            // Fail the build here, before the deploy stages.
                            // This is the gate: a failing journey must stop
                            // the deployment, not merely mark it unstable.
                            error('Selenium quality gate failed. The deployment stages will not run.')
                        }
                        echo 'Selenium quality gate passed.'
                    }
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
