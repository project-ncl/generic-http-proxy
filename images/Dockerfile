#
# Copyright © 2021-2023 Red Hat, Inc. (https://github.com/Commonjava/service-parent)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

FROM registry.access.redhat.com/ubi9/openjdk-11:1.21-1 AS builder

USER root

WORKDIR /workspace

COPY ./ .

RUN mvn package -Dquarkus.package.type=uber-jar

FROM registry.access.redhat.com/ubi9/openjdk-11-runtime:1.21-1

USER root

RUN mkdir -p /deployment/log /deployment/config && \
  chmod -R 777 /deployment/log /deployment/config

COPY --from=builder /workspace/target/*-runner.jar /deployment/indy-generic-proxy-service-runner.jar
RUN chmod +r /deployment/indy-generic-proxy-service-runner.jar

COPY images/start-service.sh /deployment/start-service.sh
RUN chmod +x /deployment/*

WORKDIR /

ENTRYPOINT ["bash", "-c"]
CMD ["/deployment/start-service.sh"]