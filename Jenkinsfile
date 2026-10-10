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
        // Supplied by the controller rather than written in here. In the
        // lab these are the egress proxy and the truststore holding its CA,
        // without which dependency resolution fails with a PKIX error that
        // reads like a certificate problem and is really a proxy one. On a
        // machine that reaches Maven Central directly they must be absent,
        // because they would name a proxy that is not listening.
        MAVEN_OPTS = "${env.MAVEN_PROXY_OPTS ?: ''}"

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

        // Local registry: no managed container registry is reachable from
        // this lab (docs/00-environment-prerequisites.md, limitation L3).
        // Where a container's published port can be reached from inside
        // another container.
        //
        // Two of them, because the controller and the browser are not in
        // the same place and do not get the same answer.
        //
        // HOST_ADDR is how the controller reaches a published port. Under
        // host networking -- the lab controller -- it shares the host's
        // stack, so localhost is right. Docker Desktop does not implement
        // host networking, so the controller is bridged and reaches the
        // host through host.docker.internal.
        //
        // BROWSER_ADDR is how the Selenium container reaches the
        // application under test. That container is bridged in both
        // environments, so localhost there means the Selenium container
        // itself and never the application. It always goes via the host.
        HOST_ADDR     = "${env.DOCKER_HOST_ADDR ?: 'localhost'}"
        BROWSER_ADDR  = 'host.docker.internal'

        // Overridable because the conventional 5000 is taken by AirPlay
        // Receiver on macOS, where the registry moves to 5001.
        REGISTRY      = "${env.DOCKER_REGISTRY ?: 'localhost:5000'}"
        IMAGE_NAME    = 'attendance-portal'
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
                        # Published rather than host-networked, so this
                        # works on a bridged daemon too. The application
                        # keeps its default port inside the container and
                        # GATE_PORT is where it appears on the host.
                        docker create --name ${GATE_CONTAINER} \\
                            -p ${GATE_PORT}:8080 \\
                            -e ATTENDANCE_ENVIRONMENT=quality-gate \\
                            ${RUNTIME_IMAGE} \\
                            java -jar /attendance.war >/dev/null
                        docker cp ${WAR_PATH} ${GATE_CONTAINER}:/attendance.war
                        docker start ${GATE_CONTAINER} >/dev/null

                        # --add-host keeps host.docker.internal meaningful
                        # on engines that do not define it themselves, so the
                        # browser can reach the application under test by the
                        # same name the controller uses.
                        docker run -d --name ${SELENIUM_CONTAINER} \\
                            -p ${SELENIUM_PORT}:4444 \\
                            --add-host=host.docker.internal:host-gateway \\
                            --shm-size=2g \\
                            -e SE_NODE_MAX_SESSIONS=2 \\
                            ${SELENIUM_IMAGE} >/dev/null

                        echo "Waiting for the application under test..."
                        for i in \$(seq 1 60); do
                          curl -sf http://${HOST_ADDR}:${GATE_PORT}${APP_CONTEXT}/actuator/health >/dev/null && break
                          sleep 3
                        done
                        if ! curl -sf http://${HOST_ADDR}:${GATE_PORT}${APP_CONTEXT}/actuator/health >/dev/null; then
                          echo "The application under test never became healthy. Container log:"
                          docker logs --tail 40 ${GATE_CONTAINER}
                          exit 1
                        fi

                        echo "Waiting for the Selenium node..."
                        for i in \$(seq 1 60); do
                          curl -sf http://${HOST_ADDR}:${SELENIUM_PORT}/status 2>/dev/null | grep -q '"ready": *true' && break
                          sleep 3
                        done
                        if ! curl -sf http://${HOST_ADDR}:${SELENIUM_PORT}/status 2>/dev/null | grep -q '"ready": *true'; then
                          echo "The Selenium node never became ready. Container log:"
                          docker logs --tail 40 ${SELENIUM_CONTAINER}
                          exit 1
                        fi

                        curl -sf http://${HOST_ADDR}:${GATE_PORT}${APP_CONTEXT}/actuator/health
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
                                -Dapp.base.url=http://${BROWSER_ADDR}:${GATE_PORT}${APP_CONTEXT} \\
                                -Dselenium.remote.url=http://${HOST_ADDR}:${SELENIUM_PORT} \\
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

        stage('Build and publish image') {
            steps {
                script {
                    // Versioned, never only :latest. A tag that moves cannot
                    // be rolled back to, and Stage 14's rollback is exactly
                    // "run the previous version again" - which needs the
                    // previous version to still have a name.
                    env.IMAGE_TAG  = "${REGISTRY}/${IMAGE_NAME}:${env.APP_VERSION}"
                    env.IMAGE_LATEST = "${REGISTRY}/${IMAGE_NAME}:latest"

                    sh """
                        set -e

                        # The image packages the WAR that was just built and
                        # tested rather than rebuilding it, so the binary that
                        # ships is the binary the quality gate ran against.
                        docker build \\
                            --build-arg APP_VERSION=${env.APP_VERSION} \\
                            --build-arg GIT_COMMIT=${env.GIT_SHA} \\
                            --build-arg BUILD_TIME=\$(date -u +%Y-%m-%dT%H:%M:%SZ) \\
                            -t ${env.IMAGE_TAG} \\
                            -t ${env.IMAGE_LATEST} \\
                            -f Dockerfile .

                        docker push ${env.IMAGE_TAG}
                        docker push ${env.IMAGE_LATEST}

                        echo "Published:"
                        echo "  ${env.IMAGE_TAG}"
                        echo "  ${env.IMAGE_LATEST}"
                    """

                    // Read the tags back from the registry rather than
                    // trusting that the push said so.
                    sh """
                        echo "Registry now holds:"
                        curl -s http://${REGISTRY}/v2/${IMAGE_NAME}/tags/list
                        echo
                    """
                }
            }
        }

        stage('Deploy container') {
            steps {
                script {
                    env.CONTAINER_NAME = "attendance-app-${params.DEPLOY_ENVIRONMENT}"
                    env.CONTAINER_PORT = "${(params.APP_PORT as Integer) + 10}"

                    sh """
                        set -e

                        echo "Deploying ${env.IMAGE_TAG} as ${env.CONTAINER_NAME} on port ${env.CONTAINER_PORT}"

                        # Pull from the registry rather than using the local
                        # build cache: this is the deployment path a separate
                        # target node would take, and exercising a different
                        # one here would prove nothing about it.
                        docker pull ${env.IMAGE_TAG}

                        docker rm -f ${env.CONTAINER_NAME} >/dev/null 2>&1 || true

                        # The data volume is per environment and is NOT
                        # removed: replacing a container must never destroy a
                        # term's attendance records.
                        docker volume create attendance-data-${params.DEPLOY_ENVIRONMENT} >/dev/null

                        docker run -d --name ${env.CONTAINER_NAME} \\
                            -p ${env.CONTAINER_PORT}:8080 \\
                            -v attendance-data-${params.DEPLOY_ENVIRONMENT}:/app/data \\
                            -e ATTENDANCE_ENVIRONMENT=${params.DEPLOY_ENVIRONMENT} \\
                            --restart unless-stopped \\
                            --label app.version=${env.APP_VERSION} \\
                            --label app.commit=${env.GIT_SHA} \\
                            ${env.IMAGE_TAG} >/dev/null

                        for i in \$(seq 1 60); do
                          if curl -sf http://${HOST_ADDR}:${env.CONTAINER_PORT}${APP_CONTEXT}/actuator/health >/dev/null; then
                            echo "Container healthy after \${i} attempt(s)"
                            break
                          fi
                          sleep 3
                        done

                        if ! curl -sf http://${HOST_ADDR}:${env.CONTAINER_PORT}${APP_CONTEXT}/actuator/health >/dev/null; then
                          echo "Deployed container never became healthy:"
                          docker logs --tail 40 ${env.CONTAINER_NAME}
                          exit 1
                        fi

                        echo "Container deployment:"
                        docker ps --filter name=${env.CONTAINER_NAME} \\
                                  --format '  {{.Names}}  {{.Image}}  {{.Status}}  {{.Ports}}'
                        curl -s http://${HOST_ADDR}:${env.CONTAINER_PORT}${APP_CONTEXT}/actuator/info
                        echo
                        echo "Containerised application URL: http://localhost:${env.CONTAINER_PORT}${APP_CONTEXT}"
                    """
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
                    def base = "http://${env.HOST_ADDR}:${params.APP_PORT}${env.APP_CONTEXT}"

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
            echo """Pipeline succeeded for ${env.APP_VERSION}:
  image     ${env.IMAGE_TAG}
  Tomcat    http://localhost:${params.APP_PORT}${env.APP_CONTEXT}
  container http://localhost:${env.CONTAINER_PORT}${env.APP_CONTEXT}"""
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
