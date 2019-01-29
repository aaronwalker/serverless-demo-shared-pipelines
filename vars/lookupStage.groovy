def call(body) {
  def config = body
  defaultStage = config.get('defaultStage', 'dev')
  echo "looking up stage: ${config}"
  if(env.BRANCH_NAME ==~ /PR-.*/) {
    env['stage'] = getStageName(env.CHANGE_BRANCH)
    if(env['stage'] == 'develop') {
      env['stage'] = defaultStage
    }
  } else if(env.BRANCH_NAME ==~ /feature\/.*/) {
    env['stage'] = getStageName(env.BRANCH_NAME)
  } else  {
    env['stage'] = defaultStage
  }
}

@NonCPS
def getStageName(branch) {
  return branch
    .replaceAll('feature/', '')
    .replaceAll('_', '-')
}