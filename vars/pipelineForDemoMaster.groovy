def call(env){
    pipeline {
        agent none
        stages {
            stage('Build') {
                agent { label 'Docker' }
                steps {
                    script {
                        sh "docker build ${env.DOCKERFILE_LOCATION} -t ${env.DOCKER_IMAGE}:${env.VERSION}"
                    }
                }
            }
            stage('Deliver') {
                agent { label 'Docker' }
                steps {
                    script {
                        sh "docker push ${env.DOCKER_IMAGE}:${env.VERSION}"
                    }
                }
            }
            stage('Deploy') {
                agent { 
                    docker { 
                        label 'Docker'
                        image 'bitnami/kubectl' 
                        args '--entrypoint=""'
                    }
                }
                steps {
                    script {
                        withCredentials([file(credentialsId: 'kubeconfig', variable: 'kubeconfig')]) {
                            sh "kubectl --kubeconfig ${kubeconfig} apply -f manifests/deployment-${env.VERSION}.yaml"
                        }
                    }
                }
            }
            stage('Do Blue/Green') {
                agent { 
                    docker { 
                        label 'Docker'
                        image 'bitnami/kubectl' 
                        args '--entrypoint=""'
                    }
                }
                steps {
                    script {
                        input message: 'Do you want switch apps?', ok: 'Switch!'
                        def patch = readYaml file: 'manifests/service-patch.yaml'
                        def deployVersion
                        def actualVersion
                        
                        withCredentials([file(credentialsId: 'kubeconfig', variable: 'kubeconfig')]) {
                            actualVersion = readYaml text: sh(script: "kubectl --kubeconfig ${kubeconfig} get svc ${env.SVC_NAME} -o yaml", returnStdout: true)
                            actualVersion = actualVersion.spec.selector.version
                            deployVersion = readYaml text: sh(script: "kubectl --kubeconfig ${kubeconfig} get deployment -l version!=${actualVersion} -o yaml", returnStdout: true)
                        }
                        
                        deployVersion = deployVersion.items[0].metadata.labels.version

                        println deployVersion
                    }
                }
            }
        }
    }
}