#bin/sh

pushd infrastructure/cdk
cdk deploy UnicornStoreSpringCI --outputs-file target/output-ci.json --require-approval never
cd ../../../../..
cd unicorn-store-spring
git add . && git commit -m "initial commit" && git push --set-upstream origin main
popd
