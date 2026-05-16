pipeline {

    agent any

    environment {
        PATH               = "/usr/share/maven/bin:/usr/lib/jvm/java-17-openjdk-amd64/bin:${PATH}"
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

        stage('Checkout Code') {
            steps {
                git branch: "${GIT_BRANCH}", url: "${GIT_REPO}"
            }
        }

        stage('Maven Compile') {
            steps {
                sh 'mvn clean compile'
            }
        }

        stage('Maven Test') {
            steps {
                sh 'mvn test'
            }
        }

        stage('Maven Package') {
            steps {
                sh 'mvn package -DskipTests'
            }
        }

        stage('SonarQube Scan') {
            steps {
                withSonarQubeEnv("${SONAR_SERVER}") {
                    sh 'mvn sonar:sonar -Dsonar.projectKey=simbu-app'
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
