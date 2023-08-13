#bin/sh

date
start=`date +%s`

export ECR_URI=$(aws ecr describe-repositories --repository-names unicorn-store-spring | jq --raw-output '.repositories[0].repositoryUri')
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_URI >/dev/null 2>&1

cd ~/environment/unicorn-store-spring
docker buildx build --load -t unicorn-store-spring:latest .
IMAGE_TAG=i$(date +%Y%m%d%H%M%S)
docker tag unicorn-store-spring:latest $ECR_URI:$IMAGE_TAG
docker tag unicorn-store-spring:latest $ECR_URI:latest
docker push $ECR_URI:$IMAGE_TAG
docker push $ECR_URI:latest

flux reconcile image repository unicorn-store-spring
git -C ~/environment/unicorn-store-spring-gitops pull
flux reconcile source git flux-system
flux reconcile kustomization apps
kubectl wait deployment -n unicorn-store-spring unicorn-store-spring --for condition=Available=True --timeout=120s
kubectl -n unicorn-store-spring get all

date
echo Built and deployed in $(~/environment/java-on-aws/labs/unicorn-store/timediff.sh $start $(date +%s))

sleep 2

kubectl -n unicorn-store-spring logs -f deployment/unicorn-store-spring
