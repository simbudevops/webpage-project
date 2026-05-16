pipeline {

    agent any

    environment {
        // Your GitHub repo
        GIT_REPO            = "https://github.com/simbudevops/webpage-project.git"
        GIT_BRANCH          = "master"

        // Your DockerHub username — CHANGE THIS
        DOCKERHUB_USERNAME  = "simbudevops"
        IMAGE_NAME          = "simbu-app"
        IMAGE_TAG           = "1.0"
        FULL_IMAGE          = "${DOCKERHUB_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"

        // Jenkins Credentials ID for DockerHub
        DOCKERHUB_CREDS     = "dockerhub-creds"

        // SonarQube server name (in Jenkins → System → SonarQube)
        SONAR_SERVER        = "SonarQube"
    }

    stages {

        // ── Stage 1: Pull Code from GitHub ────────────────────
        stage('Checkout Code') {
            steps {
                echo '=== Pulling code from GitHub ==='
                git branch: "${GIT_BRANCH}",
                    url: "${GIT_REPO}"
            }
        }

        // ── Stage 2: Maven Clean + Compile ────────────────────
        stage('Maven Compile') {
            steps {
                echo '=== Compiling with Maven ==='
                sh 'mvn clean compile'
            }
        }

        // ── Stage 3: Maven Test ────────────────────────────────
        stage('Maven Test') {
            steps {
                echo '=== Running Tests ==='
                sh 'mvn test'
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        // ── Stage 4: Maven Package (create jar) ───────────────
        stage('Maven Package') {
            steps {
                echo '=== Packaging JAR ==='
                sh 'mvn package -DskipTests'
                sh 'ls -lh target/*.jar'
            }
        }

        // ── Stage 5: SonarQube Scan ───────────────────────────
        stage('SonarQube Scan') {
            steps {
                echo '=== Running SonarQube Code Scan ==='
                withSonarQubeEnv("${SONAR_SERVER}") {
                    sh '''
                        mvn sonar:sonar \
                          -Dsonar.projectKey=simbu-app \
                          -Dsonar.projectName="Simbu App"
                    '''
                }
            }
        }

        // ── Stage 6: Quality Gate ─────────────────────────────
        stage('Quality Gate') {
            steps {
                echo '=== Checking SonarQube Quality Gate ==='
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // ── Stage 7: Docker Build ─────────────────────────────
        stage('Docker Build') {
            steps {
                echo '=== Building Docker Image ==='
                sh "docker build -t ${FULL_IMAGE} ."
                sh "docker images | grep ${IMAGE_NAME}"
            }
        }

        // ── Stage 8: Docker Push to DockerHub ─────────────────
        stage('Docker Push') {
            steps {
                echo '=== Pushing Image to DockerHub ==='
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

        // ── Stage 9: Deploy to Kubernetes ─────────────────────
        stage('Deploy to Kubernetes') {
            steps {
                echo '=== Deploying to Kubernetes ==='
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

    // ── Post Actions ───────────────────────────────────────────
    post {
        success {
            echo '========================================='
            echo '✅ PIPELINE SUCCESS!'
            echo '   App is deployed to Kubernetes!'
            echo '   Access: http://<your-server-ip>:30080'
            echo '========================================='
        }
        failure {
            echo '========================================='
            echo '❌ PIPELINE FAILED!'
            echo '   Check the console output above'
            echo '========================================='
        }
        always {
            cleanWs()
        }
    }
}
