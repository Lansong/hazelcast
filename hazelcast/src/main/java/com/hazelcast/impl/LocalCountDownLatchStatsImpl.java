/*
 * Copyright (c) 2008-2010, Hazel Ltd. All Rights Reserved.
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
 *
 */

package com.hazelcast.impl;

import com.hazelcast.monitor.LocalCountDownLatchOperationStats;
import com.hazelcast.monitor.LocalCountDownLatchStats;
import com.hazelcast.nio.DataSerializable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class LocalCountDownLatchStatsImpl implements LocalCountDownLatchStats, DataSerializable {
    private LocalCountDownLatchOperationStats operationsStats;

    public LocalCountDownLatchOperationStats getOperationStats() {
        return operationsStats;
    }

    public void setOperationsStats(LocalCountDownLatchOperationStats operationsStats) {
        this.operationsStats = operationsStats;
    }

    public void writeData(DataOutput out) throws IOException {
        operationsStats.writeData(out);
    }

    public void readData(DataInput in) throws IOException {
        (operationsStats = new LocalCountDownLatchOperationStatsImpl()).readData(in);
    }
}