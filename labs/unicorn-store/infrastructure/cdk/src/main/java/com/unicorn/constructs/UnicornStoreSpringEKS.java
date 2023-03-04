package com.unicorn.constructs;

import com.unicorn.core.InfrastructureStack;
import software.amazon.awscdk.services.eks.Cluster;
import software.amazon.awscdk.services.eks.KubernetesVersion;
import software.amazon.awscdk.services.eks.ClusterLoggingTypes;
import software.amazon.awscdk.services.eks.FargateProfile;
import software.amazon.awscdk.services.eks.FargateProfileOptions;
import software.amazon.awscdk.services.eks.HelmChartOptions;
import software.amazon.awscdk.services.eks.AlbControllerOptions;
import software.amazon.awscdk.services.eks.NodegroupOptions;
import software.amazon.awscdk.services.eks.Selector;
import software.amazon.awscdk.services.eks.ServiceAccount;
import software.amazon.awscdk.services.eks.ServiceAccountOptions;
import software.amazon.awscdk.services.eks.NodegroupAmiType;
import software.amazon.awscdk.services.eks.AlbControllerVersion;
import software.amazon.awscdk.services.eks.AwsAuthMapping;
import software.amazon.awscdk.services.eks.CapacityType;
import software.amazon.awscdk.services.eks.KubernetesManifest;
import software.amazon.awscdk.services.eks.KubernetesObjectValue;
import software.amazon.awscdk.CfnOutputProps;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.iam.IRole;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.FromRoleArnOptions;
// import software.amazon.awscdk.services.lambda.Code;
// import software.amazon.awscdk.services.lambda.Function;
// import software.amazon.awscdk.services.lambda.Runtime;
// import software.amazon.awscdk.services.events.EventPattern;
// import software.amazon.awscdk.services.events.Rule;
// import software.amazon.awscdk.services.events.targets.LambdaFunction;
import software.amazon.awscdk.cdk.lambdalayer.kubectl.v24.KubectlV24Layer;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.codebuild.PipelineProject;
import software.amazon.awscdk.services.codebuild.BuildSpec;
import software.amazon.awscdk.services.codebuild.ComputeType;
import software.amazon.awscdk.services.codebuild.LinuxBuildImage;
import software.amazon.awscdk.services.codebuild.BuildEnvironment;
import software.amazon.awscdk.services.codebuild.BuildEnvironmentVariable;
import software.amazon.awscdk.services.codepipeline.Pipeline;
import software.amazon.awscdk.services.codepipeline.Artifact;
import software.amazon.awscdk.services.codepipeline.StageProps;
import software.amazon.awscdk.services.codepipeline.actions.CodeBuildAction;
import software.amazon.awscdk.services.codepipeline.actions.EcrSourceAction;

import software.constructs.Construct;

import java.util.List;
import java.util.Arrays;
import java.util.Map;
//import java.util.HashMap;

import org.cdk8s.ApiObjectMetadata;
import org.cdk8s.App;
import org.cdk8s.Chart;
import org.cdk8s.plus24.ConfigMap;
import org.cdk8s.plus24.Namespace;

public class UnicornStoreSpringEKS extends Construct {

