def call(String dockerRepoName, String serviceName) {
    def serviceDir = [
        'receiver': 'Receiver',
        'storage': 'Storage',
        'processing': 'Processing'
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
                        echo "${WORKSPACE}/${serviceDir[serviceName]}"
                        sh 'python3 -m venv venv'
                        sh '. venv/bin/activate'
                        sh 'pip install --upgrade pip'
                        sh "pip install -r ${WORKSPACE}/${serviceDir[serviceName]}/requirements.txt"

                        sh "pip install -r ${serviceDir[serviceName]}/requirements.txt"
                    }
                }
            }
            stage('Lint') {
    steps {
        script {
            // Define the absolute path to the Receiver directory
            def receiverPath = "${WORKSPACE}/${serviceDir['receiver']}"

            // Debug: output the absolute path to ensure it's correct
            echo "Checking lint in the directory: ${receiverPath}"

            // Use the absolute path in the test command
            if (sh(returnStatus: true, script: "test -d ${receiverPath}")) {
                // If the directory exists, run pylint on all .py files in the directory
                sh "pylint --fail-under=5 ${receiverPath}/*.py"
            } else {
                // If the directory does not exist, throw an error with the path for clarity
                error("The directory ${receiverPath} does not exist.")
            }
        }
    }
}



            stage('Security Scan') {
                steps {
                    script {
                        def command = """
                        . venv/bin/activate
                        pip install safety
                        safety check -r ${serviceDir[serviceName]}/requirements.txt --full-report
                        """
                        sh command
                    }
                }
            }
            stage('Package') {
                steps {
                    withCredentials([string(credentialsId: 'dockerHubToken', variable: 'TOKEN')]) {
                        script {
                            dir(serviceDir[serviceName]) {
                                sh 'echo $TOKEN | docker login --username chitwankaur --password-stdin'
                                sh "docker build -t ${dockerRepoName}:${serviceName}:${BUILD_NUMBER} ."
                                sh "docker push ${dockerRepoName}:${serviceName}:${BUILD_NUMBER}"
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
                        sshagent(['deployment']) {
                            sh "ssh -o StrictHostKeyChecking=no chitwan@40.76.138.76 'cd Microservices/Deployment && docker compose pull ${serviceName} && docker compose up -d ${serviceName}'"
                        }
                    }
                }
            }
        }
    }
}
