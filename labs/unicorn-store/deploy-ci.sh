#bin/sh

cd infrastructure/cdk
cdk deploy UnicornStoreSpringCI --outputs-file target/output.json --require-approval never
pushd ../../..
url=$(cat aws-lambda-java-workshop/labs/unicorn-store/infrastructure/cdk/target/output.json | jq -r '.UnicornStoreSpringCI.CodeCommitURL')
echo "${url}"
git clone ${url}
cd "${url##*/}"
git checkout -b main
cd ..
rsync -av aws-lambda-java-workshop/labs/unicorn-store/software/unicorn-store-spring/ "${url##*/}" --exclude target
cd "${url##*/}"
git add . && git commit -m "initial commit" && git push --set-upstream origin main
popd
