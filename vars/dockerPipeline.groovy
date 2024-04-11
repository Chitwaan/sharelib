def call(String dockerRepoName, String serviceName) {
    // Mapping of service name to the GitHub directory
    def serviceDir = [
        'receiver': 'Microservices/Receiver',
        'storage': 'Microservices/Storage',
        'processing': 'Microservices/Processing'
    ]
    pipeline {
        agent any
        environment {
            VIRTUAL_ENV = '/opt/venv'
            PATH = "$VIRTUAL_ENV/bin:$PATH"
            DOCKER_IMAGE_NAME = "${dockerRepoName}:${serviceName}:${BUILD_NUMBER}"
        }
        stages {
            stage('Setup') {
                steps {
                    script {
                        // Create a Python virtual environment and install dependencies
                        sh 'python3 -m venv ${VIRTUAL_ENV}'
                        sh 'source ${VIRTUAL_ENV}/bin/activate'
                        sh 'pip install --upgrade pip'
                        sh "pip install -r ${WORKSPACE}/${serviceDir[serviceName]}/requirements.txt"
                    }
                }
            }
            stage('Lint') {
                steps {
                    script {
                        // Perform linting with pylint
                        if (sh(returnStatus: true, script: "test -d ${WORKSPACE}/${serviceDir[serviceName]}")) {
                            sh "pylint --fail-under=5 ${WORKSPACE}/${serviceDir[serviceName]}/*.py"
                        } else {
                            error("The directory '${WORKSPACE}/${serviceDir[serviceName]}' does not exist.")
                        }
                    }
                }
            }
            stage('Security Scan') {
                steps {
                    script {
                        // Perform security scanning with safety
                        sh """
                        source ${VIRTUAL_ENV}/bin/activate
                        pip install safety
                        safety check -r ${WORKSPACE}/${serviceDir[serviceName]}/requirements.txt --full-report
                        """
                    }
                }
            }
            stage('Package') {
                steps {
                    withCredentials([string(credentialsId: 'dockerHubToken', variable: 'TOKEN')]) {
                        script {
                            // Build and push the Docker image
                            dir("${WORKSPACE}/${serviceDir[serviceName]}") {
                                sh 'echo $TOKEN | docker login --username chitwankaur --password-stdin'
                                sh "docker build -t ${DOCKER_IMAGE_NAME} ."
                                sh "docker push ${DOCKER_IMAGE_NAME}"
                            }
                        }
                    }
                }
            }
            stage("Deploy") {
                when {
                    expression { params.DEPLOY == true }
                }
                steps {
                    script {
                        // Manually triggered deployment stage
                        sshagent(['deployment']) {
                            sh "ssh -o StrictHostKeyChecking=no chitwan@40.76.138.76 'cd Microservices/Deployment && docker-compose pull ${serviceName} && docker-compose up -d ${serviceName}'"
                        }
                    }
                }
            }
        }
    }
}
