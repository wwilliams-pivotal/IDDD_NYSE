package co.vaughnvernon.tradercommon.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.gemstone.gemfire.cache.CacheClosedException;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.client.ClientCache;
import com.gemstone.gemfire.cache.client.ClientCacheFactory;

import co.vaughnvernon.tradercommon.event.DomainEvent;
import co.vaughnvernon.tradercommon.event.EventStore;
import co.vaughnvernon.tradercommon.event.StoredEvent;

public class GemFireEventStore implements EventStore {

	private int nextEventId;

	private static final String DOMAIN_EVENTS_REGION = "DomainEvents";
	private Region<Long, StoredEvent> domainEventsRegion;
	ClientCache clientCache;

	public GemFireEventStore() {
		super();

		initializeGemFireCache();

		this.setEvents(new ArrayList<StoredEvent>());
		this.setNextEventId(1);
	}

	@Override
	public List<StoredEvent> allStoredEventsBetween(long aLowStoredEventId, long aHighStoredEventId) {
		List<StoredEvent> results = new ArrayList<>();
		Set<Long> keys = events().keySetOnServer();
		for (Long key : keys) {
			StoredEvent event = events().get(key);
			if (event.eventId() >= aLowStoredEventId && event.eventId() <= aHighStoredEventId) {
				results.add(event);
			}
		}
		return results;
	}

	@Override
	public List<StoredEvent> allStoredEventsSince(long aStoredEventId) {
		List<StoredEvent> results = new ArrayList<>();
		Set<Long> keys = events().keySetOnServer();
		for (Long key : keys) {
			StoredEvent event = events().get(key);
			if (event.eventId() > aStoredEventId) {
				results.add(event);
			}
		}
		return results;
	}

	@Override
	public StoredEvent append(DomainEvent aDomainEvent) {
		StoredEvent storedEvent = new StoredEvent(aDomainEvent);

		Collection<StoredEvent> events = events().values();
		synchronized (events) {
			int nextEventId = this.nextEventId();

			storedEvent.setEventId(nextEventId);

			this.setNextEventId(nextEventId + 1);

			events().put(storedEvent.eventId(), storedEvent);
		}

		return storedEvent;
	}

	@Override
	public long countStoredEvents() {
		return events().keySetOnServer().size();
	}

	private Region<Long, StoredEvent> events() {
		return domainEventsRegion;
	}

	private void setEvents(List<StoredEvent> anEvents) {
		Set<Long> keys = events().keySetOnServer();
		events().removeAll(keys);
		anEvents.forEach((v) -> events().put(v.eventId(), v));
	}

	private int nextEventId() {
		return this.nextEventId;
	}

	private void setNextEventId(int aNextEventId) {
		this.nextEventId = aNextEventId;
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
		domainEventsRegion = clientCache.getRegion(DOMAIN_EVENTS_REGION);
	}
}
