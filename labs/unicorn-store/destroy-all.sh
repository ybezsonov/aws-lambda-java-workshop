#bin/sh
pushd infrastructure/cdk

for x in `aws ecr list-images --repository-name unicorn-store-spring --query 'imageIds[*][imageDigest]' --output text`; do aws ecr batch-delete-image --repository-name unicorn-store-spring --image-ids imageDigest=$x; done
for x in `aws ecr list-images --repository-name unicorn-store-spring --query 'imageIds[*][imageDigest]' --output text`; do aws ecr batch-delete-image --repository-name unicorn-store-spring --image-ids imageDigest=$x; done

kubectl delete namespace unicorn-store-spring
flux uninstall --silent

cdk destroy UnicornStoreSpringEKS --force

export GITOPS_USER=unicorn-store-spring-gitops
export GITOPSC_REPO_NAME=unicorn-store-spring-gitops
export CC_POLICY_ARN=$(aws iam list-policies --query 'Policies[?PolicyName==`AWSCodeCommitPowerUser`].{ARN:Arn}' --output text)

aws iam detach-user-policy --user-name $GITOPS_USER --policy-arn $CC_POLICY_ARN
export SSC_ID=$(aws iam list-service-specific-credentials --user-name $GITOPS_USER --query 'ServiceSpecificCredentials[0].ServiceSpecificCredentialId' --output text)
aws iam delete-service-specific-credential --user-name $GITOPS_USER --service-specific-credential-id $SSC_ID
aws iam delete-user --user-name $GITOPS_USER
aws codecommit delete-repository --repository-name $GITOPSC_REPO_NAME

cdk destroy UnicornStoreSpringECS --force
cdk destroy UnicornStoreSpringCI --force
cdk destroy UnicornStoreInfrastructure --force
popd
