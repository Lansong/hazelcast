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

package com.hazelcast.hibernate;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.entity.DummyEntity;
import com.hazelcast.hibernate.entity.DummyProperty;
import com.hazelcast.hibernate.instance.HazelcastAccessor;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.stat.Statistics;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public abstract class HibernateStatisticsTestSupport extends HibernateTestSupport {

    protected SessionFactory sf;
    protected Statistics stats;

    @Before
    public void postConstruct() {
        Hazelcast.newHazelcastInstance();
        sf = createSessionFactory(getCacheProperties());
        stats = sf.getStatistics();
    }

    @After
    public void preDestroy() {
        if (sf != null) {
            sf.close();
            sf = null;
        }
        Hazelcast.shutdownAll();
    }

    protected HazelcastInstance getHazelcastInstance() {
        return HazelcastAccessor.getHazelcastInstance(sf);
    }

    protected abstract Properties getCacheProperties();

    protected void insertDummyEntities(int count) {
        insertDummyEntities(count, 0);
    }

    protected void insertDummyEntities(int count, int childCount) {
        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();
        try {
            for (int i = 0; i < count; i++) {
                DummyEntity e = new DummyEntity(new Long(i), "dummy:" + i, i * 123456d, new Date());
                session.save(e);
                for (int j = 0; j < childCount; j++) {
                    DummyProperty p = new DummyProperty("key:" + j, e);
                    session.save(p);
                }
            }
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            e.printStackTrace();
        } finally {
            session.close();
        }
    }

    @Test
    public void testEntity() {
        final HazelcastInstance hz = getHazelcastInstance();
        assertNotNull(hz);
        assertEquals(Hazelcast.getDefaultInstance(), hz);
        final int count = 100;
        final int childCount = 3;
        insertDummyEntities(count, childCount);
        sleep(1);
        List<DummyEntity> list = new ArrayList<DummyEntity>(count);
        Session session = sf.openSession();
        try {
            for (int i = 0; i < count; i++) {
                DummyEntity e = (DummyEntity) session.get(DummyEntity.class, new Long(i));
                session.evict(e);
                list.add(e);
            }
        } finally {
            session.close();
        }
        session = sf.openSession();
        Transaction tx = session.beginTransaction();
        try {
            for (DummyEntity dummy : list) {
                dummy.setDate(new Date());
                session.update(dummy);
            }
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            e.printStackTrace();
        } finally {
            session.close();
        }
        Map<?, ?> cache = hz.getMap(DummyEntity.class.getName());
        Map<?, ?> propCache = hz.getMap(DummyProperty.class.getName());
        Map<?, ?> propCollCache = hz.getMap(DummyEntity.class.getName() + ".properties");
        assertEquals((childCount + 1) * count, stats.getEntityInsertCount());
        // twice put of entity and properties (on load and update) and once put of collection
        assertEquals((childCount + 1) * count * 2, stats.getSecondLevelCachePutCount());
        assertEquals(childCount * count, stats.getEntityLoadCount());
        assertEquals(count, stats.getSecondLevelCacheHitCount());
        // collection cache miss
        assertEquals(count, stats.getSecondLevelCacheMissCount());
        assertEquals(count, cache.size());
        assertEquals(count * childCount, propCache.size());
        assertEquals(count, propCollCache.size());
        sf.getCache().evictEntityRegion(DummyEntity.class);
        sf.getCache().evictEntityRegion(DummyProperty.class);
        assertEquals(0, cache.size());
        assertEquals(0, propCache.size());
        stats.logSummary();
    }

    @Test
    public void testQuery() {
        final int entityCount = 10;
        final int queryCount = 5;
        insertDummyEntities(entityCount);
        sleep(1);
        List<DummyEntity> list = null;
        for (int i = 0; i < queryCount; i++) {
            list = executeQuery();
            assertEquals(entityCount, list.size());
            sleep(1);
        }
        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();
        try {
            for (DummyEntity dummy : list) {
                session.delete(dummy);
            }
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            e.printStackTrace();
        } finally {
            session.close();
        }
        assertEquals(1, stats.getQueryCachePutCount());
        assertEquals(1, stats.getQueryCacheMissCount());
        assertEquals(queryCount - 1, stats.getQueryCacheHitCount());
        assertEquals(1, stats.getQueryExecutionCount());
        assertEquals(entityCount, stats.getEntityInsertCount());
//      FIXME
//      HazelcastRegionFactory puts into L2 cache 2 times; 1 on insert, 1 on query execution 
//      assertEquals(entityCount, stats.getSecondLevelCachePutCount());
        assertEquals(entityCount, stats.getEntityLoadCount());
        assertEquals(entityCount, stats.getEntityDeleteCount());
        assertEquals(entityCount * (queryCount - 1) * 2, stats.getSecondLevelCacheHitCount());
        // collection cache miss
        assertEquals(entityCount, stats.getSecondLevelCacheMissCount());
        stats.logSummary();
    }

    protected List<DummyEntity> executeQuery() {
        Session session = sf.openSession();
        try {
            Query query = session.createQuery("from " + DummyEntity.class.getName());
            query.setCacheable(true);
            return query.list();
        } finally {
            session.close();
        }
    }
}
