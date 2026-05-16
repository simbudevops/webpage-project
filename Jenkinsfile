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

                    # Update apt
                    sudo apt-get update -y

                    # Install Java 11
                    if ! java -version 2>&1 | grep -q "11"; then
                        echo "Installing Java 11..."
                        sudo apt-get install -y openjdk-11-jdk
                    else
                        echo "✅ Java 11 already installed"
                    fi

                    # Set Java 11 as default
                    sudo update-alternatives --set java /usr/lib/jvm/java-11-openjdk-amd64/bin/java || true
                    export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
                    export PATH=$JAVA_HOME/bin:$PATH
                    java -version

                    # Install Maven
                    if ! /usr/share/maven/bin/mvn -version 2>/dev/null; then
                        echo "Installing Maven..."
                        sudo apt-get install -y maven
                    else
                        echo "✅ Maven already installed"
                    fi
                    sudo ln -sf /usr/share/maven/bin/mvn /usr/local/bin/mvn
                    sudo ln -sf /usr/share/maven/bin/mvn /usr/bin/mvn
                    /usr/share/maven/bin/mvn -version

                    # Install Docker
                    if ! docker --version 2>/dev/null; then
                        echo "Installing Docker..."
                        sudo apt-get install -y docker.io
                        sudo systemctl start docker
                        sudo systemctl enable docker
                    else
                        echo "✅ Docker already installed"
                    fi
                    sudo chmod 666 /var/run/docker.sock
                    docker --version

                    # Install kubectl
                    if ! kubectl version --client 2>/dev/null; then
                        echo "Installing kubectl..."
                        sudo snap install kubectl --classic
                    else
                        echo "✅ kubectl already installed"
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
