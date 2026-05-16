pipeline {
    agent any
    environment {
        GIT_REPO           = "https://github.com/simbudevops/webpage-project.git"
        GIT_BRANCH         = "master"
        DOCKERHUB_USERNAME = "simbudevops7497"
        IMAGE_NAME         = "simbu-app"
        IMAGE_TAG          = "1.0"
        FULL_IMAGE         = "${DOCKERHUB_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"
        DOCKERHUB_CREDS    = "dockerhub-creds"
        SONAR_SERVER       = "SonarQube"
        SONAR_TOKEN        = credentials('sonar-token')
        JAVA_HOME          = "/usr/lib/jvm/java-17-openjdk-amd64"
    }
    stages {

        stage('Install Required Tools') {
            steps {
                sh '''
                    echo "=== Installing Required Tools ==="

                    sudo apt-get update -y

                    # Install Java 17
                    echo "Installing Java 17..."
                    sudo apt-get install -y openjdk-17-jdk

                    # Register and set Java 17 as default
                    sudo update-alternatives --install /usr/bin/java java /usr/lib/jvm/java-17-openjdk-amd64/bin/java 2
                    sudo update-alternatives --install /usr/bin/javac javac /usr/lib/jvm/java-17-openjdk-amd64/bin/javac 2
                    sudo update-alternatives --set java /usr/lib/jvm/java-17-openjdk-amd64/bin/java
                    sudo update-alternatives --set javac /usr/lib/jvm/java-17-openjdk-amd64/bin/javac

                    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                    export PATH=$JAVA_HOME/bin:$PATH
                    java -version

                    # Install Maven
                    if ! mvn -version 2>/dev/null; then
                        echo "Installing Maven..."
                        sudo apt-get install -y maven
                    else
                        echo "Maven already installed"
                    fi
                    sudo ln -sf /usr/share/maven/bin/mvn /usr/local/bin/mvn
                    sudo ln -sf /usr/share/maven/bin/mvn /usr/bin/mvn

                    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                    export PATH=$JAVA_HOME/bin:$PATH
                    mvn -version

                    # Install Docker
                    if ! docker --version 2>/dev/null; then
                        echo "Installing Docker..."
                        sudo apt-get install -y docker.io
                        sudo systemctl start docker
                        sudo systemctl enable docker
                    else
                        echo "Docker already installed"
                    fi
                    sudo chmod 666 /var/run/docker.sock
                    docker --version

                    # Install kubectl
                    if ! kubectl version --client 2>/dev/null; then
                        echo "Installing kubectl..."
                        sudo snap install kubectl --classic
                    else
                        echo "kubectl already installed"
                    fi
                    kubectl version --client

                    # Check SonarQube is running
                    echo "Checking SonarQube..."
                    if docker ps | grep -q sonarqube; then
                        echo "SonarQube already running"
                    else
                        echo "Starting SonarQube..."
                        docker run -d --name sonarqube \
                            -p 9000:9000 \
                            sonarqube:lts-community
                        echo "Waiting for SonarQube to be ready..."
                        sleep 60
                    fi

                    echo "=== All Tools Ready ==="
                '''
            }
        }

        stage('Checkout Code') {
            steps {
                echo '=== Pulling code from GitHub ==='
                git branch: "${GIT_BRANCH}", url: "${GIT_REPO}"
            }
        }

        stage('Maven Compile') {
            steps {
                sh '''
                    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                    export PATH=$JAVA_HOME/bin:$PATH
                    echo "Using JAVA_HOME: $JAVA_HOME"
                    java -version
                    mvn clean compile
                '''
            }
        }

        stage('Maven Test') {
            steps {
                sh '''
                    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                    export PATH=$JAVA_HOME/bin:$PATH
                    echo "Using JAVA_HOME: $JAVA_HOME"
                    mvn test
                '''
            }
        }

        stage('Maven Package') {
            steps {
                sh '''
                    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                    export PATH=$JAVA_HOME/bin:$PATH
                    echo "Using JAVA_HOME: $JAVA_HOME"
                    mvn package -DskipTests
                    ls -lh target/*.jar
                '''
            }
        }

        stage('SonarQube Scan') {
            steps {
                withSonarQubeEnv("${SONAR_SERVER}") {
                    sh '''
                        export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                        export PATH=$JAVA_HOME/bin:$PATH
                        echo "Using JAVA_HOME: $JAVA_HOME"
                        mvn sonar:sonar \
                            -Dsonar.projectKey=simbu-app \
                            -Dsonar.host.url=http://localhost:9000 \
                            -Dsonar.login=${SONAR_TOKEN}
                    '''
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
            environment {
                KUBECONFIG = "/var/lib/jenkins/.kube/config"
            }
            steps {
                sh '''
                    export KUBECONFIG=/var/lib/jenkins/.kube/config
                    kubectl get nodes
                    kubectl apply -f k8s-deployment.yml --validate=false
                    kubectl apply -f k8s-service.yml --validate=false
                    kubectl rollout status deployment/simbu-app --timeout=120s
                    kubectl get pods
                    kubectl get svc
                '''
            }
        }

    }
    post {
        success {
            echo 'PIPELINE SUCCESS! App is live.'
        }
        failure {
            echo 'PIPELINE FAILED! Check the logs above.'
        }
        always {
            node('') {
                cleanWs()
            }
        }
    }
}
