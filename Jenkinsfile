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
                        env.IMAGE_TAG = branch
                    }

                    env.FULL_IMAGE = "${DOCKERHUB_USERNAME}/${IMAGE_NAME}:${env.IMAGE_TAG}"
                    echo "=== Branch: ${branch} → Image Tag: ${env.IMAGE_TAG} ==="
                    echo "=== Full Image: ${env.FULL_IMAGE} ==="
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

                    sed -i "s|IMAGE_PLACEHOLDER|${FULL_IMAGE}|g" k8s-deployment.yml

                    kubectl apply -f k8s-deployment.yml --validate=false
                    kubectl apply -f k8s-service.yml --validate=false
                    kubectl rollout status deployment/raegan-app --timeout=120s
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
