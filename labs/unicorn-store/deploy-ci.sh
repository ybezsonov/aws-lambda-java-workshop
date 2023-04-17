#bin/sh

pushd infrastructure/cdk
cdk deploy UnicornStoreSpringCI --outputs-file target/output-ci.json --require-approval never
cd ../../../../..
url=$(cat aws-lambda-java-workshop/labs/unicorn-store/infrastructure/cdk/target/output-ci.json | jq -r '.UnicornStoreSpringCI.UnicornStoreCodeCommitURL')
echo "${url}"
git clone ${url}
cd "${url##*/}"
git checkout -b main
cd ..
rsync -av aws-lambda-java-workshop/labs/unicorn-store/software/unicorn-store-spring/ "${url##*/}" --exclude target
cp -R aws-lambda-java-workshop/labs/unicorn-store/software/dockerfiles "${url##*/}"
cp -R aws-lambda-java-workshop/labs/unicorn-store/software/maven "${url##*/}"
cd "${url##*/}"
git add . && git commit -m "initial commit" && git push --set-upstream origin main
popd
