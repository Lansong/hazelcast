/*
 * Copyright (c) 2008-2012, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.cluster;

import com.hazelcast.instance.Node;

import java.util.concurrent.atomic.AtomicBoolean;

public class SplitBrainHandler implements Runnable {

    final Node node;
    final AtomicBoolean inProgress = new AtomicBoolean(false);

    public SplitBrainHandler(Node node) {
        this.node = node;
    }

    public void run() {
        if (node.isMaster() && node.joined() && node.isActive() && !node.clusterService.isJoinInProgress()
            && inProgress.compareAndSet(false, true)) {
            try {
                searchForOtherClusters();
            } finally {
                inProgress.set(false);
            }
        }
    }

    private void searchForOtherClusters() {
        Joiner joiner = node.getJoiner();
        if (joiner != null) {
            joiner.searchForOtherClusters(this);
        }
    }

    public void restart() {
        node.hazelcastInstance.restartToMerge();
    }
}
