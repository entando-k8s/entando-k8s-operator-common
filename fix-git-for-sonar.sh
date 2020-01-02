git fetch --all
git checkout origin/$(git name-rev ${PULL_PULL_SHA}| awk '{print $2}') --track
git branch master -D
git fetch origin master:master