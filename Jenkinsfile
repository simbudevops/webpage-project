// ═══════════════════════════════════════════════════════════════
// JENKINSFILE — Simbu CI/CD Pipeline
// Stages: Git → Maven Build → Test → SonarQube → Docker → K8s
// ═══════════════════════════════════════════════════════════════

pipeline {

    // Run on any available Jenkins agent
    agent any

    // ── Environment Variables ──────────────────────────────────
    environment {
        // Change this to YOUR DockerHub username
        DOCKERHUB_USERNAME = "YOURDOCKERHUBUSERNAME"
        IMAGE_NAME         = "simbu-app"
        IMAGE_TAG          = "1.0"
        FULL_IMAGE         = "${DOCKERHUB_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"

        // DockerHub credentials ID (set this in Jenkins → Credentials)
        DOCKERHUB_CREDS    = "dockerhub-creds"

        // SonarQube server name (set in Jenkins → System → SonarQube)
        SONAR_SERVER       = "SonarQube"
    }

    // ── Tools (configured in Jenkins → Tools) ─────────────────
    tools {
        maven "Maven3"    // Name you gave Maven in Jenkins Tools
        jdk   "JDK17"     // Name you gave JDK in Jenkins Tools
    }

    stages {

        // ── Stage 1: Get Code from Git ─────────────────────────
        stage('Checkout Code') {
            steps {
                echo '=== Pulling code from GitHub ==='
                // Automatically checks out the repo attached to this job
                checkout scm
            }
        }

        // ── Stage 2: Maven Build ───────────────────────────────
        stage('Maven Build') {
            steps {
                echo '=== Building with Maven ==='
                sh 'mvn clean compile'
            }
        }

        // ── Stage 3: Run Tests ─────────────────────────────────
        stage('Maven Test') {
            steps {
                echo '=== Running Unit Tests ==='
                sh 'mvn test'
            }
            post {
                always {
                    // Publish test results in Jenkins UI
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }

        // ── Stage 4: Package (create .jar) ────────────────────
        stage('Maven Package') {
            steps {
                echo '=== Packaging JAR ==='
                sh 'mvn package -DskipTests'
            }
        }

        // ── Stage 5: SonarQube Code Quality Scan ──────────────
        stage('SonarQube Scan') {
            steps {
                echo '=== Running SonarQube Scan ==='
                withSonarQubeEnv("${SONAR_SERVER}") {
                    sh '''
                        mvn sonar:sonar \
                          -Dsonar.projectKey=simbu-app \
                          -Dsonar.projectName="Simbu App"
                    '''
                }
            }
        }

        // ── Stage 6: SonarQube Quality Gate ───────────────────
        stage('Quality Gate') {
            steps {
                echo '=== Waiting for SonarQube Quality Gate ==='
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // ── Stage 7: Build Docker Image ────────────────────────
        stage('Docker Build') {
            steps {
                echo '=== Building Docker Image ==='
                sh "docker build -t ${FULL_IMAGE} ."
            }
        }

        // ── Stage 8: Push to DockerHub ─────────────────────────
        stage('Docker Push') {
            steps {
                echo '=== Pushing to DockerHub ==='
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
        stage('Deploy to K8s') {
            steps {
                echo '=== Deploying to Kubernetes ==='
                sh '''
                    kubectl apply -f k8s-deployment.yml
                    kubectl apply -f k8s-service.yml
                    kubectl rollout status deployment/simbu-app --timeout=120s
                '''
            }
        }

    }

    // ── Post Actions ───────────────────────────────────────────
    post {
        success {
            echo '✅ Pipeline SUCCESS! App deployed to Kubernetes.'
        }
        failure {
            echo '❌ Pipeline FAILED! Check the logs above.'
        }
        always {
            // Clean workspace after build
            cleanWs()
        }
    }
}
