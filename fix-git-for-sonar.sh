git fetch --all
git checkout -b PR-8 ${PULL_PULL_SHA}
git branch master -D
git fetch origin master:master