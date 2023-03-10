package com.unicorn.constructs.eks;

import com.unicorn.core.InfrastructureStack;

import software.amazon.awscdk.services.eks.Cluster;
import software.amazon.awscdk.services.eks.ServiceAccount;
import software.amazon.awscdk.services.eks.KubernetesManifest;
import software.amazon.awscdk.services.eks.KubernetesObjectValue;

import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class UnicornStoreEKSaddApp extends Construct {

    private String unicornStoreServiceURL = "";

    public UnicornStoreEKSaddApp(final Construct scope, final String id,
        InfrastructureStack infrastructureStack, Cluster cluster,
        ServiceAccount appServiceAccount, final String projectName) {
            super(scope, id);

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
                        "nodeSelector", Map.of(
                            "kubernetes.io/arch", "arm64"),
                        "serviceAccountName", appServiceAccount.getServiceAccountName(),
                        "containers", List.of(Map.of(
                            "resources", Map.of(
                                "requests", Map.of(
                                    "cpu", "0.5",
                                    "memory", "1Gi")),
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
        appManifestDeployment.getNode().addDependency(appServiceAccount);

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
        unicornStoreServiceURL = appServiceAddress.getValue();
    }

    public String getUnicornStoreServiceURL() {
        return unicornStoreServiceURL;
    }
}

