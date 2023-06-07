## go to tmp directory
cd /tmp

## Ensure AWS CLI v2 is installed
sudo yum -y remove aws-cli
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip -q awscliv2.zip
sudo ./aws/install
rm awscliv2.zip

## Install Maven
export MVN_VERSION=3.9.2
export MVN_FOLDERNAME=apache-maven-${MVN_VERSION}
export MVN_FILENAME=apache-maven-${MVN_VERSION}-bin.tar.gz
curl -4 -L https://dlcdn.apache.org/maven/maven-3/${MVN_VERSION}/binaries/${MVN_FILENAME} | tar -xvz
sudo mv $MVN_FOLDERNAME /usr/lib/maven
export M2_HOME=/usr/lib/maven
export PATH=${PATH}:${M2_HOME}/bin
sudo ln -s /usr/lib/maven/bin/mvn /usr/local/bin

# Install newer version of AWS SAM CLI
wget -q https://github.com/aws/aws-sam-cli/releases/latest/download/aws-sam-cli-linux-x86_64.zip
unzip -q aws-sam-cli-linux-x86_64.zip -d sam-installation
sudo ./sam-installation/install --update
rm -rf ./sam-installation/
rm ./aws-sam-cli-linux-x86_64.zip

## Install additional dependencies
sudo yum install -y jq
npm install -g aws-cdk --force
npm install -g artillery

curl -Lo copilot https://github.com/aws/copilot-cli/releases/latest/download/copilot-linux
chmod +x copilot
sudo mv copilot /usr/local/bin/copilot
copilot --version

wget https://github.com/mikefarah/yq/releases/download/v4.33.3/yq_linux_amd64.tar.gz -O - |\
  tar xz && sudo mv yq_linux_amd64 /usr/bin/yq
yq --version

## Resize disk
/home/ec2-user/environment/aws-java-workshop/resize-cloud9.sh 30

## Set JDK 11 as default
# sudo update-alternatives --set java /usr/lib/jvm/java-11-amazon-corretto.x86_64/bin/java
# sudo update-alternatives --set javac /usr/lib/jvm/java-11-amazon-corretto.x86_64/bin/javac
# export JAVA_HOME=/usr/lib/jvm/java-11-amazon-corretto.x86_64

## Set JDK 17 as default
sudo yum -y install java-17-amazon-corretto-devel
sudo update-alternatives --set java /usr/lib/jvm/java-17-amazon-corretto.x86_64/bin/java
sudo update-alternatives --set javac /usr/lib/jvm/java-17-amazon-corretto.x86_64/bin/javac
export JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto.x86_64
java -version

## Pre-Download Maven dependencies for Unicorn Store
cd ~/environment/aws-java-workshop/labs/unicorn-store
./mvnw dependency:go-offline -f infrastructure/db-setup/pom.xml
./mvnw dependency:go-offline -f software/alternatives/unicorn-store-basic/pom.xml
./mvnw dependency:go-offline -f software/unicorn-store-spring/pom.xml
./mvnw dependency:go-offline -f software/alternatives/unicorn-store-micronaut/pom.xml

## go to home directory
cd ~/environment
rm -vf ${HOME}/.aws/credentials
export C9_ENV_ID=$(aws cloud9 describe-environments --environment-ids $(aws cloud9 list-environments --output json | jq -r '[.environmentIds[]] | join(" ")') --query "environments[?name == 'java-on-aws-workshop'].id" --output text)
echo "Cloud9 environment ID: $C9_ENV_ID"

aws cloud9 update-environment --environment-id $C9_ENV_ID --managed-credentials-action DISABLE
rm -vf ${HOME}/.aws/credentials
export ACCOUNT_ID=$(aws sts get-caller-identity --output text --query Account)
export AWS_REGION=$(curl -s 169.254.169.254/latest/dynamic/instance-identity/document | jq -r '.region')
echo "export ACCOUNT_ID=${ACCOUNT_ID}" | tee -a ~/.bash_profile
echo "export AWS_REGION=${AWS_REGION}" | tee -a ~/.bash_profile
aws configure set default.region ${AWS_REGION}
aws configure get default.region
test -n "$AWS_REGION" && echo AWS_REGION is "$AWS_REGION" || echo AWS_REGION is not set
aws sts get-caller-identity --query Arn | grep java-on-aws-workshop-admin -q && echo "IAM role is valid" || echo "IAM role is NOT valid"
