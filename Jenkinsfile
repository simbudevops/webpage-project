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
    }
    stages {

        stage('Install Required Tools') {
            steps {
                sh '''
                    echo "=== Checking and Installing Required Tools ==="

                    # Install Java if not found
                    if ! java -version 2>/dev/null; then
                        echo "Installing Java..."
                        sudo apt update -y
                        sudo apt install openjdk-17-jdk -y
                    else
                        echo "✅ Java already installed"
                        java -version
                    fi

                    # Install Maven if not found
                    if ! /usr/share/maven/bin/mvn -version 2>/dev/null; then
                        echo "Installing Maven..."
                        sudo apt install maven -y
                    else
                        echo "✅ Maven already installed"
                    fi
                    /usr/share/maven/bin/mvn -version

                    # Create mvn symlink
                    sudo ln -sf /usr/share/maven/bin/mvn /usr/local/bin/mvn
                    sudo ln -sf /usr/share/maven/bin/mvn /usr/bin/mvn

                    # Install Docker if not found
                    if ! docker --version 2>/dev/null; then
                        echo "Installing Docker..."
                        sudo apt install docker.io -y
                        sudo systemctl start docker
                        sudo systemctl enable docker
                    else
                        echo "✅ Docker already installed"
                        docker --version
                    fi

                    # Fix Docker permission for jenkins user
                    sudo usermod -aG docker jenkins
                    sudo chmod 666 /var/run/docker.sock

                    # Install kubectl if not found
                    if ! kubectl version --client 2>/dev/null; then
                        echo "Installing kubectl..."
                        sudo snap install kubectl --classic
                    else
                        echo "✅ kubectl already installed"
                        kubectl version --client
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
                sh '/usr/share/maven/bin/mvn clean compile'
            }
        }

        stage('Maven Test') {
            steps {
                sh '/usr/share/maven/bin/mvn test'
            }
        }

        stage('Maven Package') {
            steps {
                sh '/usr/share/maven/bin/mvn package -DskipTests'
                sh 'ls -lh target/*.jar'
            }
        }

        stage('SonarQube Scan') {
            steps {
                withSonarQubeEnv("${SONAR_SERVER}") {
                    sh '/usr/share/maven/bin/mvn sonar:sonar -Dsonar.projectKey=simbu-app'
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
                sh "docker build -t ${FULL_IMAGE} ."
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
                        echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                        docker push ${FULL_IMAGE}
                        docker logout
                    """
                }
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                sh 'kubectl apply -f k8s-deployment.yml'
                sh 'kubectl apply -f k8s-service.yml'
                sh 'kubectl rollout status deployment/simbu-app --timeout=120s'
                sh 'kubectl get pods'
                sh 'kubectl get svc'
            }
        }

    }
    post {
        success {
            echo '✅ PIPELINE SUCCESS! Access: http://<your-server-ip>:30080'
        }
        failure {
            echo '❌ PIPELINE FAILED! Check the logs above.'
        }
        always {
            cleanWs()
        }
    }
}
