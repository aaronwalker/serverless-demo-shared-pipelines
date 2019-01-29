/***********************************
serverlessPipeline DSL


@Library(['ciinabox', 'pipelines']) _
serverlessPipeline {
  name = 'demo-serverless-python'
  region = 'ap-southeast-2'
  accounts = [
    dev:  '123456789012',
    prod: '210987654321'
  ]
}
************************************/
def call(body) {
  def config= [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  accounts = config.get('accounts', [dev:  env.DEV_ACCOUNT_ID, prod: env.PROD_ACCOUNT_ID])

  pipeline {
    environment {
      AWS_REGION = "${config.get('region', 'eu-central-1')}"
      CIINABOX_ROLE = "${config.get('iamRole', 'ciinabox')}"
      DEV_ACCOUNT_ID = "${accounts.dev}"
      PROD_ACCOUNT_ID = "${accounts.prod}"
    }
    agent {
      docker {
        image "base2/serverless:${config.get('serverlessVersion', 'latest')}"
        reuseNode true
      }
    }
    triggers {
      issueCommentTrigger('.*test this please.*')
    }
    stages {
      stage('Prepare') {
        steps {
          sh 'npm install'
          sh 'python3 -m pip install --user --upgrade pip'
          lookupStage defaultStage: 'dev'
          sh 'printenv'
        }
      }
      stage('unit tests') {
        steps {
          sh 'make test'
        }
        post {
          always{
            junit allowEmptyResults: true, testResults: '**/*/results.xml'
          }
        }
      }
      stage('deploy dev/feature/pr') {
        when {
          anyOf { branch 'develop'; branch 'feature/*'; branch 'PR-*'}
        }
        steps {
          echo "deploy ${env.stage}"
          withIAMRole(env.DEV_ACCOUNT_ID, env.AWS_REGION, env.CIINABOX_ROLE) {
            sh "make deploy CF_ENV=dev STAGE=${env.stage} REGION=${env.AWS_REGION}"
          }
        }
      }
      stage('deploy staging') {
        when {
          anyOf { branch 'master'; }
        }
        steps {
          echo "deploy staging"
          withIAMRole(env.PROD_ACCOUNT_ID, env.AWS_REGION, env.CIINABOX_ROLE) {
            sh "make deploy CF_ENV=staging STAGE=staging REGION=${env.AWS_REGION}"
          }
        }
      }
      stage('Promote To Prod') {
        agent none
        when {
          anyOf { branch 'master'; }
        }
        steps {
          input message: 'Promote to Prod', ok: 'Promote'
          milestone label: 'Prod-Deploy', ordinal: 2
        }
      }
      stage('deploy prod') {
        when {
          anyOf { branch 'master'; }
        }
        steps {
          echo "deploy prod"
          withIAMRole(env.PROD_ACCOUNT_ID, env.AWS_REGION, env.CIINABOX_ROLE) {
            sh "make deploy CF_ENV=prod STAGE=prod REGION=${env.AWS_REGION}"
          }
        }
      }
    }
  }
}