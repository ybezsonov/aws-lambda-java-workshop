#bin/sh

echo $(date '+%Y.%m.%d %H:%M:%S')

cd ~/environment/java-on-aws/labs/unicorn-store/infrastructure/scripts

start_time=`date +%s`
init_time=$start_time
~/environment/java-on-aws/labs/unicorn-store/infrastructure/scripts/timeprint.sh "Started" $start_time 2>&1 | tee >(cat >> /home/ec2-user/setup-timing.log)

## Resize disk
start_time=`date +%s`
~/environment/java-on-aws/labs/unicorn-store/infrastructure/scripts/resize-cloud9.sh 50
~/environment/java-on-aws/labs/unicorn-store/infrastructure/scripts/timeprint.sh "resize-cloud9" $start_time 2>&1 | tee >(cat >> /home/ec2-user/setup-timing.log)

# Setup Cloud9
start_time=`date +%s`
~/environment/java-on-aws/labs/unicorn-store/infrastructure/scripts/setup-cloud9.sh
~/environment/java-on-aws/labs/unicorn-store/infrastructure/scripts/timeprint.sh "setup-cloud9" $start_time 2>&1 | tee >(cat >> /home/ec2-user/setup-timing.log)

start_time=`date +%s`
cd ~/environment/java-on-aws/labs/unicorn-store/infrastructure
# Build the database setup function
./mvnw clean package -f infrastructure/db-setup/pom.xml 1> /dev/null

# Build the unicorn application
./mvnw clean package -f software/unicorn-store-spring/pom.xml 1> /dev/null

# Deploy the infrastructure
pushd infrastructure/cdk

cdk bootstrap
cdk deploy UnicornStoreVpc --require-approval never --outputs-file target/output-vpc.json
cdk deploy UnicornStoreInfrastructure --require-approval never --outputs-file target/output-infra.json
cdk deploy UnicornStoreLambdaApp --require-approval never --outputs-file target/output-lambda.json

# Execute the DB Setup function to create the table
aws lambda invoke --function-name $(cat target/output-infra.json | jq -r '.UnicornStoreInfrastructure.DbSetupArn') /dev/stdout | cat;

popd

# Copy the Spring Boot Java Application source code to your local directory
cd ~/environment
mkdir unicorn-store-spring

rsync -av java-on-aws/labs/unicorn-store/software/unicorn-store-spring/ unicorn-store-spring --exclude target
cp -R java-on-aws/labs/unicorn-store/software/dockerfiles unicorn-store-spring
cp -R java-on-aws/labs/unicorn-store/software/scripts unicorn-store-spring
echo "target" > unicorn-store-spring/.gitignore

# create AWS CodeCommit for Java Sources
aws codecommit create-repository --repository-name unicorn-store-spring --repository-description "Java application sources"

# create Amazon ECR for images
aws ecr create-repository --repository-name unicorn-store-spring

export ECR_URI=$(aws ecr describe-repositories --repository-names unicorn-store-spring | jq --raw-output '.repositories[0].repositoryUri')
echo "export ECR_URI=${ECR_URI}" | tee -a ~/.bash_profile
echo "export ECR_URI=${ECR_URI}" >> ~/.bashrc

# Navigate to the application folder and download dependencies via Maven:
cd ~/environment/unicorn-store-spring
mvn dependency:go-offline -f ./pom.xml 1> /dev/null

# Resolution for ECS Service Unavailable
aws iam create-service-linked-role --aws-service-name ecs.amazonaws.com

~/environment/java-on-aws/labs/unicorn-store/infrastructure/scripts/timeprint.sh "setup-infrastructure" $start_time 2>&1 | tee >(cat >> /home/ec2-user/setup-timing.log)

# additional modules setup
start_time=`date +%s`
cd ~/environment/java-on-aws/labs/unicorn-store
./setup-vpc-env-vars.sh
source ~/.bashrc
./setup-vpc-connector.sh
./setup-vpc-peering.sh
~/environment/java-on-aws/labs/unicorn-store/infrastructure/scripts/timeprint.sh "setup-vpc" $start_time 2>&1 | tee >(cat >> /home/ec2-user/setup-timing.log)
start_time=`date +%s`
./22-deploy-eks-eksctl.sh
~/environment/java-on-aws/labs/unicorn-store/infrastructure/scripts/timeprint.sh "eks" $start_time 2>&1 | tee >(cat >> /home/ec2-user/setup-timing.log)
start_time=`date +%s`
./21-deploy-gitops.sh
~/environment/java-on-aws/labs/unicorn-store/infrastructure/scripts/timeprint.sh "gitops" $start_time 2>&1 | tee >(cat >> /home/ec2-user/setup-timing.log)

~/environment/java-on-aws/labs/unicorn-store/infrastructure/scripts/timeprint.sh "Finished" $init_time 2>&1 | tee >(cat >> /home/ec2-user/setup-timing.log)
