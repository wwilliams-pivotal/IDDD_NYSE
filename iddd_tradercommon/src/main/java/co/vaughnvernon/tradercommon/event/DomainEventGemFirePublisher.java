//   Copyright 2012,2013 Vaughn Vernon
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

package co.vaughnvernon.tradercommon.event;

import com.gemstone.gemfire.cache.CacheClosedException;
import com.gemstone.gemfire.cache.client.ClientCache;
import com.gemstone.gemfire.cache.client.ClientCacheFactory;
import com.gemstone.gemfire.cache.query.CqAttributes;
import com.gemstone.gemfire.cache.query.CqAttributesFactory;
import com.gemstone.gemfire.cache.query.CqClosedException;
import com.gemstone.gemfire.cache.query.CqException;
import com.gemstone.gemfire.cache.query.CqExistsException;
import com.gemstone.gemfire.cache.query.CqListener;
import com.gemstone.gemfire.cache.query.CqQuery;
import com.gemstone.gemfire.cache.query.QueryInvalidException;
import com.gemstone.gemfire.cache.query.QueryService;
import com.gemstone.gemfire.cache.query.RegionNotFoundException;

import co.vaughnvernon.tradercommon.infrastructure.persistence.GemFireEventStore;

public class DomainEventGemFirePublisher {

	private static final ThreadLocal<DomainEventGemFirePublisher> instance = new ThreadLocal<DomainEventGemFirePublisher>() {
		protected DomainEventGemFirePublisher initialValue() {
			return new DomainEventGemFirePublisher();
		}
	};

	CqQuery cqQuery = null;

	private EventStore eventStore = new GemFireEventStore();

	ClientCache clientCache;

	public static DomainEventGemFirePublisher instance() {
		return instance.get();
	}

	public <T> void publish(final T aDomainEvent) {
		eventStore.append((DomainEvent) aDomainEvent);
	}

	public void reset() {
		// Get the query service
		QueryService queryService = clientCache.getQueryService();
		CqQuery[] cqQueriesOnRegion = queryService.getCqs();
		if (cqQueriesOnRegion == null || cqQueriesOnRegion.length == 0) {
			queryService.closeCqs();
		}
	}

	public <T> void subscribe(DomainEventListener aListener) {

		// Get the query service
		QueryService queryService = clientCache.getQueryService();
		cqQuery = createNewCq(queryService, aListener);
	}

	public <T> void subscribe(DomainEventListener[] someListeners) {

		// Get the query service
		QueryService queryService = clientCache.getQueryService();

		CqQuery[] cqQueriesOnRegion = queryService.getCqs();
		if (cqQueriesOnRegion == null || cqQueriesOnRegion.length == 0) {
			createNewCq(queryService, someListeners);
		} else {
			addListenerToExistingCq(queryService, cqQueriesOnRegion[0], someListeners);
		}
	}

	private CqQuery createNewCq(QueryService queryService, DomainEventListener aListener) {
		// Create CQ Attributes.
		CqAttributesFactory cqAf = new CqAttributesFactory();

		CqListener[] cqListeners = { aListener };

		cqAf.initCqListeners(cqListeners);
		CqAttributes cqa = cqAf.create();

		String cqName = aListener.queryName();
		String query = aListener.oql();
		CqQuery testExistCqQuery = queryService.getCq(cqName);
		if (testExistCqQuery != null) {
			cqQuery = testExistCqQuery;
		}
		else {
			cqQuery = buildCq(queryService, cqName, query, cqa);
		}
		return cqQuery;
	}

	private CqQuery createNewCq(QueryService queryService, DomainEventListener[] someListeners) {
		CqAttributesFactory cqAf = new CqAttributesFactory();
		CqListener[] cqListeners = someListeners;

		cqAf.initCqListeners(cqListeners);
		CqAttributes cqa = cqAf.create();

		String cqName = someListeners[0].queryName();
		String query = someListeners[0].oql();
		cqQuery = buildCq(queryService, cqName, query, cqa);
		return cqQuery;
	}

	private CqQuery buildCq(QueryService queryService, String cqName, String query, CqAttributes cqa) {
		try {
			cqQuery = queryService.newCq(cqName, query, cqa);
			cqQuery.executeWithInitialResults();

		} catch (QueryInvalidException | CqExistsException | CqException | CqClosedException | RegionNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			cqQuery = null;
		}
		return cqQuery;
	}

	private CqQuery addListenerToExistingCq(QueryService queryService, CqQuery cqQuery,
			DomainEventListener[] someListeners) {

		CqAttributes cqa = cqQuery.getCqAttributes();

		// Add new listener to existing query
		CqAttributesFactory cqAf = new CqAttributesFactory(cqa);
		for (DomainEventListener aListener : someListeners) {
			cqAf.addCqListener(aListener);
		}
		cqa = cqAf.create();

		String cqName = cqQuery.getName();
		String query = cqQuery.getQueryString();
		cqQuery = buildCq(queryService, cqName, query, cqa);
		return cqQuery;
	}

	public void unsubscribe() throws CqException {
		QueryService queryService = clientCache.getQueryService();
		CqQuery[] cqQueriesOnRegion = queryService.getCqs();
		cqQuery.close();
	}

	private DomainEventGemFirePublisher() {
		super();

		initializeGemFireCache();
	}

	private void initializeGemFireCache() {
		try {
			clientCache = ClientCacheFactory.getAnyInstance();
		} catch (CacheClosedException e) {
			clientCache = null;
		}
		if (clientCache == null || clientCache.isClosed()) {
			ClientCacheFactory ccf = new ClientCacheFactory();
			ccf.set("cache-xml-file", "../iddd_nanotrader/target/classes/gemfire/clientCache.xml");
			clientCache = ccf.create();
		}
	}
}
