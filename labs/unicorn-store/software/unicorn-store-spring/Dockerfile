FROM public.ecr.aws/lts/ubuntu:22.04 AS builder

RUN apt-get update --fix-missing && apt-get install zip curl -y

RUN \
    curl -s "https://get.sdkman.io" | bash; \
    bash -c "source $HOME/.sdkman/bin/sdkman-init.sh; \
    sdk install java 17.0.6-amzn; \
    sdk install maven;"

COPY ./pom.xml ./pom.xml
COPY src ./src/

ENV MAVEN_OPTS='-Xmx6g'

RUN bash -c "source $HOME/.sdkman/bin/sdkman-init.sh && mvn -Dmaven.test.skip=true clean package"

FROM public.ecr.aws/lts/ubuntu:22.04
RUN addgroup --system spring && adduser --system spring --ingroup spring

COPY --from=builder target/store-spring-1.0.0.jar store-spring.jar

ADD https://github.com/aws-observability/aws-otel-java-instrumentation/releases/latest/download/aws-opentelemetry-agent.jar /opt/aws-opentelemetry-agent.jar
ENV JAVA_TOOL_OPTIONS=-javaagent:/opt/aws-opentelemetry-agent.jar

# OpenTelemetry agent configuration
ENV OTEL_TRACES_SAMPLER "always_on"
ENV OTEL_PROPAGATORS "tracecontext,baggage,xray"
ENV OTEL_RESOURCE_ATTRIBUTES "service.name=unicorn-store-spring"
ENV OTEL_IMR_EXPORT_INTERVAL "10000"
ENV OTEL_EXPORTER_OTLP_ENDPOINT "http://localhost:4317"

EXPOSE 80
ENTRYPOINT ["java","-jar","-Dserver.port=80","/store-spring.jar"]
