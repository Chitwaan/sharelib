// This is assuming you're creating a shared library global variable named 'dockerPipeline'
// This script should be placed in the 'vars' directory of your shared library repository.

def call(String dockerRepoName) {
    def serviceDir = [
        'receiver': 'Receiver',
        'storage': 'Storage',
        // Add other services with the correct case as needed
    ]
    pipeline {
        agent any
        parameters {
            string(name: 'SERVICE_NAME', defaultValue: 'receiver', description: 'Name of the service to operate on: receiver, storage, processing')
            booleanParam(defaultValue: false, description: 'Deploy the Apps', name: 'DEPLOY')
        }
        environment {
            VIRTUAL_ENV = '/opt/venv'
            PATH = "$VIRTUAL_ENV/bin:$PATH"
            DOCKER_IMAGE_NAME = "${dockerRepoName}:${BUILD_NUMBER}"
            }
        stages {
            stage('Setup') {
                steps {
                    sh 'python3 -m venv venv'
                    sh '. venv/bin/activate'
                    sh 'pip install --upgrade pip'
                    sh "pip install -r ${serviceDir[params.SERVICE_NAME]}/requirements.txt"
                }
            }
            stage('Lint') {
                steps {
                    script {
                        // Assuming you are in the root directory of the checked out repository
                        if (sh(returnStatus: true, script: 'test -d ${serviceDir[params.SERVICE_NAME]}')) {
                            sh "pylint --fail-under=5 ${serviceDir[params.SERVICE_NAME]}/*.py"
                        } else {
                            error("The directory '${serviceDir[params.SERVICE_NAME]}' does not exist.")
                        }
                    }
                }
            }
          stage('Security Scan') {
                steps {
                    script {
                        // Activating virtual environment and running safety check
                        // Ensure that the virtual environment activation is successful
                        // and that the requirements.txt path is correctly formed
                        def command = """
                        . venv/bin/activate
                        pip install safety
                        safety check -r ${env.WORKSPACE}/${serviceDir[params.SERVICE_NAME]}/requirements.txt --full-report
                        """
                        sh command
                    }
                }
            }

            
            stage('Package') {
                
                steps {
                    withCredentials([usernamePassword(credentialsId: 'DockerHub', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                        sh "echo '$PASSWORD' | docker login -u '$USERNAME' --password-stdin docker.io"
                        sh "docker build -t $USERNAME/${DOCKER_IMAGE_NAME} ./${params.SERVICE_NAME}/"
                        sh "docker push $USERNAME/${DOCKER_IMAGE_NAME}"
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
                            sh "ssh -o StrictHostKeyChecking=no chitwan@40.76.138.76 'cd Microservices/Deployment && docker-compose pull ${dockerRepoName} && docker-compose up -d ${dockerRepoName}'"
                        }
                    }
                }
            }
        }
    }
}
