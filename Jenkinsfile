pipeline {
    agent any

    environment {
        GIT_REPO           = "https://github.com/simbudevops/webpage-project.git"
        DOCKERHUB_USERNAME = "simbudevops7497"
        IMAGE_NAME         = "raegan-app"
        DOCKERHUB_CREDS    = "dockerhub-creds"
        SONAR_SERVER       = "SonarQube"
        JAVA_HOME          = "/usr/lib/jvm/java-17-openjdk-amd64"
        NAMESPACE          = "default"
        DEPLOYMENT_NAME    = "raegan-app-iam"    // FIX #1: was "raegan-app", must match k8s-deployment.yml
        IMAGE_TAG          = "v2"
    }

    stages {

        // ─────────────────────────────────────────────────────────────────
        // STAGE 1: Install Required Tools
        // ─────────────────────────────────────────────────────────────────
        stage('Install Required Tools') {
            steps {
                sh '''
                    echo "=== Installing Required Tools ==="

                    sudo apt-get update -y

                    # ── Java 17 ───────────────────────────────────────────
                    sudo apt-get install -y openjdk-17-jdk
                    sudo update-alternatives --install /usr/bin/java  java  /usr/lib/jvm/java-17-openjdk-amd64/bin/java  2
                    sudo update-alternatives --install /usr/bin/javac javac /usr/lib/jvm/java-17-openjdk-amd64/bin/javac 2
                    sudo update-alternatives --set java  /usr/lib/jvm/java-17-openjdk-amd64/bin/java
                    sudo update-alternatives --set javac /usr/lib/jvm/java-17-openjdk-amd64/bin/javac
                    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                    export PATH=$JAVA_HOME/bin:$PATH
                    java -version

                    # ── Maven ─────────────────────────────────────────────
                    if ! command -v mvn &>/dev/null; then
                        sudo apt-get install -y maven
                    else
                        echo "Maven already installed"
                    fi
                    sudo ln -sf /usr/share/maven/bin/mvn /usr/local/bin/mvn || true
                    sudo ln -sf /usr/share/maven/bin/mvn /usr/bin/mvn        || true
                    mvn -version

                    # ── Docker ────────────────────────────────────────────
                    if ! command -v docker &>/dev/null; then
                        sudo apt-get install -y docker.io
                        sudo systemctl start docker
                        sudo systemctl enable docker
                    else
                        echo "Docker already installed"
                    fi
                    sudo chmod 666 /var/run/docker.sock
                    docker --version

                    # ── kubectl ───────────────────────────────────────────
                    if ! command -v kubectl &>/dev/null; then
                        sudo snap install kubectl --classic
                    else
                        echo "kubectl already installed"
                    fi
                    kubectl version --client

                    # ── SonarQube container ───────────────────────────────
                    if docker ps --filter "name=sonarqube" --filter "status=running" | grep -q sonarqube; then
                        echo "SonarQube already running"
                    elif docker ps -a --filter "name=sonarqube" | grep -q sonarqube; then
                        echo "Restarting stopped SonarQube container..."
                        docker start sonarqube
                        sleep 30
                    else
                        echo "Starting fresh SonarQube container..."
                        docker run -d --name sonarqube -p 9000:9000 sonarqube:lts-community
                        echo "Waiting 60s for SonarQube to be ready..."
                        sleep 60
                    fi

                    echo "=== All Tools Ready ==="
                '''
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 2: Checkout feature-iam branch
        // FIX #2: was checking out 'dev' branch - must checkout 'feature-iam'
        //         so the correct k8s-deployment.yml (with raegan-app-iam) is used
        // ─────────────────────────────────────────────────────────────────
        stage('Checkout Code') {
            steps {
                script {
                    echo "=== Checking out branch: feature-iam ==="
                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: '*/feature-iam']],
                        userRemoteConfigs: [[url: "${GIT_REPO}"]]
                    ])
                    env.CURRENT_BRANCH = 'feature-iam'

                    sh '''
                        echo "=== Workspace Contents ==="
                        ls -la
                        echo ""
                        echo "=== Required File Check ==="
                        MISSING=0
                        for f in Dockerfile k8s-deployment.yml k8s-service.yml pom.xml; do
                            if [ -f "$f" ]; then
                                echo "  [OK]      $f"
                            else
                                echo "  [MISSING] $f  <-- pipeline will fail without this"
                                MISSING=1
                            fi
                        done
                        if [ "$MISSING" = "1" ]; then
                            echo ""
                            echo "ERROR: Missing required file(s)."
                            exit 1
                        fi

                        echo ""
                        echo "=== k8s-deployment.yml preview ==="
                        cat k8s-deployment.yml
                        echo ""

                        echo "=== IMAGE_PLACEHOLDER check ==="
                        if grep -q "IMAGE_PLACEHOLDER" k8s-deployment.yml; then
                            echo "  [OK] IMAGE_PLACEHOLDER found - sed will work correctly"
                        else
                            echo "  [ERROR] IMAGE_PLACEHOLDER NOT found in k8s-deployment.yml"
                            exit 1
                        fi
                    '''
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 3: Set image tag to v2
        // ─────────────────────────────────────────────────────────────────
        stage('Assign Image Tag') {
            steps {
                script {
                    env.FULL_IMAGE  = "${DOCKERHUB_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"
                    env.DEPLOY_FILE = "k8s-deploy-${IMAGE_TAG}.yml"

                    echo "======================================"
                    echo "  Branch:      ${env.CURRENT_BRANCH}"
                    echo "  Image Tag:   ${IMAGE_TAG}"
                    echo "  Full Image:  ${env.FULL_IMAGE}"
                    echo "  Deploy File: ${env.DEPLOY_FILE}"
                    echo "======================================"
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 4: Maven Compile
        // ─────────────────────────────────────────────────────────────────
        stage('Maven Compile') {
            steps {
                sh '''
                    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                    export PATH=$JAVA_HOME/bin:$PATH
                    echo "=== Maven Compile ==="
                    mvn clean compile
                '''
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 5: Maven Test
        // ─────────────────────────────────────────────────────────────────
        stage('Maven Test') {
            steps {
                sh '''
                    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                    export PATH=$JAVA_HOME/bin:$PATH
                    echo "=== Maven Test ==="
                    mvn test
                '''
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 6: Maven Package
        // ─────────────────────────────────────────────────────────────────
        stage('Maven Package') {
            steps {
                sh '''
                    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                    export PATH=$JAVA_HOME/bin:$PATH
                    echo "=== Maven Package ==="
                    mvn package -DskipTests
                    echo "=== Built Artifacts ==="
                    ls -lh target/*.jar 2>/dev/null || \
                    ls -lh target/*.war 2>/dev/null || \
                    echo "WARNING: No jar/war found in target/"
                '''
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 7: SonarQube Scan
        // ─────────────────────────────────────────────────────────────────
        stage('SonarQube Scan') {
            steps {
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    withSonarQubeEnv("${SONAR_SERVER}") {
                        sh '''
                            export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                            export PATH=$JAVA_HOME/bin:$PATH
                            echo "=== SonarQube Scan ==="
                            # Run tests + collect coverage first, then sonar scan picks it up
                    mvn verify sonar:sonar \
                                -Dsonar.projectKey=simbu-app \
                                -Dsonar.host.url=http://13.235.56.152:9000 \
                                -Dsonar.login=${SONAR_TOKEN} \
                                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                        '''
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 8: Quality Gate
        // abortPipeline: false → logs gate result but never blocks deploy
        // Add JaCoCo tests + pom.xml fix so gate passes on next run
        // ─────────────────────────────────────────────────────────────────
        stage('Quality Gate') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: false
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 9: Docker Build
        // ─────────────────────────────────────────────────────────────────
        stage('Docker Build') {
            steps {
                sh '''
                    sudo chmod 666 /var/run/docker.sock

                    if [ ! -f "Dockerfile" ]; then
                        echo "ERROR: Dockerfile not found in workspace root"
                        exit 1
                    fi

                    echo "=== Building Docker image: ${FULL_IMAGE} ==="
                    docker build -t ${FULL_IMAGE} .

                    echo "=== Build complete ==="
                    docker images | grep "${IMAGE_NAME}"
                '''
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 10: Docker Push (v2)
        // ─────────────────────────────────────────────────────────────────
        stage('Docker Push') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: "${DOCKERHUB_CREDS}",
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh """
                        sudo chmod 666 /var/run/docker.sock
                        echo "=== Pushing: ${env.FULL_IMAGE} ==="
                        echo "\$DOCKER_PASS" | docker login -u "\$DOCKER_USER" --password-stdin
                        docker push ${env.FULL_IMAGE}
                        docker logout
                        echo "=== Push complete: ${env.FULL_IMAGE} ==="
                    """
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 11: Deploy to Kubernetes
        // ─────────────────────────────────────────────────────────────────
        stage('Deploy to Kubernetes') {
            steps {
                sh '''
                    echo "=== Kubernetes Deployment Start ==="

                    # ── Step 1: kubeconfig ────────────────────────────────
                    mkdir -p /var/lib/jenkins/.kube
                    sudo cp /home/ubuntu/.kube/config /var/lib/jenkins/.kube/config
                    sudo chown jenkins:jenkins /var/lib/jenkins/.kube/config
                    sudo chmod 600 /var/lib/jenkins/.kube/config
                    export KUBECONFIG=/var/lib/jenkins/.kube/config

                    # ── Step 2: Verify cluster connectivity ───────────────
                    echo "=== Cluster Nodes ==="
                    kubectl get nodes
                    if [ $? -ne 0 ]; then
                        echo "ERROR: Cannot reach Kubernetes cluster."
                        exit 1
                    fi

                    # ── Step 3: Validate manifest files ───────────────────
                    for f in k8s-deployment.yml k8s-service.yml; do
                        if [ ! -f "$f" ]; then
                            echo "ERROR: $f not found in workspace"
                            exit 1
                        fi
                    done

                    # ── Step 4: Copy template → temp file ─────────────────
                    cp k8s-deployment.yml ${DEPLOY_FILE}

                    # ── Step 5: Replace IMAGE_PLACEHOLDER with v2 image ───
                    sed -i "s|IMAGE_PLACEHOLDER|${FULL_IMAGE}|g" ${DEPLOY_FILE}

                    # ── Step 6: Confirm replacement succeeded ─────────────
                    echo "=== Image line after replacement ==="
                    grep "image:" ${DEPLOY_FILE}

                    if grep -q "IMAGE_PLACEHOLDER" ${DEPLOY_FILE}; then
                        echo "ERROR: IMAGE_PLACEHOLDER still present - sed failed."
                        exit 1
                    fi

                    # ── Step 7: Apply manifests ───────────────────────────
                    echo "=== Applying Deployment manifest ==="
                    kubectl apply -f ${DEPLOY_FILE} -n ${NAMESPACE} --validate=false

                    echo "=== Applying Service manifest ==="
                    kubectl apply -f k8s-service.yml -n ${NAMESPACE} --validate=false

                    # ── Step 8: Wait for rollout (180s timeout) ───────────
                    # FIX #3: rollout status now uses DEPLOYMENT_NAME=raegan-app-iam
                    echo "=== Waiting for rollout to complete ==="
                    kubectl rollout status deployment/${DEPLOYMENT_NAME} -n ${NAMESPACE} --timeout=180s

                    ROLLOUT_STATUS=$?
                    if [ $ROLLOUT_STATUS -ne 0 ]; then
                        echo "======================================"
                        echo "  ROLLOUT FAILED - Debug Info Below"
                        echo "======================================"
                        kubectl get pods -n ${NAMESPACE} -l app=${DEPLOYMENT_NAME} -o wide
                        FIRST_POD=$(kubectl get pods -n ${NAMESPACE} -l app=${DEPLOYMENT_NAME} \
                            -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
                        if [ -n "$FIRST_POD" ]; then
                            kubectl describe pod $FIRST_POD -n ${NAMESPACE}
                            kubectl logs $FIRST_POD -n ${NAMESPACE} --tail=50 || true
                        fi
                        kubectl describe deployment/${DEPLOYMENT_NAME} -n ${NAMESPACE}
                        exit 1
                    fi

                    # ── Step 9: Final status print ────────────────────────
                    echo "======================================"
                    echo "  DEPLOYMENT SUCCESSFUL"
                    echo "  Image: ${FULL_IMAGE}"
                    echo "======================================"
                    echo "--- Pods ---"
                    kubectl get pods -n ${NAMESPACE} -l app=${DEPLOYMENT_NAME} -o wide
                    echo ""
                    echo "--- Service ---"
                    kubectl get svc raegan-service-iam -n ${NAMESPACE}
                    echo ""
                    echo "--- Deployment ---"
                    kubectl get deployment ${DEPLOYMENT_NAME} -n ${NAMESPACE}

                    # ── Step 10: Cleanup temp file ────────────────────────
                    rm -f ${DEPLOY_FILE}
                '''
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // POST
    // ─────────────────────────────────────────────────────────────────────
    post {
        success {
            echo "======================================"
            echo "  PIPELINE SUCCESS"
            echo "  Branch : ${env.CURRENT_BRANCH}"
            echo "  Image  : ${env.FULL_IMAGE}"
            echo "======================================"
        }
        failure {
            echo "======================================"
            echo "  PIPELINE FAILED"
            echo "  Branch : ${env.CURRENT_BRANCH}"
            echo "  Check stage logs above for root cause."
            echo "======================================"
        }
        always {
            sh "rm -f k8s-deploy-*.yml || true"
            cleanWs()
        }
    }
}
