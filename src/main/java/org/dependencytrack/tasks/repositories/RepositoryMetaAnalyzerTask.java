/*
 * This file is part of Dependency-Track.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package org.dependencytrack.tasks.repositories;

import alpine.event.framework.Event;
import alpine.event.framework.Subscriber;
import alpine.logging.Logger;
import alpine.persistence.PaginatedResult;
import alpine.resources.AlpineRequest;
import alpine.resources.OrderDirection;
import alpine.resources.Pagination;
import org.apache.commons.lang3.StringUtils;
import org.dependencytrack.event.RepositoryMetaEvent;
import org.dependencytrack.model.Component;
import org.dependencytrack.model.Repository;
import org.dependencytrack.model.RepositoryMetaComponent;
import org.dependencytrack.persistence.QueryManager;
import java.util.Date;
import java.util.List;

public class RepositoryMetaAnalyzerTask implements Subscriber {

    private static final Logger LOGGER = Logger.getLogger(RepositoryMetaAnalyzerTask.class);

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public void inform(final Event e) {
        if (e instanceof RepositoryMetaEvent) {
            LOGGER.debug("Analyzing component repository metadata");
            final RepositoryMetaEvent event = (RepositoryMetaEvent)e;
            if (event.getComponent() != null) {
                try (QueryManager qm = new QueryManager()) {
                    // Refreshing the object by querying for it again is preventative
                    analyze(qm, qm.getObjectById(Component.class, event.getComponent().getId()));
                }
            } else {
                final AlpineRequest alpineRequest = new AlpineRequest(
                        null,
                        new Pagination(Pagination.Strategy.OFFSET, 0, 1000),
                        null,
                        "id",
                        OrderDirection.ASCENDING
                );
                try (QueryManager qm = new QueryManager(alpineRequest)) {
                    final long total = qm.getCount(Component.class);
                    LOGGER.info("Performing component repository metadata analysis against all (" + total +") components in the portfolio");
                    long count = 0;
                    while (count < total) {
                        final PaginatedResult result = qm.getComponents();
                        final List<Component> components = result.getList(Component.class);
                        for (final Component component: components) {
                            analyze(qm, component);
                        }
                        count += result.getObjects().size();
                        qm.advancePagination();
                    }
                }
                LOGGER.info("Portfolio component repository metadata analysis complete");
            }
            LOGGER.debug("Component repository metadata analysis complete");
        }
    }

    private void analyze(final QueryManager qm, final Component component) {
        LOGGER.debug("Analyzing component: " + component.getUuid());
        final IMetaAnalyzer analyzer = IMetaAnalyzer.build(component);
        for (final Repository repository: qm.getAllRepositoriesOrdered(analyzer.supportedRepositoryType())) {
            analyzer.setRepositoryBaseUrl(repository.getUrl());
            final MetaModel model = analyzer.analyze(component);
            if (StringUtils.trimToNull(model.getLatestVersion()) != null) {
                // Resolution from repository was successful. Update meta model
                final RepositoryMetaComponent metaComponent = new RepositoryMetaComponent();
                metaComponent.setRepositoryType(repository.getType());
                metaComponent.setNamespace(component.getPurl().getNamespace());
                metaComponent.setName(component.getPurl().getName());
                metaComponent.setPublished(model.getPublishedTimestamp());
                metaComponent.setLatestVersion(model.getLatestVersion());
                metaComponent.setLastCheck(new Date());
                qm.synchronizeRepositoryMetaComponent(metaComponent);
                return;
            }
        }
    }
}
