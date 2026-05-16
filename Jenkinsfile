pipeline {
    agent any
    environment {
        GIT_REPO           = "https://github.com/simbudevops/webpage-project.git"
        GIT_BRANCH         = "master"
        DOCKERHUB_USERNAME = "simbudevops"
        IMAGE_NAME         = "simbu-app"
        IMAGE_TAG          = "1.0"
        FULL_IMAGE         = "${DOCKERHUB_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"
        DOCKERHUB_CREDS    = "dockerhub-creds"
        SONAR_SERVER       = "SonarQube"
        JAVA_HOME          = "/usr/lib/jvm/java-11-openjdk-amd64"
    }
    stages {

        stage('Install Required Tools') {
            steps {
                sh '''
                    echo "=== Installing Required Tools ==="

                    sudo apt-get update -y

                    if ! java -version 2>&1 | grep -q "11"; then
                        echo "Installing Java 11..."
                        sudo apt-get install -y openjdk-11-jdk
                    else
                        echo "Java 11 already installed"
                    fi

                    sudo update-alternatives --set java /usr/lib/jvm/java-11-openjdk-amd64/bin/java || true
                    export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
                    export PATH=$JAVA_HOME/bin:$PATH
                    java -version

                    if ! /usr/share/maven/bin/mvn -version 2>/dev/null; then
                        echo "Installing Maven..."
                        sudo apt-get install -y maven
                    else
                        echo "Maven already installed"
                    fi
                    sudo ln -sf /usr/share/maven/bin/mvn /usr/local/bin/mvn
                    sudo ln -sf /usr/share/maven/bin/mvn /usr/bin/mvn
                    /usr/share/maven/bin/mvn -version

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

                    if ! kubectl version --client 2>/dev/null; then
                        echo "Installing kubectl..."
                        sudo snap install kubectl --classic
                    else
                        echo "kubectl already installed"
                    fi
                    kubectl version --client

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
                    export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
                    export PATH=$JAVA_HOME/bin:$PATH
                    /usr/share/maven/bin/mvn clean compile
                '''
            }
        }

        stage('Maven Test') {
            steps {
                sh '''
                    export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
                    export PATH=$JAVA_HOME/bin:$PATH
                    /usr/share/maven/bin/mvn test
                '''
            }
        }

        stage('Maven Package') {
            steps {
                sh '''
                    export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
                    export PATH=$JAVA_HOME/bin:$PATH
                    /usr/share/maven/bin/mvn package -DskipTests
                    ls -lh target/*.jar
                '''
            }
        }

        stage('SonarQube Scan') {
            steps {
                withSonarQubeEnv("${SONAR_SERVER}") {
                    sh '''
                        export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
                        export PATH=$JAVA_HOME/bin:$PATH
                        /usr/share/maven/bin/mvn sonar:sonar -Dsonar.projectKey=simbu-app
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
            steps {
                sh '''
                    kubectl apply -f k8s-deployment.yml
                    kubectl apply -f k8s-service.yml
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
            cleanWs()
        }
    }
}
