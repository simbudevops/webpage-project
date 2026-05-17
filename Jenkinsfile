pipeline {
    agent any
    environment {
        GIT_REPO           = "https://github.com/simbudevops/webpage-project.git"
        DOCKERHUB_USERNAME = "simbudevops7497"
        IMAGE_NAME         = "simbu-app"
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
                    echo "Installing Java 17..."
                    sudo apt-get install -y openjdk-17-jdk
                    sudo update-alternatives --install /usr/bin/java java /usr/lib/jvm/java-17-openjdk-amd64/bin/java 2
                    sudo update-alternatives --set java /usr/lib/jvm/java-17-openjdk-amd64/bin/java
                    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                    export PATH=$JAVA_HOME/bin:$PATH
                    java -version
                    if ! mvn -version 2>/dev/null; then
                        sudo apt-get install -y maven
                    fi
                    sudo ln -sf /usr/share/maven/bin/mvn /usr/local/bin/mvn
                    mvn -version
                    if ! docker --version 2>/dev/null; then
                        sudo apt-get install -y docker.io
                        sudo systemctl start docker
                        sudo systemctl enable docker
                    fi
                    sudo chmod 666 /var/run/docker.sock
                    docker --version
                    if ! kubectl version --client 2>/dev/null; then
                        sudo snap install kubectl --classic
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
                echo '=== Pulling code from GitHub ==='
                git branch: "${env.BRANCH_NAME}", url: "${GIT_REPO}"
            }
        }

        stage('Assign Image Tag') {
            steps {
                script {
                    def branch = env.BRANCH_NAME
                    def versionFile = "branch-versions.txt"
                    def versions = [:]

                    // Read existing versions
                    if (fileExists(versionFile)) {
                        readFile(versionFile).trim().split('\n').each { line ->
                            def parts = line.split('=')
                            if (parts.size() == 2) {
                                versions[parts[0].trim()] = parts[1].trim()
                            }
                        }
                    }

                    // Assign tag
                    if (branch == 'master') {
                        env.IMAGE_TAG = 'latest'
                    } else if (versions.containsKey(branch)) {
                        // Already has a version assigned
                        env.IMAGE_TAG = versions[branch]
                    } else {
                        // New branch — assign next version number
                        def maxVersion = 0
                        versions.each { b, tag ->
                            if (tag.startsWith('v')) {
                                def num = tag.replace('v', '').toInteger()
                                if (num > maxVersion) maxVersion = num
                            }
                        }
                        def nextVersion = "v${maxVersion + 1}"
                        env.IMAGE_TAG = nextVersion
                        versions[branch] = nextVersion

                        // Save back to file and commit
                        def content = versions.collect { b, t -> "${b}=${t}" }.join('\n')
                        writeFile file: versionFile, text: content
                        sh """
                            git config user.email "jenkins@simbu.com"
                            git config user.name "Jenkins"
                            git add ${versionFile}
                            git commit -m "Auto: assign ${nextVersion} to branch ${branch}"
                            git push origin ${branch}
                        """
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
