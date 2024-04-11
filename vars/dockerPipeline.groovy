

def call(String dockerRepoName) {
    def serviceDir = [
        'receiver': 'Receiver',
        'storage': 'Storage',
        'processing': 'Processing'
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
                    withCredentials([string(credentialsId: 'dockerHubToken', variable: 'TOKEN')]) {
                        script {
                            def serviceName = params.SERVICE_NAME.toLowerCase()
                            def dockerfilePath = "${serviceDir[serviceName]}/Dockerfile"
                           
                            dir("${serviceDir[serviceName]}") {
                                sh 'echo $TOKEN | docker login --username chitwankaur --password-stdin'
                                sh "docker build -t chitwankaur/mydockerrepo:${BUILD_NUMBER} ."
                                sh "docker push chitwankaur/mydockerrepo:${BUILD_NUMBER}"
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
                            def deployService = params.SERVICE_NAME 
                            sh "ssh -o StrictHostKeyChecking=no chitwan@40.76.138.76 'cd Microservices/Deployment && docker compose pull ${deployService} && docker compose up -d ${deployService}'"
                        }
                    }
                }
            }

        }
    }
}