    public UnicornStoreSpringEKS(final Construct scope, final String id, InfrastructureStack infrastructureStack) {
            super(scope, id);

        final String projectName = "unicorn-store-spring";

        IRole adminRole = Role.fromRoleArn(scope, projectName + "-admin-role",
            "arn:aws:iam::" + infrastructureStack.getAccount() + ":role/Admin",
            FromRoleArnOptions.builder().mutable(false)
            .build());
        IRole workshopAdminRole = Role.fromRoleArn(scope, projectName + "-workshop-admin-role",
            "arn:aws:iam::" + infrastructureStack.getAccount() + ":role/workshop-admin",
            FromRoleArnOptions.builder().mutable(false)
            .build());

        // Create the EKS cluster
        var cluster = Cluster.Builder.create(scope, projectName + "-cluster").clusterName(projectName)
            .clusterName(projectName + "-cluster")
            .vpc(infrastructureStack.getVpc())
            .vpcSubnets(List.of(SubnetSelection.builder().subnetType(SubnetType.PRIVATE_WITH_EGRESS).build()))
            .clusterLogging(Arrays.asList(ClusterLoggingTypes.API,
                ClusterLoggingTypes.AUDIT,
                ClusterLoggingTypes.AUTHENTICATOR,
                ClusterLoggingTypes.CONTROLLER_MANAGER,
                ClusterLoggingTypes.SCHEDULER))
            .version(KubernetesVersion.of("1.24"))
            .kubectlLayer(new KubectlV24Layer(scope, projectName + "-cluster-kubectl-layer"))
            .albController(AlbControllerOptions.builder()
                .version(AlbControllerVersion.V2_4_1)
                .build())
            .defaultCapacity(0)
            .defaultCapacityInstance(InstanceType.of(InstanceClass.M5, InstanceSize.LARGE))
            .build();

        cluster.getAwsAuth().addRoleMapping(adminRole, AwsAuthMapping.builder().groups(List.of("system:masters")).build());
        cluster.getAwsAuth().addRoleMapping(workshopAdminRole, AwsAuthMapping.builder().groups(List.of("system:masters")).build());

        cluster.addNodegroupCapacity("managed-node-group", NodegroupOptions.builder()
            .nodegroupName("managed-node-group")
            .capacityType(CapacityType.ON_DEMAND)
            .instanceTypes(List.of(new InstanceType("m5.large")))
            .minSize(1)
            .desiredSize(1)
            .maxSize(4)
            .amiType(NodegroupAmiType.AL2_X86_64)
            .build());

        FargateProfile fargateProfile = cluster.addFargateProfile(projectName + "-fargate-profile", FargateProfileOptions.builder()
            .selectors(List.of(Selector.builder().namespace(projectName + "*").build()))
            .fargateProfileName(projectName + "-fargate-profile")
            .vpc(infrastructureStack.getVpc())
            .build());

        // o11y
        // Logging for Fargate
        // https://docs.aws.amazon.com/eks/latest/userguide/fargate-logging.html
        PolicyStatement executionRolePolicy = PolicyStatement.Builder.create()
            .effect(Effect.ALLOW)
            //.principals(List.of(new AnyPrincipal()))
            .actions(List.of(
    			"logs:CreateLogStream",
    			"logs:CreateLogGroup",
    			"logs:DescribeLogStreams",
    			"logs:PutLogEvents"))
            .resources(List.of("*"))
            .build();

        fargateProfile.getPodExecutionRole().addToPrincipalPolicy(executionRolePolicy);

        // Map<String, Object> o11yNamespace = Map.of(
        //     "apiVersion", "v1",
        //     "kind", "Namespace",
        //     "metadata", Map.of(
        //         "name", "aws-observability",
        //         "labels", Map.of(
        //             "aws-observability", "enabled")));

        // KubernetesManifest o11yManifestNamespace = KubernetesManifest.Builder.create(scope, projectName + "-o11y-manifest-ns")
        //     .cluster(cluster)
        //     .manifest(List.of(o11yNamespace))
        //     .build();

        // o11yManifestConfigMap.getNode().addDependency(o11yManifestNamespace);

        // String newLine = System.getProperty("line.separator");
        // Map<String, Object> o11yConfigMap = Map.of(
        //     "apiVersion", "v1",
        //     "kind", "ConfigMap",
        //     "metadata", Map.of(
        //         "name", "aws-logging",
        //         "namespace", "aws-observability"),
        //     "data", Map.of(
        //         "flb_log_cw", "false",
        //         "filters.conf", String.join(newLine,
        //           "[FILTER]",
        //           "    Name parser",
        //           "    Match *",
        //           "    Key_name log",
        //           "    Parser crio",
        //           "[FILTER]",
        //           "    Name kubernetes",
        //           "    Match kube.*",
        //           "    Merge_Log On",
        //           "    Keep_Log Off",
        //           "    Buffer_Size 0",
        //           "    Kube_Meta_Cache_TTL 300s"),
        //         "output.conf", String.join(newLine,
        //           "[OUTPUT]",
        //           "    Name cloudwatch_logs",
        //           "    Match kube.*",
        //           "    region " + infrastructureStack.getRegion(),
        //           "    log_group_name /aws/eks/" + projectName + "-cluster/" + fargateProfile.getFargateProfileName(),
        //           "    log_stream_prefix from-fluent-bit-",
        //           "    log_retention_days 60",
        //           "    auto_create_group true"),
        //         "parsers.conf", String.join(newLine,
        //           "[PARSER]",
        //           "    Name crio",
        //           "    Format Regex",
        //           "    Regex ^(?<time>[^ ]+) (?<stream>stdout|stderr) (?<logtag>P|F) (?<log>.*)$",
        //           "    Time_Key    time",
        //           "    Time_Format %Y-%m-%dT%H:%M:%S.%L%z")));

        // KubernetesManifest o11yManifestConfigMap = KubernetesManifest.Builder.create(scope, projectName + "-o11y-manifest-cm")
        //     .cluster(cluster)
        //     .manifest(List.of(o11yConfigMap))
        //     .build();

        // o11yManifestConfigMap.getNode().addDependency(o11yManifestNamespace);

        App cdk8sApp = new App();
        Chart o11yChart = new Chart(cdk8sApp, "o11y-chart");

        Namespace o11yNamespace = Namespace.Builder.create(o11yChart, "aws-observability")
            .metadata(ApiObjectMetadata.builder()
                .name("aws-observability")
                .labels(Map.of(
                    "aws-observability", "enabled"
                    )
                )
                .build())
            .build();

        String newLine = System.getProperty("line.separator");
        ConfigMap o11yConfigMap = ConfigMap.Builder.create(o11yChart, "aws-logging")
            .metadata(ApiObjectMetadata.builder()
                .name("aws-logging")
                .namespace("aws-observability")
                .build())
            .data(Map.of(
                "flb_log_cw", "false",
                "filters.conf", String.join(newLine,
                  "[FILTER]",
                  "    Name parser",
                  "    Match *",
                  "    Key_name log",
                  "    Parser crio",
                  "[FILTER]",
                  "    Name kubernetes",
                  "    Match kube.*",
                  "    Merge_Log On",
                  "    Keep_Log Off",
                  "    Buffer_Size 0",
                  "    Kube_Meta_Cache_TTL 300s"),
                "output.conf", String.join(newLine,
                  "[OUTPUT]",
                  "    Name cloudwatch_logs",
                  "    Match kube.*",
                  "    region " + infrastructureStack.getRegion(),
                  "    log_group_name /aws/eks/" + projectName + "-cluster/" + fargateProfile.getFargateProfileName(),
                  "    log_stream_prefix from-fluent-bit-",
                  "    log_retention_days 60",
                  "    auto_create_group true"),
                "parsers.conf", String.join(newLine,
                  "[PARSER]",
                  "    Name crio",
                  "    Format Regex",
                  "    Regex ^(?<time>[^ ]+) (?<stream>stdout|stderr) (?<logtag>P|F) (?<log>.*)$",
                  "    Time_Key    time",
                  "    Time_Format %Y-%m-%dT%H:%M:%S.%L%z")))
            .build();

        o11yConfigMap.getNode().addDependency(o11yNamespace);

        cluster.addCdk8sChart("o11y-chart", o11yChart);

        // App
        Map<String, Object> appNamespace = Map.of(
            "apiVersion", "v1",
            "kind", "Namespace",
            "metadata", Map.of(
                "name", projectName,
                "labels", Map.of(
                    "app", projectName)));

        KubernetesManifest appManifestNamespace = KubernetesManifest.Builder.create(scope, projectName + "-app-manifest-ns")
            .cluster(cluster)
            .manifest(List.of(appNamespace))
            .build();

        ServiceAccountOptions appServiceAccountOptions = ServiceAccountOptions.builder()
            .namespace(projectName)
            .name(projectName + "-sa")
            .build();
        ServiceAccount appServiceAccount = cluster.addServiceAccount(projectName + "-app-sa", appServiceAccountOptions);
        appServiceAccount.getNode().addDependency(appManifestNamespace);

        infrastructureStack.getEventBridge().grantPutEventsTo(appServiceAccount);

        // Using AWS SecretManager with External Secret Operator
        // https://aws.amazon.com/blogs/containers/leverage-aws-secrets-stores-from-eks-fargate-with-external-secrets-operator/

        infrastructureStack.getDatabaseSecret().grantRead(appServiceAccount);

        cluster.addHelmChart("external-secrets-operator", HelmChartOptions.builder()
            .repository("https://charts.external-secrets.io")
            .chart("external-secrets")
            .release("external-secrets")
            .namespace("external-secrets")
            .createNamespace(true)
            .values(Map.of(
                "installCRDs", true,
                "webhook.port", 9443))
            .wait(true)
            .build());

        Map<String, Object> secretStore = Map.of(
            "apiVersion", "external-secrets.io/v1beta1",
            "kind", "SecretStore",
            "metadata", Map.of(
                "name", projectName + "-secret-store",
                "namespace", projectName),
            "spec", Map.of(
                "provider", Map.of(
                    "aws", Map.of(
                        "service", "SecretsManager",
                        "region", infrastructureStack.getRegion(),
                        "auth", Map.of(
                            "jwt", Map.of(
                                "serviceAccountRef", Map.of(
                                    "name", projectName + "-sa"
                                )
                            )
                        )
                    )
                )
            )
        );
        KubernetesManifest secretStoreManifest = KubernetesManifest.Builder.create(scope,
            projectName + "-manifest-secret-store")
            .cluster(cluster)
            .manifest(List.of(secretStore))
            .build();
        secretStoreManifest.getNode().addDependency(appServiceAccount);

        Map<String, Object> externalSecret = Map.of(
            "apiVersion", "external-secrets.io/v1beta1",
            "kind", "ExternalSecret",
            "metadata", Map.of(
                "name", projectName + "-external-secret",
                "namespace", projectName),
            "spec", Map.of(
                "refreshInterval", "1h",
                "secretStoreRef", Map.of(
                    "name", projectName + "-secret-store",
                    "kind", "SecretStore"
                ),
                "target", Map.of(
                    "name", infrastructureStack.getDatabaseSecretName(),
                    "creationPolicy", "Owner"
                ),
                "data", List.of(Map.of(
                    "secretKey", infrastructureStack.getDatabaseSecretKey(),
                    "remoteRef", Map.of(
                        "key", infrastructureStack.getDatabaseSecretName(),
                        "property", infrastructureStack.getDatabaseSecretKey()
                        )
                    )
                )
            )
        );

        KubernetesManifest externalSecretManifest = KubernetesManifest.Builder.create(scope,
            projectName + "-manifest-external-secret")
            .cluster(cluster)
            .manifest(List.of(externalSecret))
            .build();
        externalSecretManifest.getNode().addDependency(secretStoreManifest);

        // https://aws.amazon.com/blogs/opensource/migrating-x-ray-tracing-to-aws-distro-for-opentelemetry/
        PolicyStatement AWSOpenTelemetryPolicy = PolicyStatement.Builder.create()
            .effect(Effect.ALLOW)
            //.principals(List.of(new AnyPrincipal()))
            .actions(List.of(
                "logs:PutLogEvents",
                "logs:CreateLogGroup",
                "logs:CreateLogStream",
                "logs:DescribeLogStreams",
                "logs:DescribeLogGroups",
                "logs:PutRetentionPolicy",
                "xray:PutTraceSegments",
                "xray:PutTelemetryRecords",
                "xray:GetSamplingRules",
                "xray:GetSamplingTargets",
                "xray:GetSamplingStatisticSummaries",
                "cloudwatch:PutMetricData",
                "ssm:GetParameters"))
            .resources(List.of("*"))
            .build();

        appServiceAccount.getGrantPrincipal().addToPrincipalPolicy(AWSOpenTelemetryPolicy);

        Map<String, Object> appDeployment = Map.of(
            "apiVersion", "apps/v1",
            "kind", "Deployment",
            "metadata", Map.of(
                "name", projectName,
                "namespace", projectName,
                "labels", Map.of(
                    "app", projectName)),
            "spec", Map.of(
                "replicas", 1,
                "selector", Map.of(
                    "matchLabels", Map.of(
                        "app", projectName)),
                "template", Map.of(
                    "metadata", Map.of(
                        "labels", Map.of(
                            "app", projectName)),
                    "spec", Map.of(
                        "serviceAccountName", appServiceAccount.getServiceAccountName(),
                        "containers", List.of(Map.of(
                            "resources", Map.of(
                                "requests", Map.of(
                                    "cpu", "1",
                                    "memory", "2Gi")),
                            "name", projectName,
                            "image", infrastructureStack.getAccount()
                                    + ".dkr.ecr."
                                    + infrastructureStack.getRegion()
                                    + ".amazonaws.com/"
                                    + projectName
                                    + ":latest",
                            "env", List.of(Map.of(
                                "name", "SPRING_DATASOURCE_PASSWORD",
                                "valueFrom", Map.of(
                                    "secretKeyRef", Map.of(
                                        "name", infrastructureStack.getDatabaseSecretName(),
                                        "key", infrastructureStack.getDatabaseSecretKey(),
                                        "optional", false
                                        )
                                    )
                                ), Map.of(
                                "name", "SPRING_DATASOURCE_URL",
                                "value", infrastructureStack.getDatabaseJDBCConnectionString()
                                ), Map.of(
                                "name", "SPRING_DATASOURCE_HIKARI_maximumPoolSize",
                                "value", "1"
                                ), Map.of(
                                "name", "OTEL_OTLP_ENDPOINT",
                                "value", "localhost:4317"
                                ), Map.of(
                                "name", "OTEL_RESOURCE_ATTRIBUTES",
                                "value", "service.namespace=AWSObservability,service.name=unicorn-store-spring"
                                ), Map.of(
                                "name", "S3_REGION",
                                "value", infrastructureStack.getRegion()
                                ), Map.of(
                                "name", "OTEL_METRICS_EXPORTER",
                                "value", "otlp"
                                )
                            ),
                            "ports", List.of(Map.of(
                                "containerPort", 80))
                        ), Map.of(
                            "name", projectName + "-otel",
                            "image", "amazon/aws-otel-collector:latest"
                            )
                        )
                    )
                )
            )
        );

        KubernetesManifest appManifestDeployment = KubernetesManifest.Builder.create(scope,
            projectName + "-manifest-app-deployment")
            .cluster(cluster)
            .manifest(List.of(appDeployment))
            .build();
        appManifestDeployment.getNode().addDependency(appManifestNamespace);

        Map<String, Object> appService = Map.of(
            "apiVersion", "v1",
            "kind", "Service",
            "metadata", Map.of(
                "name", projectName,
                "namespace", projectName),
            "spec", Map.of(
                "type", "LoadBalancer",
                "selector", Map.of(
                    "app", projectName),
                 "ports", List.of(Map.of(
                    "port", 80,
                    "targetPort", 80,
                    "protocol", "TCP"
                    )
                )
            )
        );

        KubernetesManifest appManifestService = KubernetesManifest.Builder.create(scope,
            projectName + "-manifest-app-service")
            .cluster(cluster)
            .manifest(List.of(appService))
            .build();
        appManifestService.getNode().addDependency(appManifestDeployment);
        appManifestService.getNode().addDependency(cluster.getAlbController());

        // query the load balancer address
        KubernetesObjectValue appServiceAddress = KubernetesObjectValue.Builder.create(scope, projectName + "-loadbalancer-attribute")
            .cluster(cluster)
            .objectType("service")
            .objectNamespace(projectName)
            .objectName(projectName)
            .jsonPath(".status.loadBalancer.ingress[0].hostname")
            .build();
        new CfnOutput(scope, "UnicornStoreURL", CfnOutputProps.builder()
            .value("http://" + appServiceAddress.getValue())
            .build());

        // deployment construct which listens to ECR events, then deploys to the existing service.
        IRepository ecr = Repository.fromRepositoryName(scope, projectName + "-ecr", projectName);

        Artifact sourceOuput = Artifact.artifact(projectName + "-ecr-artifact");

        EcrSourceAction sourceAction = EcrSourceAction.Builder.create()
            .actionName("ECR_Source")
            .repository(ecr)
            .imageTag("latest")
            .output(sourceOuput)
            .variablesNamespace("ecrvars")
            .build();

        PipelineProject codeBuild = PipelineProject.Builder.create(scope, projectName + "-codebuild-eks-deploy")
            .projectName(projectName + "-eks-deploy")
            .vpc(infrastructureStack.getVpc())
            .environment(BuildEnvironment.builder()
                .privileged(true)
                .computeType(ComputeType.SMALL)
                .buildImage(LinuxBuildImage.AMAZON_LINUX_2_4)
                .build())
            .buildSpec(BuildSpec.fromObject(Map.of(
                "version", "0.2",
                "phases", Map.of(
                    "build", Map.of(
                        "commands", List.of(
                            "cat imageDetail.json",
                            "IMAGE_DETAIL_URI=$(cat imageDetail.json | python -c \"import sys, json; print(json.load(sys.stdin)['ImageURI'].split('@')[0])\")",
                            "IMAGE_DETAIL_TAG=$(cat imageDetail.json | python -c \"import sys, json; a=json.load(sys.stdin)['ImageTags']; a.sort(); print(a[0])\")",
                            "echo $IMAGE_DETAIL_URI:$IMAGE_DETAIL_TAG",
                            "echo IMAGE_URI=$IMAGE_URI",
                            "echo IMAGE_TAG=$IMAGE_TAG",
                            "echo \"spec:\">> patch.yml",
                            "echo \"  template:\">> patch.yml",
                            "echo \"    spec:\">> patch.yml",
                            "echo \"      containers:\">> patch.yml",
                            "echo \"      - name: unicorn-store-spring\">> patch.yml",
                            "echo \"        image: $IMAGE_DETAIL_URI:$IMAGE_DETAIL_TAG\">> patch.yml",
                            "cat patch.yml",
                            "aws eks update-kubeconfig --name unicorn-store-spring-cluster --region eu-west-1 --role-arn " + workshopAdminRole.getRoleArn(),
                            "kubectl -n " + projectName + " patch deployment " + projectName + " --patch-file patch.yml"
                        )
                    )
                )
            )))
            .environmentVariables(Map.of(
                "IMAGE_URI", BuildEnvironmentVariable.builder()
                    .value(sourceAction.getVariables().getImageUri())
                    .build(),
                "IMAGE_TAG", BuildEnvironmentVariable.builder()
                    .value(sourceAction.getVariables().getImageTag())
                    .build()
            ))
            .timeout(Duration.minutes(60))
            .build();

        PolicyStatement EksRoPolicy = PolicyStatement.Builder.create()
            .effect(Effect.ALLOW)
            .actions(List.of(
                "eks:DescribeNodegroup",
                "eks:DescribeUpdate",
                "eks:DescribeCluster"))
            .resources(List.of("*"))
            .build();

        PolicyStatement CodeBuildSTSPolicy = PolicyStatement.Builder.create()
            .effect(Effect.ALLOW)
            .actions(List.of(
                "sts:AssumeRole"))
            .resources(List.of(workshopAdminRole.getRoleArn()))
            .build();

        codeBuild.addToRolePolicy(EksRoPolicy);
        codeBuild.addToRolePolicy(CodeBuildSTSPolicy);
        workshopAdminRole.grantAssumeRole(codeBuild.getGrantPrincipal());

        Pipeline.Builder.create(scope, projectName +  "-pipeline-eks-deploy")
            .pipelineName(projectName + "-eks-deploy")
            .crossAccountKeys(false)
            .stages(List.of(
                StageProps.builder()
                    .stageName("Source")
                    .actions(List.of(
                            sourceAction
                        )
                    )
                .build(),
                StageProps.builder()
                    .stageName("Deploy")
                    .actions(List.of(
                        CodeBuildAction.Builder.create()
                            .actionName("CodeBuild_deploy")
                            .input(sourceOuput)
                            .project(codeBuild)
                            .runOrder(1)
                            .build()
                        )
                    )
                .build()
                )
            )
            .build();

        // CD to EKS using event from ECR and Lambda to change image in App deployment
        // Function fn = Function.Builder.create(scope, projectName + "-deploy-lambda")
        //     .runtime(Runtime.JAVA_11)
        //     .functionName(projectName + "-deploy-lambda")
        //     .memorySize(1024)
        //     .timeout(Duration.seconds(29))
        //     .code(Code.fromAsset("../eks-lambda/target/eks-lambda.jar"))
        //     .handler("com.amazon.aws.DeployHandler::handleRequest")
        //     .layers(List.of(new KubectlV24Layer(scope, projectName + "-deploy-lambda-kubectl-layer")))
        //     .vpc(infrastructureStack.getVpc())
        //     .securityGroups(List.of(infrastructureStack.getApplicationSecurityGroup()))
        //     // .environment(new HashMap<>() {{
        //     //     put("KUBECONFIG", cluster.kube;
        //     //     put("DATASOURCES_DEFAULT_URL", infrastructureStack.getDatabaseJDBCConnectionString());
        //     //     put("DATASOURCES_DEFAULT_maxPoolSize", "1");
        //     //     put("JAVA_TOOL_OPTIONS", "-XX:+TieredCompilation -XX:TieredStopAtLevel=1");
        //     // }})
        //     .build();

        // Rule rule = Rule.Builder.create(this, "rule")
        //     .eventPattern(EventPattern.builder()
        //         .source(List.of("aws.ecr"))
        //         .detailType(List.of("ECR Image Action"))
        //         .detail(Map.of(
        //             "repository-name", List.of(projectName),
        //             "action-type", List.of("PUSH"),
        //             "image-tag", List.of("latest"),
        //             "result", List.of("SUCCESS")
        //             )
        //         )
        //         .build())
        // .build();

        // rule.addTarget(LambdaFunction.Builder.create(fn)
        //     .maxEventAge(Duration.hours(2)) // Optional: set the maxEventAge retry policy
        //     .retryAttempts(2)
        //     .build());
    }
}
