package com.unicorn.constructs;

import com.unicorn.core.InfrastructureStack;
import software.amazon.awscdk.services.eks.Cluster;
import software.amazon.awscdk.services.eks.KubernetesVersion;
import software.amazon.awscdk.services.eks.ClusterLoggingTypes;
import software.amazon.awscdk.services.eks.FargateProfile;
import software.amazon.awscdk.services.eks.FargateProfileOptions;
import software.amazon.awscdk.services.eks.AlbControllerOptions;
import software.amazon.awscdk.services.eks.NodegroupOptions;
import software.amazon.awscdk.services.eks.Selector;
import software.amazon.awscdk.services.eks.NodegroupAmiType;
import software.amazon.awscdk.services.eks.AlbControllerVersion;
import software.amazon.awscdk.services.eks.AwsAuthMapping;
import software.amazon.awscdk.services.eks.CapacityType;
import software.amazon.awscdk.services.eks.KubernetesManifest;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.iam.IRole;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.FromRoleArnOptions;

import software.constructs.Construct;

import java.util.List;
import java.util.Arrays;
import java.util.Map;

public class UnicornStoreSpringEKS extends Construct {

        private Cluster cluster;

        public UnicornStoreSpringEKS(final Construct scope, final String id, InfrastructureStack infrastructureStack) {
                super(scope, id);

                final String projectName = "unicorn";
                IRole adminRole = Role.fromRoleArn(scope, projectName + "-admin-role",
                        "arn:aws:iam::" + infrastructureStack.getAccount() + ":role/Admin",
                        FromRoleArnOptions.builder().mutable(false)
                        .build());
                IRole workshopAdminRole = Role.fromRoleArn(scope, projectName + "-workshop-admin-role",
                        "arn:aws:iam::" + infrastructureStack.getAccount() + ":role/workshop-admin",
                        FromRoleArnOptions.builder().mutable(false)
                        .build());

                // Create the EKS cluster
                cluster = Cluster.Builder.create(scope, projectName + "-cluster").clusterName(projectName)
                        .clusterName(projectName + "-cluster")
                        .vpc(infrastructureStack.getVpc())
                        .vpcSubnets(List.of(SubnetSelection.builder().subnetType(SubnetType.PRIVATE_WITH_EGRESS).build()))
                        .clusterLogging(Arrays.asList(ClusterLoggingTypes.API,
                                ClusterLoggingTypes.AUDIT,
                                ClusterLoggingTypes.AUTHENTICATOR,
                                ClusterLoggingTypes.CONTROLLER_MANAGER,
                                ClusterLoggingTypes.SCHEDULER))
                        .version(KubernetesVersion.of("1.24"))
                        //.kubectlLayer(new KubectlV24Layer(scope, projectName + "-kubectl"))
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

                FargateProfile fargateProfile = cluster.addFargateProfile(projectName, FargateProfileOptions.builder()
                        .selectors(List.of(Selector.builder().namespace(projectName + "-*").build()))
                        .fargateProfileName(projectName + "-fargate-profile")
                        .build());

                // Logging for Fargate
                // https://docs.aws.amazon.com/eks/latest/userguide/fargate-logging.html
                PolicyStatement executionRolePolicy = PolicyStatement.Builder.create()
                        .effect(Effect.ALLOW)
                        //.principals(List.of(new AnyPrincipal()))
                        .actions(List.of(
        			"logs:CreateLogStream",
        			"logs:CreateLogGroup",
        			"logs:DescribeLogStreams",
        			"logs:PutLogEvents"
                        ))
                        .resources(List.of("*"))
                        .build();

                fargateProfile.getPodExecutionRole().addToPrincipalPolicy(executionRolePolicy);

                String newLine = System.getProperty("line.separator");

                Map<String, Object> namespace = Map.of(
                        "apiVersion", "v1",
                        "kind", "Namespace",
                        "metadata", Map.of("name", "aws-observability",
                                "labels", Map.of("aws-observability", "enabled")));

                Map<String, Object> configMap = Map.of(
                        "apiVersion", "v1",
                        "kind", "ConfigMap",
                        "metadata", Map.of(
                                "name", "aws-logging",
                                "namespace", "aws-observability"),
                        "data", Map.of(
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
                                       "    Kube_Meta_Cache_TTL 300s"
                                       ),
                                "output.conf", String.join(newLine,
                                       "[OUTPUT]",
                                       "    Name cloudwatch_logs",
                                       "    Match kube.*",
                                       "    region " + infrastructureStack.getRegion(),
                                       "    log_group_name /aws/eks/" + projectName + "-cluster/" + fargateProfile.getFargateProfileName(),
                                       "    log_stream_prefix from-fluent-bit-",
                                       "    log_retention_days 60",
                                       "    auto_create_group true"
                                       ),
                                "parsers.conf", String.join(newLine,
                                       "[PARSER]",
                                       "    Name crio",
                                       "    Format Regex",
                                       "    Regex ^(?<time>[^ ]+) (?<stream>stdout|stderr) (?<logtag>P|F) (?<log>.*)$",
                                       "    Time_Key    time",
                                       "    Time_Format %Y-%m-%dT%H:%M:%S.%L%z"
                                       )
                                       ));

                KubernetesManifest manifestNamespace = KubernetesManifest.Builder.create(scope, projectName + "-manifest-ns")
                        .cluster(cluster)
                        .manifest(List.of(namespace))
                        .build();

                KubernetesManifest manifestConfigMap = KubernetesManifest.Builder.create(scope, projectName + "-manifest-cm")
                        .cluster(cluster)
                        .manifest(List.of(configMap))
                        .build();

                manifestConfigMap.getNode().addDependency(manifestNamespace);
        }

        public Cluster getCluster() {
                return cluster;
        }
}
