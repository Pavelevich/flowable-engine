/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flowable.app.engine.test.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipInputStream;

import org.flowable.app.api.repository.AppDefinition;
import org.flowable.app.engine.impl.persistence.entity.deploy.AppDefinitionCacheEntry;
import org.flowable.app.engine.test.AppDeployment;
import org.flowable.app.engine.test.FlowableAppTestCase;
import org.flowable.common.engine.impl.persistence.deploy.DeploymentCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Improved version of DeploymentTest with better resource management and exception handling.
 */
public class DeploymentTest extends FlowableAppTestCase {

    @Test
    @AppDeployment
    @DisplayName("Verify App Definition is Deployed Correctly")
    public void testAppDefinitionDeployed() throws Exception {
        org.flowable.app.api.repository.AppDeployment appDeployment =
                appRepositoryService.createDeploymentQuery().singleResult();
        assertThat(appDeployment).isNotNull();

        List<String> resourceNames = appRepositoryService.getDeploymentResourceNames(appDeployment.getId());
        assertThat(resourceNames).containsOnly("org/flowable/app/engine/test/repository/DeploymentTest.testAppDefinitionDeployed.app");

        try (InputStream inputStream = appRepositoryService.getResourceAsStream(appDeployment.getId(), resourceNames.get(0))) {
            assertThat(inputStream).isNotNull();
        }

        DeploymentCache<AppDefinitionCacheEntry> appDefinitionCache = appEngineConfiguration.getAppDefinitionCache();
        assertThat(appDefinitionCache.getAll()).hasSize(1);

        AppDefinitionCacheEntry cachedAppDefinition = appDefinitionCache.getAll().iterator().next();
        assertThat(cachedAppDefinition.getAppModel()).isNotNull();
        assertThat(cachedAppDefinition.getAppDefinition()).isNotNull();

        AppDefinition appDefinition = cachedAppDefinition.getAppDefinition();
        assertThat(appDefinition.getId()).isNotNull();
        assertThat(appDefinition.getDeploymentId()).isNotNull();
        assertThat(appDefinition.getKey()).isNotNull();
        assertThat(appDefinition.getResourceName()).isNotNull();
        assertThat(appDefinition.getVersion()).isPositive();
    }

    @Test
    @DisplayName("Verify App Definition Deployment from ZIP File")
    public void testAppDefinitionZipDeployed() throws Exception {
        String resourcePath = "org/flowable/app/engine/test/vacationRequest.zip";
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (resourceStream == null) {
            throw new IllegalArgumentException("Resource not found: " + resourcePath);
        }

        appRepositoryService.createDeployment()
                .addZipInputStream(new ZipInputStream(resourceStream))
                .deploy();

        org.flowable.app.api.repository.AppDeployment appDeployment = appRepositoryService.createDeploymentQuery().singleResult();
        assertThat(appDeployment).isNotNull();

        List<String> resourceNames = appRepositoryService.getDeploymentResourceNames(appDeployment.getId());
        assertThat(resourceNames).contains("vacationRequestApp.app");

        try (InputStream inputStream = appRepositoryService.getResourceAsStream(appDeployment.getId(), "vacationRequestApp.app")) {
            assertThat(inputStream).isNotNull();
        }

        appRepositoryService.deleteDeployment(appDeployment.getId(), true);
    }


    private List<String> createTestDeployments() {
        return appEngineConfiguration.getCommandExecutor().execute(commandContext -> {
            org.flowable.app.api.repository.AppDeployment deployment1 = appRepositoryService.createDeployment()
                    .name("First")
                    .key("full-info")
                    .category("test")
                    .addClasspathResource("org/flowable/app/engine/test/fullinfo.app")
                    .deploy();

            org.flowable.app.api.repository.AppDeployment deployment2 = appRepositoryService.createDeployment()
                    .name("Second")
                    .key("test")
                    .addClasspathResource("org/flowable/app/engine/test/test.app")
                    .deploy();

            return Arrays.asList(deployment1.getId(), deployment2.getId());
        });
    }

    @Test
    @DisplayName("Verify Deployment Resource Names")
    public void testDeploymentResourceNames() {
        List<String> deploymentIds = createTestDeployments();
        assertThat(appRepositoryService.getDeploymentResourceNames(deploymentIds.get(0)))
                .containsExactlyInAnyOrder("org/flowable/app/engine/test/fullinfo.app");
        assertThat(appRepositoryService.getDeploymentResourceNames(deploymentIds.get(1)))
                .containsExactlyInAnyOrder("org/flowable/app/engine/test/test.app");
        deploymentIds.forEach(id -> appRepositoryService.deleteDeployment(id, true));
    }

    @Test
    @DisplayName("Verify Deployment Metadata and Properties")
    public void testDeploymentMetadata() {
        List<String> deploymentIds = createTestDeployments();
        assertThat(appRepositoryService.createDeploymentQuery().list())
                .as("deployment time not null")
                .allSatisfy(deployment -> assertThat(deployment.getDeploymentTime()).as(deployment.getName()).isNotNull())
                .extracting(org.flowable.app.api.repository.AppDeployment::getId, org.flowable.app.api.repository.AppDeployment::getName,
                        org.flowable.app.api.repository.AppDeployment::getKey, org.flowable.app.api.repository.AppDeployment::getCategory)
                .as("id, name, key, category")
                .containsExactlyInAnyOrder(
                        tuple(deploymentIds.get(0), "First", "full-info", "test"),
                        tuple(deploymentIds.get(1), "Second", "test", null)
                );
        deploymentIds.forEach(id -> appRepositoryService.deleteDeployment(id, true));
    }
}