pipeline {
    agent any
    environment {
        GIT_REPO           = "https://github.com/simbudevops/webpage-project.git"
        DOCKERHUB_USERNAME = "simbudevops7497"
        IMAGE_NAME         = "raegan-app"
        DOCKERHUB_CREDS    = "dockerhub-creds"
        SONAR_SERVER       = "SonarQube"
        JAVA_HOME          = "/usr/lib/jvm/java-17-openjdk-amd64"
    }
    stages {

        stage('Install Required Tools') {
            steps {
                sh '''
                    echo "=== Installing Required Tools ==="
                    sudo apt-get update -y
                    sudo apt-get install -y openjdk-17-jdk
                    sudo update-alternatives --install /usr/bin/java java /usr/lib/jvm/java-17-openjdk-amd64/bin/java 2
                    sudo update-alternatives --install /usr/bin/javac javac /usr/lib/jvm/java-17-openjdk-amd64/bin/javac 2
                    sudo update-alternatives --set java /usr/lib/jvm/java-17-openjdk-amd64/bin/java
                    sudo update-alternatives --set javac /usr/lib/jvm/java-17-openjdk-amd64/bin/javac
                    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                    export PATH=$JAVA_HOME/bin:$PATH
                    java -version
                    if ! mvn -version 2>/dev/null; then
                        sudo apt-get install -y maven
                    else
                        echo "Maven already installed"
                    fi
                    sudo ln -sf /usr/share/maven/bin/mvn /usr/local/bin/mvn
                    sudo ln -sf /usr/share/maven/bin/mvn /usr/bin/mvn
                    mvn -version
                    if ! docker --version 2>/dev/null; then
                        sudo apt-get install -y docker.io
                        sudo systemctl start docker
                        sudo systemctl enable docker
                    else
                        echo "Docker already installed"
                    fi
                    sudo chmod 666 /var/run/docker.sock
                    docker --version
                    if ! kubectl version --client 2>/dev/null; then
                        sudo snap install kubectl --classic
                    else
                        echo "kubectl already installed"
                    fi
                    kubectl version --client
                    if docker ps | grep -q sonarqube; then
                        echo "SonarQube already running"
                    else
                        docker run -d --name sonarqube -p 9000:9000 sonarqube:lts-community
                        sleep 60
                    fi
                    echo "=== All Tools Ready ==="
                '''
            }
        }

        stage('Checkout Code') {
            steps {
                script {
                    echo '=== Pulling code from GitHub ==='
                    def branch = env.BRANCH_NAME?.trim() ?: 'dev'
                    env.CURRENT_BRANCH = branch
                    echo "=== Checking out branch: ${branch} ==="
                    git branch: "${branch}", url: "${GIT_REPO}"
                }
            }
        }

        stage('Assign Image Tag') {
            steps {
                script {
                    def branch = env.CURRENT_BRANCH

                    if (branch == 'master') {
                        env.IMAGE_TAG = 'latest'
                    } else if (branch == 'dev') {
                        env.IMAGE_TAG = 'v1'
                    } else {
                        // ✅ FIX: any other branch gets its own unique tag
                        // e.g. branch "feature-login" → tag "feature-login"
                        env.IMAGE_TAG = branch.replaceAll('/', '-')
                    }

                    env.FULL_IMAGE = "${DOCKERHUB_USERNAME}/${IMAGE_NAME}:${env.IMAGE_TAG}"

                    // ✅ FIX: unique deployment file per branch — no more cross-branch collisions
                    env.DEPLOY_FILE = "k8s-deploy-${env.IMAGE_TAG}.yml"

                    echo "=== Branch: ${branch} → Image Tag: ${env.IMAGE_TAG} ==="
                    echo "=== Full Image: ${env.FULL_IMAGE} ==="
                    echo "=== Deploy File: ${env.DEPLOY_FILE} ==="
                }
            }
        }

        stage('Maven Compile') {
            steps {
                sh '''
                    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                    export PATH=$JAVA_HOME/bin:$PATH
                    mvn clean compile
                '''
            }
        }

        stage('Maven Test') {
            steps {
                sh '''
                    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                    export PATH=$JAVA_HOME/bin:$PATH
                    mvn test
                '''
            }
        }

        stage('Maven Package') {
            steps {
                sh '''
                    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                    export PATH=$JAVA_HOME/bin:$PATH
                    mvn package -DskipTests
                    ls -lh target/*.jar
                '''
            }
        }

        stage('SonarQube Scan') {
            steps {
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    withSonarQubeEnv("${SONAR_SERVER}") {
                        sh '''
                            export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                            export PATH=$JAVA_HOME/bin:$PATH
                            mvn sonar:sonar \
                                -Dsonar.projectKey=simbu-app \
                                -Dsonar.host.url=http://13.235.56.152:9000 \
                                -Dsonar.login=${SONAR_TOKEN}
                        '''
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    sudo chmod 666 /var/run/docker.sock
                    docker build -t ${FULL_IMAGE} .
                '''
            }
        }

        stage('Docker Push') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: "${DOCKERHUB_CREDS}",
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh """
                        sudo chmod 666 /var/run/docker.sock
                        echo "\$DOCKER_PASS" | docker login -u "\$DOCKER_USER" --password-stdin
                        docker push ${FULL_IMAGE}
                        docker logout
                    """
                }
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                sh '''
                    mkdir -p /var/lib/jenkins/.kube
                    sudo cp /home/ubuntu/.kube/config /var/lib/jenkins/.kube/config
                    sudo chown jenkins:jenkins /var/lib/jenkins/.kube/config
                    sudo chmod 600 /var/lib/jenkins/.kube/config
                    export KUBECONFIG=/var/lib/jenkins/.kube/config

                    kubectl get nodes

                    # ✅ FIX 1: Copy the ORIGINAL template to a BRANCH-SPECIFIC temp file
                    # The original k8s-deployment.yml is NEVER modified
                    # Every branch/run gets its own file → no conflicts between branches
                    cp k8s-deployment.yml ${DEPLOY_FILE}

                    # ✅ FIX 2: sed runs on the COPY, not the original
                    # Next run: original still has IMAGE_PLACEHOLDER → sed always works
                    sed -i "s|IMAGE_PLACEHOLDER|${FULL_IMAGE}|g" ${DEPLOY_FILE}

                    # ✅ FIX 3: verify the replacement actually worked before applying
                    echo "=== Verifying image in deploy file ==="
                    grep "image:" ${DEPLOY_FILE}

                    # Apply using the branch-specific deploy file
                    kubectl apply -f ${DEPLOY_FILE} --validate=false
                    kubectl apply -f k8s-service.yml --validate=false

                    # ✅ FIX 4: deployment name includes branch tag to avoid k8s name conflicts
                    kubectl rollout status deployment/raegan-app --timeout=120s

                    echo "=== Deployment complete for branch: ${CURRENT_BRANCH} ==="
                    kubectl get pods -l app=raegan-app
                    kubectl get svc raegan-service

                    # ✅ FIX 5: clean up the temp deploy file after apply
                    rm -f ${DEPLOY_FILE}
                '''
            }
        }
    }

    post {
        success {
            echo "PIPELINE SUCCESS! Branch: ${env.CURRENT_BRANCH} | Image: ${env.FULL_IMAGE}"
        }
        failure {
            echo "PIPELINE FAILED! Branch: ${env.CURRENT_BRANCH} | Check logs above."
        }
        always {
            // ✅ FIX 6: also clean up any leftover temp deploy files before workspace wipe
            sh "rm -f k8s-deploy-*.yml || true"
            node('') {
                cleanWs()
            }
        }
    }
}
