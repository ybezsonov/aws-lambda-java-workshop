#bin/sh
date
start=`date +%s`
./setup-infrastructure.sh
./deploy-ci.sh
./deploy-ecs.sh
./deploy-eks.sh
./deploy-gitops.sh
date
end=`date +%s`
runtime=$((end-start))
echo $runtime