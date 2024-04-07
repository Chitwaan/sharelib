// This is assuming you're creating a shared library global variable named 'dockerPipeline'
// This script should be placed in the 'vars' directory of your shared library repository.
def serviceDir = [
  'receiver': 'Receiver',
  'storage': 'Storage',
  // Add other services with the correct case as needed
]
def call(String dockerRepoName) {
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
                    sh "pylint --fail-under=5 ${params.SERVICE_NAME}/*.py"
                }
            }
            stage('Security Scan') {
                steps {
                    snykSecurity(
                        snykInstallation: 'snyk', 
                        snykTokenId: 'A01332333', 
                        failOnIssues: true,
                        failOnError: true,
                        targetFile: "${params.SERVICE_NAME}/requirements.txt", 
                        severity: 'low'
                    )
                }
            }
            stage('Package') {
                when {
                    branch 'main'
                }
                steps {
                    withCredentials([string(credentialsId: 'DockerHub', variable: 'TOKEN')]) {
                        sh "docker login -u 'chitwankaur' -p '$TOKEN' docker.io"
                        sh "docker build -t chitwankaur/${DOCKER_IMAGE_NAME} ./${params.SERVICE_NAME}/"
                        sh "docker push chitwankaur/${DOCKER_IMAGE_NAME}"
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
                            sh "ssh -o StrictHostKeyChecking=no ubuntu@your.vm.ip.address 'cd /path/to/deployment && docker-compose pull ${dockerRepoName} && docker-compose up -d ${dockerRepoName}'"
                        }
                    }
                }
            }
        }
    }
}
